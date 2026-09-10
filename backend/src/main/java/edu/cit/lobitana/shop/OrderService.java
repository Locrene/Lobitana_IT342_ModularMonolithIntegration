package edu.cit.lobitana.shop;

import edu.cit.lobitana.inventory.InventoryService;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(String productId, int quantity) {
        Order order = new Order();
        order.setProductId(productId);
        order.setQuantity(quantity);

        boolean reserved = inventoryService.reserve(productId, quantity);

        if (reserved) {
            order.setStatus("CONFIRMED");
            order.setReason(null);
        } else {
            order.setStatus("REJECTED");
            order.setReason(determineRejectReason(productId, quantity));
        }

        return orderRepository.save(order);
    }

    private String determineRejectReason(String productId, int quantity) {
        return inventoryService.getItem(productId)
                .map(item -> "Insufficient stock: requested " + quantity + ", available " + item.getStock())
                .orElse("Product not found: " + productId);
    }
}