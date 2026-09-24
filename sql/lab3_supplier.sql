-- Lab 3: LegacySupply integration. Additive migration for an existing Lab 2 database.
-- Run this in the Supabase SQL editor.

CREATE TABLE IF NOT EXISTS supplier_orders (
    id         BIGSERIAL PRIMARY KEY,
    product_id TEXT    NOT NULL REFERENCES inventory(product_id),
    -- buyer_ref is "RO-" + id, so it cannot exist until the row does: the adapter
    -- inserts, then fills both refs in the same transaction. Hence nullable, but unique.
    buyer_ref  TEXT    UNIQUE,
    request_id TEXT    UNIQUE,
    po_number  TEXT,
    cases      INTEGER NOT NULL CHECK (cases > 0),
    units      INTEGER NOT NULL CHECK (units > 0),
    -- Our own vocabulary, never LegacySupply's codes.
    status     TEXT    NOT NULL CHECK (status IN
                   ('PENDING', 'SUBMITTED', 'CONFIRMED', 'PICKING', 'SHIPPED',
                    'DELIVERED', 'FAILED', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The retry and polling jobs both select by status.
CREATE INDEX IF NOT EXISTS idx_supplier_orders_status ON supplier_orders (status);

-- A PO number may only ever belong to one row: a second one would mean a duplicate order.
CREATE UNIQUE INDEX IF NOT EXISTS uq_supplier_orders_po_number
    ON supplier_orders (po_number) WHERE po_number IS NOT NULL;

-- Safe to re-run: brings the status list up to date if an earlier version of this
-- migration already created the table without PICKING and SHIPPED.
ALTER TABLE supplier_orders DROP CONSTRAINT IF EXISTS supplier_orders_status_check;
ALTER TABLE supplier_orders ADD CONSTRAINT supplier_orders_status_check
    CHECK (status IN ('PENDING', 'SUBMITTED', 'CONFIRMED', 'PICKING', 'SHIPPED',
                      'DELIVERED', 'FAILED', 'UNKNOWN'));

-- The notification log gains one more type.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_REJECTED', 'LOW_STOCK', 'SUPPLIER_DELIVERED'));
