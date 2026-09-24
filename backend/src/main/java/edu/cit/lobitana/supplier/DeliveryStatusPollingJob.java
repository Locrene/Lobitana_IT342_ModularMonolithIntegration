package edu.cit.lobitana.supplier;

import edu.cit.lobitana.supplier.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Watches purchase orders that are already with LegacySupply and translates whatever they
 * report into our own statuses. On delivery it publishes an event; Inventory restocks from
 * that event, so neither Inventory nor Order ever calls this module.
 */
@Component
class DeliveryStatusPollingJob {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusPollingJob.class);

    private final SupplierOrderStore store;
    private final LegacySupplyClient client;
    private final LegacySupplyStatusTranslator statusTranslator;
    private final LegacySupplyProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    DeliveryStatusPollingJob(SupplierOrderStore store,
                             LegacySupplyClient client,
                             LegacySupplyStatusTranslator statusTranslator,
                             LegacySupplyProperties properties,
                             ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.client = client;
        this.statusTranslator = statusTranslator;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${legacysupply.status-job.interval-ms:120000}",
               initialDelayString = "${legacysupply.status-job.initial-delay-ms:45000}")
    void pollOpenOrders() {
        List<SupplierOrder> open = store.findAwaitingDelivery(properties.getStatusJob().getBatchSize());
        for (SupplierOrder order : open) {
            try {
                poll(order);
            } catch (RuntimeException e) {
                // The order keeps its current status and is polled again next run.
                log.warn("Could not read the status of {}: {}", order.getBuyerRef(), e.getMessage());
            }
        }
    }

    private void poll(SupplierOrder order) {
        boolean hasPoNumber = order.getPoNumber() != null && !order.getPoNumber().isBlank();
        Optional<PurchaseOrderAck> ack = hasPoNumber
                ? client.fetchStatus(order.getPoNumber())
                : client.findByBuyerRef(order.getBuyerRef());

        if (ack.isEmpty()) {
            log.warn("LegacySupply does not recognise {} (PO {}); leaving it as {}",
                    order.getBuyerRef(), order.getPoNumber(), order.getStatus());
            return;
        }

        SupplierOrderStatus current = order.getStatus();
        SupplierOrderStatus reported = statusTranslator.toDomain(ack.get().statusCode());
        if (reported == current) {
            return;
        }

        SupplierOrder updated = store.update(order.getId(), reported, ack.get().poNumber());
        log.info("{} moved from {} to {} (LegacySupply said '{}')",
                order.getBuyerRef(), current, reported, ack.get().statusCode());

        if (reported == SupplierOrderStatus.DELIVERED) {
            eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                    updated.getId(), updated.getProductId(), updated.getUnits(), updated.getPoNumber()));
        }
    }
}
