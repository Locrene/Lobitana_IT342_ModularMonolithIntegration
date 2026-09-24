package edu.cit.lobitana.supplier;

/**
 * The only way into the supplier module. Callers speak in our own terms:
 * our product id and the number of units we want back on the shelf.
 * Nothing about LegacySupply (SKUs, pack sizes, XML, status codes) crosses this line.
 */
public interface SupplierGateway {

    ReorderOutcome reorder(String productId, int unitsNeeded);
}
