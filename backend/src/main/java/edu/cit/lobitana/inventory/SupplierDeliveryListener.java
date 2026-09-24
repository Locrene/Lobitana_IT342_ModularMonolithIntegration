package edu.cit.lobitana.inventory;

// Rule: an EVENT class only. Inventory never calls the supplier module, and knows nothing
// about SKUs, cases or LegacySupply status codes -- the event speaks in our units.
import edu.cit.lobitana.supplier.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SupplierDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(SupplierDeliveryListener.class);

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        inventoryService.restock(event.productId(), event.units());
        log.info("Restocked {} by {} unit(s) from delivered supplier order {}",
                event.productId(), event.units(), event.supplierOrderId());
    }
}
