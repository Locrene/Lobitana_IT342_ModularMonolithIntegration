package edu.cit.lobitana.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The one place LegacySupply's status codes are understood. Everything past this class
 * speaks SupplierOrderStatus. Codes we have never seen become UNKNOWN: the order stays
 * open and is polled again, and nothing is restocked on a guess.
 */
@Component
class LegacySupplyStatusTranslator {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyStatusTranslator.class);

    private final LegacySupplyProperties properties;

    LegacySupplyStatusTranslator(LegacySupplyProperties properties) {
        this.properties = properties;
    }

    SupplierOrderStatus toDomain(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            log.warn("LegacySupply returned an order with no status code; treating as UNKNOWN");
            return SupplierOrderStatus.UNKNOWN;
        }

        String code = supplierCode.trim().toUpperCase(Locale.ROOT);
        String mapped = properties.getStatusMap().get(code);
        if (mapped == null) {
            log.warn("Unmapped LegacySupply status code '{}'; treating as UNKNOWN. "
                    + "Add legacysupply.status-map.{}=<our status> once its meaning is known", code, code);
            return SupplierOrderStatus.UNKNOWN;
        }

        try {
            return SupplierOrderStatus.valueOf(mapped.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("legacysupply.status-map.{} is set to '{}', which is not one of our statuses", code, mapped);
            return SupplierOrderStatus.UNKNOWN;
        }
    }
}
