package edu.cit.lobitana.inventory;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Optional<Inventory> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    public boolean reserve(String productId, int quantity) {
        Optional<Inventory> found = inventoryRepository.findById(productId);

        if (found.isEmpty()) {
            return false;
        }

        Inventory item = found.get();
        if (item.getStock() < quantity) {
            return false;
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return true;
    }
}