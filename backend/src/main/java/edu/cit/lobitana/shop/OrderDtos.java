package edu.cit.lobitana.shop;

import edu.cit.lobitana.inventory.InventoryView;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {}

    public record LineItem(String productId, int quantity) {}

    public record OrderRequest(List<LineItem> items) {}

    public record ItemOutcome(String productId, int quantity, String outcome) {}

    public record OrderResponse(Long orderId, String status, String reason,
                                List<ItemOutcome> items, List<InventoryView> inventory) {}

    public record OrderView(Long orderId, String status, String reason,
                            LocalDateTime createdAt, List<LineItem> items) {}
}
