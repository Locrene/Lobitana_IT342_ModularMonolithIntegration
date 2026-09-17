package edu.cit.lobitana.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryView> getInventory() {
        return inventoryService.getAll().stream()
                .map(i -> new InventoryView(i.getProductId(), i.getName(), i.getStock(), inventoryService.isLowStock(i)))
                .toList();
    }
}
