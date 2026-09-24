package edu.cit.lobitana.supplier;

import org.springframework.stereotype.Component;

/**
 * Turns "we need N units of our product P" into "Qty cases of SKU S".
 * LegacySupply only sells whole cases, so the quantity is always rounded up, which means
 * more units arrive than were asked for. The extra units are recorded, not discarded.
 */
@Component
class UnitTranslator {

    private final LegacySupplyProperties properties;

    UnitTranslator(LegacySupplyProperties properties) {
        this.properties = properties;
    }

    SupplierLine toSupplierLine(String productId, int unitsNeeded) {
        LegacySupplyProperties.Product product = properties.product(productId)
                .orElseThrow(() -> new LegacySupplyException(
                        "No LegacySupply mapping configured for product " + productId, false));

        if (product.getSku() == null || product.getSku().isBlank()) {
            throw new LegacySupplyException("Product " + productId + " has no supplier SKU configured", false);
        }
        int packSize = Math.max(1, product.getPackSize());
        int cases = Math.max(1, ceilDiv(unitsNeeded, packSize));

        return new SupplierLine(product.getSku(), cases, cases * packSize);
    }

    private int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    /**
     * Qty is sent as a plain case count: the SKU already tells LegacySupply what a case is,
     * so the request carries no Uom (it only comes back on the response).
     *
     * @param units what will actually arrive: cases x pack size.
     */
    record SupplierLine(String sku, int cases, int units) {
    }
}
