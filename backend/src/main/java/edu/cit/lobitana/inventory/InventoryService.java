package edu.cit.lobitana.inventory;

import java.util.Optional;

public interface InventoryService {

    Optional<Inventory> getItem(String productId);

    /**
     * Attempts to reserve the given quantity from stock.
     * Returns true if the reservation succeeded, false if there wasn't enough
     * stock or the product doesn't exist.
     */
    boolean reserve(String productId, int quantity);
}