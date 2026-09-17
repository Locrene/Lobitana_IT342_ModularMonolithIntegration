package edu.cit.lobitana.shop.events;

public record OrderRejectedEvent(Long orderId, String reason) {
}
