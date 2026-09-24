package edu.cit.lobitana.supplier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only window on what the adapter has done. Useful as lab evidence. */
@RestController
@RequestMapping("/api/supplier-orders")
class SupplierOrderController {

    private final SupplierOrderStore store;

    SupplierOrderController(SupplierOrderStore store) {
        this.store = store;
    }

    @GetMapping
    List<SupplierOrderView> recent() {
        return store.recent();
    }
}
