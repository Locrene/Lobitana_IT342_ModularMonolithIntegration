package edu.cit.lobitana.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The anti-corruption layer itself. Above it: our product ids, units and statuses.
 * Below it: SKUs, cases, XML and LegacySupply status codes.
 */
@Service
class LegacySupplyAdapter implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyAdapter.class);

    private final SupplierOrderStore store;
    private final LegacySupplyClient client;
    private final UnitTranslator unitTranslator;
    private final LegacySupplyStatusTranslator statusTranslator;

    LegacySupplyAdapter(SupplierOrderStore store,
                        LegacySupplyClient client,
                        UnitTranslator unitTranslator,
                        LegacySupplyStatusTranslator statusTranslator) {
        this.store = store;
        this.client = client;
        this.unitTranslator = unitTranslator;
        this.statusTranslator = statusTranslator;
    }

    @Override
    public ReorderOutcome reorder(String productId, int unitsNeeded) {
        if (productId == null || productId.isBlank() || unitsNeeded <= 0) {
            throw new IllegalArgumentException("Reorder needs a product id and a positive unit count");
        }

        // One open order per product at a time: a second low-stock event while a PO is
        // still in flight must not become a second purchase order.
        Optional<SupplierOrder> inFlight = store.findOpenFor(productId);
        if (inFlight.isPresent()) {
            SupplierOrder existing = inFlight.get();
            log.info("Reorder for {} skipped: {} is already open with status {}",
                    productId, existing.getBuyerRef(), existing.getStatus());
            return outcome(existing);
        }

        UnitTranslator.SupplierLine line = unitTranslator.toSupplierLine(productId, unitsNeeded);
        SupplierOrder order = store.createPending(productId, line.cases(), line.units());
        log.info("Reorder {} created for {}: {} unit(s) needed -> {} case(s) = {} unit(s)",
                order.getBuyerRef(), productId, unitsNeeded, line.cases(), line.units());

        return send(order, false);
    }

    /** Used by the retry job: looks the order up first so a resend can never duplicate it. */
    ReorderOutcome resend(SupplierOrder order) {
        return send(order, true);
    }

    private ReorderOutcome send(SupplierOrder order, boolean checkForExisting) {
        try {
            if (checkForExisting) {
                Optional<PurchaseOrderAck> alreadyThere = client.findByBuyerRef(order.getBuyerRef());
                if (alreadyThere.isPresent()) {
                    log.info("{} already exists at LegacySupply as PO {}; adopting it instead of resending",
                            order.getBuyerRef(), alreadyThere.get().poNumber());
                    return outcome(accept(order, alreadyThere.get()));
                }
            }

            UnitTranslator.SupplierLine line = unitTranslator.toSupplierLine(order.getProductId(), order.getUnits());
            PurchaseOrderAck ack = client.submitPurchaseOrder(
                    order.getBuyerRef(), order.getRequestId(), line.sku(), order.getCases());

            log.info("{} accepted by LegacySupply as PO {}", order.getBuyerRef(), ack.poNumber());
            return outcome(accept(order, ack));

        } catch (LegacySupplyException e) {
            if (e.isRetryable()) {
                // Stays PENDING. The scheduled job will send it, with the same request id.
                log.warn("{} could not be sent yet, left PENDING: {}", order.getBuyerRef(), e.getMessage());
                return outcome(order);
            }
            log.error("{} permanently refused by LegacySupply: {}", order.getBuyerRef(), e.getMessage());
            return outcome(store.update(order.getId(), SupplierOrderStatus.FAILED, null));
        }
    }

    private SupplierOrder accept(SupplierOrder order, PurchaseOrderAck ack) {
        SupplierOrderStatus status = statusTranslator.toDomain(ack.statusCode());
        if (status == SupplierOrderStatus.UNKNOWN) {
            // We hold a PO number, so it was accepted, whatever the code happened to say.
            status = SupplierOrderStatus.SUBMITTED;
        }
        if (ack.poNumber() == null || ack.poNumber().isBlank()) {
            log.warn("{} was accepted without a PO number; status will be polled by BuyerRef", order.getBuyerRef());
        }
        return store.update(order.getId(), status, ack.poNumber());
    }

    private ReorderOutcome outcome(SupplierOrder order) {
        return new ReorderOutcome(order.getId(), order.getBuyerRef(), order.getProductId(),
                order.getUnits(), order.getStatus());
    }
}
