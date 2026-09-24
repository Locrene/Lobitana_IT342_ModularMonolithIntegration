package edu.cit.lobitana.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nothing is lost when LegacySupply is down: the reorder is already a PENDING row, and
 * this job keeps sending it, with its original BuyerRef and X-Request-Id, until it lands.
 * The batch size caps how many requests a single run can spend against the quota.
 */
@Component
class PendingOrderRetryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderRetryJob.class);

    private final SupplierOrderStore store;
    private final LegacySupplyAdapter adapter;
    private final LegacySupplyProperties properties;

    PendingOrderRetryJob(SupplierOrderStore store,
                         LegacySupplyAdapter adapter,
                         LegacySupplyProperties properties) {
        this.store = store;
        this.adapter = adapter;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${legacysupply.retry-job.interval-ms:60000}",
               initialDelayString = "${legacysupply.retry-job.initial-delay-ms:30000}")
    void sendPendingOrders() {
        List<SupplierOrder> pending = store.findPending(properties.getRetryJob().getBatchSize());
        if (pending.isEmpty()) {
            return;
        }

        log.info("Retrying {} pending supplier order(s)", pending.size());
        for (SupplierOrder order : pending) {
            try {
                adapter.resend(order);
            } catch (RuntimeException e) {
                // Leave it PENDING and try again next run.
                log.warn("{} still could not be sent: {}", order.getBuyerRef(), e.getMessage());
            }
        }
    }
}
