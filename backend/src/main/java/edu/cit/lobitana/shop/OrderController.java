package edu.cit.lobitana.shop;

import edu.cit.lobitana.shop.OrderDtos.OrderRequest;
import edu.cit.lobitana.shop.OrderDtos.OrderResponse;
import edu.cit.lobitana.shop.OrderDtos.OrderView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.placeOrder(request.items());
    }

    @GetMapping
    public List<OrderView> getOrders() {
        return orderService.listOrders();
    }

    @PostMapping("/{orderId}/cancel")
    public OrderView cancelOrder(@PathVariable Long orderId) {
        return orderService.cancel(orderId);
    }
}
