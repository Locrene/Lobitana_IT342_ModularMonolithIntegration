package edu.cit.lobitana.shop;

import edu.cit.lobitana.inventory.Inventory;
import edu.cit.lobitana.inventory.InventoryService;
import edu.cit.lobitana.inventory.InventoryView;
import edu.cit.lobitana.shop.OrderDtos.*;
import edu.cit.lobitana.shop.events.OrderPlacedEvent;
import edu.cit.lobitana.shop.events.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    static final String CONFIRMED = "CONFIRMED";
    static final String REJECTED = "REJECTED";
    static final String CANCELLED = "CANCELLED";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(List<LineItem> requestItems) {
        if (requestItems == null || requestItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }

        // Merge duplicate products so two P100 lines are checked as one total.
        Map<String, Integer> lines = new LinkedHashMap<>();
        for (LineItem li : requestItems) {
            if (li.productId() == null || li.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each item needs a productId and a quantity greater than 0");
            }
            lines.merge(li.productId(), li.quantity(), Integer::sum);
        }

        // ---- Step 1: validate EVERY line before reserving ANYTHING ----
        Map<String, String> outcomes = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> line : lines.entrySet()) {
            Inventory item = inventoryService.getItem(line.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Product not found: " + line.getKey()));
            if (item.getStock() < line.getValue()) {
                outcomes.put(line.getKey(), "INSUFFICIENT_STOCK");
                problems.add(line.getKey() + " (requested " + line.getValue() + ", available " + item.getStock() + ")");
            } else {
                outcomes.put(line.getKey(), "NOT_RESERVED");
            }
        }

        Order order = new Order();
        lines.forEach(order::addItem);

        if (!problems.isEmpty()) {
            order.setStatus(REJECTED);
            order.setReason("Insufficient stock: " + String.join(", ", problems));
            orderRepository.save(order);
            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), order.getReason()));
            return toResponse(order, lines, outcomes);
        }

        // ---- Step 2: all lines passed, reserve each one ----
        order.setStatus(CONFIRMED);
        orderRepository.save(order);

        for (Map.Entry<String, Integer> line : lines.entrySet()) {
            if (!inventoryService.reserve(line.getKey(), line.getValue())) {
                // Only possible if stock changed after validation. Throwing rolls back
                // the order row and every reserve() already done in this transaction.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock changed while placing order; nothing was reserved");
            }
            outcomes.put(line.getKey(), "RESERVED");
        }

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), lines.size()));
        return toResponse(order, lines, outcomes);
    }

    @Transactional
    public OrderView cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));

        if (CANCELLED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " is already CANCELLED");
        }
        if (REJECTED.equals(order.getStatus())) {
            // A rejected order reserved nothing, so restocking it would create stock out of thin air.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " was REJECTED and has nothing to restock");
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }
        order.setStatus(CANCELLED);
        orderRepository.save(order);
        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> listOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    private OrderResponse toResponse(Order order, Map<String, Integer> lines, Map<String, String> outcomes) {
        List<ItemOutcome> items = lines.entrySet().stream()
                .map(e -> new ItemOutcome(e.getKey(), e.getValue(), outcomes.get(e.getKey())))
                .toList();
        List<InventoryView> inventory = inventoryService.getAll().stream()
                .map(i -> new InventoryView(i.getProductId(), i.getName(), i.getStock(), inventoryService.isLowStock(i)))
                .toList();
        return new OrderResponse(order.getOrderId(), order.getStatus(), order.getReason(), items, inventory);
    }

    private OrderView toView(Order order) {
        List<LineItem> items = order.getItems().stream()
                .map(i -> new LineItem(i.getProductId(), i.getQuantity()))
                .toList();
        return new OrderView(order.getOrderId(), order.getStatus(), order.getReason(), order.getCreatedAt(), items);
    }
}
