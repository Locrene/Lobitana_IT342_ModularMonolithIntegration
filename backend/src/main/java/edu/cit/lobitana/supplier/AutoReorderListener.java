package edu.cit.lobitana.supplier;

import edu.cit.lobitana.inventory.events.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The Lab 2 auto-reorder rule, now placing real purchase orders. It lives in this module,
 * not in Inventory, so Inventory keeps knowing nothing about suppliers: it publishes a
 * low-stock event and the supplier module decides what to do about it.
 *
 * Runs after the stock change commits, so a purchase order is never placed for a
 * reservation that then rolls back, and a slow supplier never holds the order transaction open.
 */
@Component
class AutoReorderListener {

    private static final Logger log = LoggerFactory.getLogger(AutoReorderListener.class);

    private final SupplierGateway supplierGateway;
    private final int targetUnits;

    AutoReorderListener(SupplierGateway supplierGateway,
                        @Value("${supplier.reorder-target-units:20}") int targetUnits) {
        this.supplierGateway = supplierGateway;
        this.targetUnits = targetUnits;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onLowStock(LowStockEvent event) {
        int unitsNeeded = targetUnits - event.remainingStock();
        if (unitsNeeded <= 0) {
            return;
        }

        try {
            ReorderOutcome outcome = supplierGateway.reorder(event.productId(), unitsNeeded);
            log.info("Auto-reorder for {} ({} left, threshold {}): {} -> {} unit(s), status {}",
                    event.productId(), event.remainingStock(), event.threshold(),
                    outcome.buyerRef(), outcome.unitsOrdered(), outcome.status());
        } catch (RuntimeException e) {
            // A failed reorder must never break the order that triggered it.
            log.error("Auto-reorder for {} could not be started: {}", event.productId(), e.getMessage(), e);
        }
    }
}
