package edu.cit.lobitana.notification;

// Rule: this module may import EVENT classes only, never InventoryService or OrderService.
import edu.cit.lobitana.inventory.events.LowStockEvent;
import edu.cit.lobitana.shop.events.OrderPlacedEvent;
import edu.cit.lobitana.shop.events.OrderRejectedEvent;
import edu.cit.lobitana.supplier.events.SupplierOrderDeliveredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class NotificationListener {

    private final NotificationRepository notificationRepository;

    NotificationListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    void onOrderPlaced(OrderPlacedEvent event) {
        notificationRepository.save(new Notification("ORDER_CONFIRMED",
                "Order " + event.orderId() + " confirmed (" + event.lineItemCount() + " item(s))"));
    }

    @EventListener
    void onOrderRejected(OrderRejectedEvent event) {
        notificationRepository.save(new Notification("ORDER_REJECTED",
                "Order " + event.orderId() + " rejected: " + event.reason()));
    }

    @EventListener
    void onLowStock(LowStockEvent event) {
        notificationRepository.save(new Notification("LOW_STOCK",
                "Reorder needed: " + event.name() + " (" + event.productId() + ") has "
                        + event.remainingStock() + " left, below threshold of " + event.threshold()));
    }

    @EventListener
    void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        notificationRepository.save(new Notification("SUPPLIER_DELIVERED",
                "Supplier order " + event.poNumber() + " delivered: " + event.units()
                        + " unit(s) of " + event.productId() + " restocked"));
    }
}
