package edu.cit.lobitana.supplier.events;

/**
 * Published when a supplier order is confirmed delivered. Inventory listens and restocks.
 * Carries only our own terms: our product id and a unit count.
 */
public record SupplierOrderDeliveredEvent(Long supplierOrderId,
                                          String productId,
                                          int units,
                                          String poNumber) {
}
