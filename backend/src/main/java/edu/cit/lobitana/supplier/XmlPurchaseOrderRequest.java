package edu.cit.lobitana.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * A purchase order in LegacySupply's vocabulary: their SKU, a quantity expressed in
 * their unit of measure, and our BuyerRef so the order can be found again.
 */
// FILL IN: root element and field names from the interface manual.
@XmlRootElement(name = "PurchaseOrder")
@XmlAccessorType(XmlAccessType.FIELD)
class XmlPurchaseOrderRequest {

    @XmlElement(name = "SupplierSku")
    private String supplierSku;

    @XmlElement(name = "Qty")
    private int qty;

    @XmlElement(name = "BuyerRef")
    private String buyerRef;

    XmlPurchaseOrderRequest() {}

    XmlPurchaseOrderRequest(String supplierSku, int qty, String buyerRef) {
        this.supplierSku = supplierSku;
        this.qty = qty;
        this.buyerRef = buyerRef;
    }
}

