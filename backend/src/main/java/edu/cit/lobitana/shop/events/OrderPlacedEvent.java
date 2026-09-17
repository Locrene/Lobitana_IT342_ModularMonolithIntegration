package edu.cit.lobitana.shop.events;

public record OrderPlacedEvent(Long orderId, int lineItemCount) {
}
