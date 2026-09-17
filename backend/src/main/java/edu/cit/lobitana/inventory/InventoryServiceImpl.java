package edu.cit.lobitana.inventory;

import edu.cit.lobitana.inventory.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                         ApplicationEventPublisher eventPublisher,
                         @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public List<Inventory> getAll() {
        return inventoryRepository.findAll(Sort.by("productId"));
    }

    @Override
    public Optional<Inventory> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        Optional<Inventory> found = inventoryRepository.findById(productId);

        if (found.isEmpty() || quantity <= 0) {
            return false;
        }

        Inventory item = found.get();
        if (item.getStock() < quantity) {
            return false;
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);

        if (isLowStock(item)) {
            eventPublisher.publishEvent(new LowStockEvent(
                    item.getProductId(), item.getName(), item.getStock(), lowStockThreshold));
        }
        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        Inventory item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }

    @Override
    public boolean isLowStock(Inventory item) {
        return item.getStock() < lowStockThreshold;
    }
}
