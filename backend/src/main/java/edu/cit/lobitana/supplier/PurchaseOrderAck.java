package edu.cit.lobitana.supplier;

/** What LegacySupply says about one purchase order. statusCode is still their code. */
record PurchaseOrderAck(String poNumber, String buyerRef, String statusCode) {
}
