package edu.cit.lobitana.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every HTTP conversation with LegacySupply. Applies the timeout, the capped retry with
 * exponential backoff, and the session header. The X-Request-Id is always passed in by the
 * caller, never generated here, so a retry repeats the id instead of minting a new one.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);

    private final RestTemplate restTemplate;
    private final LegacySupplyProperties properties;
    private final LegacySupplySessionManager session;
    private final XmlCodec xmlCodec;

    LegacySupplyClient(RestTemplate legacySupplyRestTemplate,
                       LegacySupplyProperties properties,
                       LegacySupplySessionManager session,
                       XmlCodec xmlCodec) {
        this.restTemplate = legacySupplyRestTemplate;
        this.properties = properties;
        this.session = session;
        this.xmlCodec = xmlCodec;
    }

    PurchaseOrderAck submitPurchaseOrder(String buyerRef, String requestId, String sku, int qty) {
        String body = xmlCodec.marshal(new XmlPurchaseOrderRequest(sku, qty, buyerRef));
        String response = exchange(HttpMethod.POST, properties.url(properties.getOrdersPath()), body, requestId, false)
                .orElseThrow(() -> new LegacySupplyException("Purchase order reply had no body", true));
        return toAck(xmlCodec.unmarshal(response, XmlPurchaseOrderResponse.class));
    }

    /**
     * Asks whether a BuyerRef already exists. Used before every resend, so an order that
     * landed but whose reply we never saw is adopted instead of placed twice.
     */
    Optional<PurchaseOrderAck> findByBuyerRef(String buyerRef) {
        String url = properties.url(properties.getOrderByRefPath())
                .replace("{ref}", UriUtils.encodeQueryParam(buyerRef, StandardCharsets.UTF_8));
        return exchange(HttpMethod.GET, url, null, readId(), true)
                .map(body -> toAck(xmlCodec.unmarshal(body, XmlPurchaseOrderResponse.class)))
                // An empty or PO-less document means "no such order", not "found one".
                // Adopting a blank ack would mark the order sent without a PO number.
                .filter(ack -> ack.poNumber() != null && !ack.poNumber().isBlank());
    }

    Optional<PurchaseOrderAck> fetchStatus(String poNumber) {
        String url = properties.url(properties.getOrderStatusPath()).replace("{po}", encode(poNumber));
        return exchange(HttpMethod.GET, url, null, readId(), true)
                .map(body -> toAck(xmlCodec.unmarshal(body, XmlPurchaseOrderResponse.class)));
    }

    /**
     * Reads get a fresh id every time. The stable, order-derived id belongs to the POST
     * that creates a purchase order; reusing it on a GET could collide with an
     * idempotency cache and hand back a stale copy of the original reply.
     */
    private String readId() {
        return UUID.randomUUID().toString();
    }

    private PurchaseOrderAck toAck(XmlPurchaseOrderResponse xml) {
        return new PurchaseOrderAck(xml.getPoNumber(), xml.getBuyerRef(), xml.getStatusCode());
    }

    /**
     * @param emptyOn404 true for lookups, where "not found" is an answer rather than a failure.
     * @return the response body, or empty when the resource does not exist
     */
    private Optional<String> exchange(HttpMethod method, String url, String body,
                                      String requestId, boolean emptyOn404) {
        LegacySupplyException lastFailure = null;
        int maxAttempts = Math.max(1, properties.getMaxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url, method, new HttpEntity<>(body, headers(requestId)), String.class);
                return Optional.ofNullable(response.getBody());

            } catch (HttpClientErrorException.NotFound e) {
                if (emptyOn404) {
                    return Optional.empty();
                }
                throw new LegacySupplyException("LegacySupply has no such resource: " + url, false, e);

            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    session.invalidate();
                }
                lastFailure = classify(e, status);
                if (!lastFailure.isRetryable()) {
                    throw lastFailure;
                }

            } catch (ResourceAccessException e) {
                // Timed out, refused, or the host is down: all worth another attempt.
                lastFailure = new LegacySupplyException(
                        "LegacySupply did not answer within " + properties.getTimeoutMs() + "ms: " + e.getMessage(),
                        true, e);

            } catch (LegacySupplyException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                lastFailure = e;
            }

            log.warn("LegacySupply call {} {} failed on attempt {}/{} (request id {}): {}",
                    method, url, attempt, maxAttempts, requestId, lastFailure.getMessage());

            if (attempt < maxAttempts) {
                backoff(attempt);
            }
        }
        throw lastFailure;
    }

    private LegacySupplyException classify(HttpStatusCodeException e, int status) {
        boolean retryable = e.getStatusCode().is5xxServerError()
                || status == 408 || status == 429 || status == 401 || status == 403;
        return new LegacySupplyException("LegacySupply answered HTTP " + status + ": " + preview(e), retryable, e);
    }

    private HttpHeaders headers(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        headers.set(properties.getSessionHeader(), session.token());
        headers.set(properties.getRequestIdHeader(), requestId);
        return headers;
    }

    private void backoff(int attempt) {
        long wait = properties.getBackoffMs() * (1L << (attempt - 1));
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LegacySupplyException("Interrupted while backing off", true, e);
        }
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String preview(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString().replaceAll("\\s+", " ").trim();
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
