package edu.cit.lobitana.shop;

import edu.cit.lobitana.inventory.Inventory;
import edu.cit.lobitana.inventory.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    public OrderController(OrderService orderService, InventoryService inventoryService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<Object> createOrder(@RequestBody OrderRequest request) {
        Order order = orderService.placeOrder(request.getProductId(), request.getQuantity());

        Map<String, Object> response = new HashMap<>();
        response.put("status", order.getStatus());
        response.put("reason", order.getReason());

        inventoryService.getItem(request.getProductId()).ifPresent(item -> {
            Map<String, Object> inventoryInfo = new HashMap<>();
            inventoryInfo.put("productId", item.getProductId());
            inventoryInfo.put("name", item.getName());
            inventoryInfo.put("stock", item.getStock());
            response.put("inventory", inventoryInfo);
        });

        return ResponseEntity.ok(response);
    }

    static class OrderRequest {
        private String productId;
        private int quantity;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}