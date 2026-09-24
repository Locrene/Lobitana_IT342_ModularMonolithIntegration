package edu.cit.lobitana.supplier;

import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    /**
     * "RO-" + id, so it cannot be derived until the row exists. Set immediately after the
     * insert in the same transaction, which is why the column is nullable in the DDL.
     */
    @Column(name = "buyer_ref", unique = true)
    private String buyerRef;

    /** Sent as X-Request-Id. Derived from buyerRef, so retries and restarts reuse it. */
    @Column(name = "request_id", unique = true)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    /** Units that will actually arrive: cases x pack size. This is what Inventory restocks. */
    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status = SupplierOrderStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierOrder() {}

    SupplierOrder(String productId, int cases, int units) {
        this.productId = productId;
        this.cases = cases;
        this.units = units;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** Called once, right after the insert assigned an id. */
    void assignReferences() {
        this.buyerRef = "RO-" + id;
        this.requestId = UUID.nameUUIDFromBytes(buyerRef.getBytes(StandardCharsets.UTF_8)).toString();
    }

    Long getId() { return id; }
    String getProductId() { return productId; }
    String getBuyerRef() { return buyerRef; }
    String getRequestId() { return requestId; }

    String getPoNumber() { return poNumber; }
    void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    int getCases() { return cases; }
    int getUnits() { return units; }

    SupplierOrderStatus getStatus() { return status; }
    void setStatus(SupplierOrderStatus status) { this.status = status; }

    LocalDateTime getCreatedAt() { return createdAt; }
    LocalDateTime getUpdatedAt() { return updatedAt; }

    SupplierOrderView toView() {
        return new SupplierOrderView(id, productId, buyerRef, requestId, poNumber,
                cases, units, status, createdAt, updatedAt);
    }
}
