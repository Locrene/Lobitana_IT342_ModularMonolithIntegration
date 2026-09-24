package edu.cit.lobitana.supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * All database work for supplier orders, kept in its own bean so that every transaction
 * is short and no HTTP call ever runs while a transaction is open.
 */
@Service
class SupplierOrderStore {

    // Derived from the enum so a new status cannot silently fall out of these queries.
    private static final List<SupplierOrderStatus> OPEN =
            Arrays.stream(SupplierOrderStatus.values()).filter(SupplierOrderStatus::isOpen).toList();

    /** Open, and already sent: the orders the polling job asks LegacySupply about. */
    private static final List<SupplierOrderStatus> AWAITING_DELIVERY =
            OPEN.stream().filter(status -> status != SupplierOrderStatus.PENDING).toList();

    private final SupplierOrderRepository repository;

    SupplierOrderStore(SupplierOrderRepository repository) {
        this.repository = repository;
    }

    // The three methods below run from AutoReorderListener, which fires in the AFTER_COMMIT
    // phase. The original transaction's resources are still bound to the thread there, so a
    // REQUIRED transaction would join a transaction that has already committed and the writes
    // would be discarded on cleanup. REQUIRES_NEW forces a real, independent transaction.

    /** Guards against a second PO for a product that already has one in flight. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<SupplierOrder> findOpenFor(String productId) {
        return repository.findFirstByProductIdAndStatusInOrderByIdAsc(productId, OPEN);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SupplierOrder createPending(String productId, int cases, int units) {
        SupplierOrder order = repository.saveAndFlush(new SupplierOrder(productId, cases, units));
        order.assignReferences();
        return repository.saveAndFlush(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SupplierOrder update(Long id, SupplierOrderStatus status, String poNumber) {
        SupplierOrder order = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Supplier order vanished: " + id));
        order.setStatus(status);
        if (poNumber != null && !poNumber.isBlank()) {
            order.setPoNumber(poNumber);
        }
        return repository.saveAndFlush(order);
    }

    @Transactional(readOnly = true)
    List<SupplierOrder> findPending(int batchSize) {
        return repository.findByStatusInOrderByIdAsc(
                List.of(SupplierOrderStatus.PENDING), PageRequest.of(0, batchSize));
    }

    /** Orders worth polling: already sent, not yet finished. */
    @Transactional(readOnly = true)
    List<SupplierOrder> findAwaitingDelivery(int batchSize) {
        return repository.findByStatusInOrderByIdAsc(AWAITING_DELIVERY, PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    List<SupplierOrderView> recent() {
        return repository.findTop50ByOrderByIdDesc().stream().map(SupplierOrder::toView).toList();
    }
}
