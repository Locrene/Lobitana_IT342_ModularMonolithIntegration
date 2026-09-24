package edu.cit.lobitana.supplier;

/**
 * What a reorder attempt produced, in our terms.
 *
 * @param unitsOrdered units that will actually arrive (cases x pack size), which is
 *                     usually more than the units asked for because LegacySupply sells whole cases.
 */
public record ReorderOutcome(Long supplierOrderId,
                             String buyerRef,
                             String productId,
                             int unitsOrdered,
                             SupplierOrderStatus status) {
}
