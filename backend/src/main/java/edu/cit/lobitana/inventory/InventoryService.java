package edu.cit.lobitana.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryService {

    List<Inventory> getAll();

    Optional<Inventory> getItem(String productId);

    /**
     * Attempts to reserve the given quantity from stock.
     * Returns true if the reservation succeeded, false if there wasn't enough
     * stock or the product doesn't exist.
     * Publishes a LowStockEvent if remaining stock drops below the threshold.
     */
    boolean reserve(String productId, int quantity);

    /** Returns the given quantity to stock (used when an order is cancelled). */
    void restock(String productId, int quantity);

    boolean isLowStock(Inventory item);
}
