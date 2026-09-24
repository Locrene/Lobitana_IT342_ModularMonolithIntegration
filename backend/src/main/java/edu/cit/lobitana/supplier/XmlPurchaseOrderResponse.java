package edu.cit.lobitana.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Used for both the create-order reply and the status lookup: XmlCodec unmarshals against
 * the declared type, so the tracking response adding CheckedAt does not need a second class.
 * Element names confirmed against the interface manual.
 */
@XmlRootElement(name = "PurchaseOrderAck")
@XmlAccessorType(XmlAccessType.FIELD)
class XmlPurchaseOrderResponse {

    @XmlElement(name = "PoNumber")
    private String poNumber;

    @XmlElement(name = "BuyerRef")
    private String buyerRef;

    /** LegacySupply's own numeric code (10/20/30/40). Never leaves this module untranslated. */
    @XmlElement(name = "StatusCode")
    private String statusCode;

    String getPoNumber() { return poNumber; }

    String getBuyerRef() { return buyerRef; }

    String getStatusCode() { return statusCode; }
}
