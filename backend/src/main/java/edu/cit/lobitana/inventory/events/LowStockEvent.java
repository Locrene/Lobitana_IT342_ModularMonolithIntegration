package edu.cit.lobitana.inventory.events;

public record LowStockEvent(String productId, String name, int remainingStock, int threshold) {
}
