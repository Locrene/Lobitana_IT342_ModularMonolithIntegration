package edu.cit.lobitana.supplier;

/** Our vocabulary for where a supplier order stands. LegacySupply's codes are translated into this. */
public enum SupplierOrderStatus {

    /** Saved on our side, not yet accepted by the supplier. Retried by the scheduled job. */
    PENDING,
    /** The supplier accepted the request and gave us a PO number. */
    SUBMITTED,
    /** The supplier confirmed the PO is being fulfilled. */
    CONFIRMED,
    /** The supplier is assembling the order. */
    PICKING,
    /** The order has left the supplier but has not arrived. */
    SHIPPED,
    /** Goods arrived; inventory has been restocked. */
    DELIVERED,
    /** The supplier permanently refused this order. No further attempts. */
    FAILED,
    /** The supplier reported something we do not recognise. Kept open and polled again. */
    UNKNOWN;

    /** Open orders are the ones the polling job keeps watching: everything not yet settled. */
    public boolean isOpen() {
        return this != DELIVERED && this != FAILED;
    }
}
