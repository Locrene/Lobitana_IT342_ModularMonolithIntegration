package edu.cit.lobitana.supplier;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    Optional<SupplierOrder> findByBuyerRef(String buyerRef);

    Optional<SupplierOrder> findFirstByProductIdAndStatusInOrderByIdAsc(
            String productId, Collection<SupplierOrderStatus> statuses);

    List<SupplierOrder> findByStatusInOrderByIdAsc(Collection<SupplierOrderStatus> statuses, Pageable page);

    List<SupplierOrder> findTop50ByOrderByIdDesc();
}
