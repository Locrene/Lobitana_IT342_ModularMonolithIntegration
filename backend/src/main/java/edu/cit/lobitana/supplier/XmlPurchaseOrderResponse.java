package edu.cit.lobitana.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Used for both the create-order reply and the status lookup: XmlCodec unmarshals against
 * the declared type, so the two endpoints may use different root element names.
 */
// FILL IN: field names from the interface manual.
@XmlRootElement(name = "PurchaseOrder")
@XmlAccessorType(XmlAccessType.FIELD)
class XmlPurchaseOrderResponse {

    @XmlElement(name = "PoNumber")
    private String poNumber;

    @XmlElement(name = "BuyerRef")
    private String buyerRef;

    /** LegacySupply's own status code. Never leaves this module untranslated. */
    @XmlElement(name = "Status")
    private String status;

    String getPoNumber() { return poNumber; }

    String getBuyerRef() { return buyerRef; }

    String getStatus() { return status; }
}
