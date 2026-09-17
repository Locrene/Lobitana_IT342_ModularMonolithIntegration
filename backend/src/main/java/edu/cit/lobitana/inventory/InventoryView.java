package edu.cit.lobitana.inventory;

/** API shape for an inventory row, including the low-stock flag. */
public record InventoryView(String productId, String name, int stock, boolean lowStock) {
}
