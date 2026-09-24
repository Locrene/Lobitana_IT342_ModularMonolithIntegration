package edu.cit.lobitana.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Owns the LegacySupply session. Signs in on first use, re-signs in when the session
 * is near its TTL or when the supplier rejects it. Nothing outside this class ever
 * sees a token, and no token is ever pasted in by hand.
 */
@Component
class LegacySupplySessionManager {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySessionManager.class);

    private final RestTemplate restTemplate;
    private final LegacySupplyProperties properties;
    private final XmlCodec xmlCodec;

    private String token;
    private Instant renewAt;

    LegacySupplySessionManager(RestTemplate legacySupplyRestTemplate,
                               LegacySupplyProperties properties,
                               XmlCodec xmlCodec) {
        this.restTemplate = legacySupplyRestTemplate;
        this.properties = properties;
        this.xmlCodec = xmlCodec;
    }

    synchronized String token() {
        if (token == null || Instant.now().isAfter(renewAt)) {
            signIn();
        }
        return token;
    }

    /** Called when LegacySupply answers 401/403: the next call will sign in again. */
    synchronized void invalidate() {
        if (token != null) {
            log.info("LegacySupply rejected the session; will sign in again on the next call");
        }
        token = null;
        renewAt = null;
    }

    private void signIn() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LegacySupplyException("LS_API_KEY is not set; cannot sign in to LegacySupply", false);
        }

        String body = xmlCodec.marshal(new XmlSessionRequest(properties.getClientId(), properties.getApiKey()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.url(properties.getSessionPath()), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            XmlSessionResponse parsed = xmlCodec.unmarshal(response.getBody(), XmlSessionResponse.class);
            if (parsed.getSessionToken() == null || parsed.getSessionToken().isBlank()) {
                throw new LegacySupplyException("Sign-in response carried no session token", true);
            }

            long ttl = parsed.getExpiresInSeconds() != null
                    ? parsed.getExpiresInSeconds()
                    : properties.getSessionTtlSeconds();
            long usable = Math.max(5, ttl - properties.getSessionRefreshMarginSeconds());

            this.token = parsed.getSessionToken();
            this.renewAt = Instant.now().plusSeconds(usable);
            log.info("Signed in to LegacySupply; renewing session in {}s", usable);

        } catch (HttpStatusCodeException e) {
            boolean retryable = e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429;
            throw new LegacySupplyException("Sign-in failed with HTTP " + e.getStatusCode().value(), retryable, e);
        } catch (ResourceAccessException e) {
            throw new LegacySupplyException("Sign-in did not answer in time: " + e.getMessage(), true, e);
        }
    }
}
