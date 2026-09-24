package edu.cit.lobitana.supplier;

import java.time.LocalDateTime;

/** Read-only shape for the supplier orders endpoint (evidence screen). */
public record SupplierOrderView(Long id,
                                String productId,
                                String buyerRef,
                                String requestId,
                                String poNumber,
                                int cases,
                                int units,
                                SupplierOrderStatus status,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt) {
}
