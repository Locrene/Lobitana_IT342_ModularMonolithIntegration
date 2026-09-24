package edu.cit.lobitana.supplier;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every LegacySupply-specific detail that might turn out to be wrong lives here, so that
 * reading the interface manual means editing application.properties, not recompiling logic.
 * Package-private: nothing outside this module can see a SKU, a pack size or a status code.
 */
@ConfigurationProperties(prefix = "legacysupply")
class LegacySupplyProperties {

    private String baseUrl = "https://legacysupply.onrender.com/api/v1";
    private String clientId;
    private String apiKey;

    /** Paths appended to baseUrl. FILL IN from https://legacysupply.onrender.com/docs. */
    private String sessionPath = "/auth/token";
    private String ordersPath = "/purchase-orders";
    /** {ref} is replaced with the BuyerRef; used to check whether an order already landed. */
    private String orderByRefPath = "/purchase-orders/by-ref/{ref}";
    /** {po} is replaced with the PO number. */
    private String orderStatusPath = "/purchase-orders/{po}";

    /** Header names. FILL IN if the manual uses different ones. */
    // The client id and API key travel in the AuthRequest body, not in headers.
    private String sessionHeader = "X-LS-Session";
    private String requestIdHeader = "X-Request-Id";

    private long timeoutMs = 3000;
    private int maxAttempts = 3;
    private long backoffMs = 300;

    /** How long a session really lasts. Measure it (Part B) and set it here. */
    private long sessionTtlSeconds = 300;
    /** Re-login this many seconds before the TTL runs out. */
    private long sessionRefreshMarginSeconds = 30;

    private final Job retryJob = new Job(60_000, 30_000, 5);
    private final Job statusJob = new Job(120_000, 45_000, 5);

    /** productId -> supplier SKU, pack size, unit of measure. */
    private Map<String, Product> products = new LinkedHashMap<>();

    /** LegacySupply status code -> our SupplierOrderStatus name. Anything missing becomes UNKNOWN. */
    private Map<String, String> statusMap = new LinkedHashMap<>();

    Optional<Product> product(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    String url(String path) {
        return baseUrl + path;
    }

    String getBaseUrl() { return baseUrl; }
    void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    String getClientId() { return clientId; }
    void setClientId(String clientId) { this.clientId = clientId; }

    String getApiKey() { return apiKey; }
    void setApiKey(String apiKey) { this.apiKey = apiKey; }

    String getSessionPath() { return sessionPath; }
    void setSessionPath(String sessionPath) { this.sessionPath = sessionPath; }

    String getOrdersPath() { return ordersPath; }
    void setOrdersPath(String ordersPath) { this.ordersPath = ordersPath; }

    String getOrderByRefPath() { return orderByRefPath; }
    void setOrderByRefPath(String orderByRefPath) { this.orderByRefPath = orderByRefPath; }

    String getOrderStatusPath() { return orderStatusPath; }
    void setOrderStatusPath(String orderStatusPath) { this.orderStatusPath = orderStatusPath; }

    String getSessionHeader() { return sessionHeader; }
    void setSessionHeader(String sessionHeader) { this.sessionHeader = sessionHeader; }

    String getRequestIdHeader() { return requestIdHeader; }
    void setRequestIdHeader(String requestIdHeader) { this.requestIdHeader = requestIdHeader; }

    long getTimeoutMs() { return timeoutMs; }
    void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    int getMaxAttempts() { return maxAttempts; }
    void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    long getBackoffMs() { return backoffMs; }
    void setBackoffMs(long backoffMs) { this.backoffMs = backoffMs; }

    long getSessionTtlSeconds() { return sessionTtlSeconds; }
    void setSessionTtlSeconds(long sessionTtlSeconds) { this.sessionTtlSeconds = sessionTtlSeconds; }

    long getSessionRefreshMarginSeconds() { return sessionRefreshMarginSeconds; }
    void setSessionRefreshMarginSeconds(long s) { this.sessionRefreshMarginSeconds = s; }

    Job getRetryJob() { return retryJob; }

    Job getStatusJob() { return statusJob; }

    Map<String, Product> getProducts() { return products; }
    void setProducts(Map<String, Product> products) { this.products = products; }

    Map<String, String> getStatusMap() { return statusMap; }
    void setStatusMap(Map<String, String> statusMap) { this.statusMap = statusMap; }

    /** Nested types are public only so the properties binder can construct them. */
    public static class Product {
        private String sku;
        private int packSize = 1;
        private String uom = "CS";

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public int getPackSize() { return packSize; }
        public void setPackSize(int packSize) { this.packSize = packSize; }

        public String getUom() { return uom; }
        public void setUom(String uom) { this.uom = uom; }
    }

    /** Scheduling knobs. Batch size is the lever that keeps us inside the request quota. */
    public static class Job {
        private long intervalMs;
        private long initialDelayMs;
        private int batchSize;

        Job(long intervalMs, long initialDelayMs, int batchSize) {
            this.intervalMs = intervalMs;
            this.initialDelayMs = initialDelayMs;
            this.batchSize = batchSize;
        }

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
