-- Lab 2 schema: recreates the full current schema from scratch, including seed data.
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS order_items   CASCADE;
DROP TABLE IF EXISTS orders        CASCADE;
DROP TABLE IF EXISTS inventory     CASCADE;

-- Inventory table
CREATE TABLE inventory (
    product_id TEXT PRIMARY KEY,
    name       TEXT    NOT NULL,
    stock      INTEGER NOT NULL CHECK (stock >= 0)
);

-- Orders table (line items moved to order_items)
CREATE TABLE orders (
    order_id   BIGSERIAL PRIMARY KEY,
    status     TEXT NOT NULL CHECK (status IN ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Order line items (an order can have many)
CREATE TABLE order_items (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id      BIGINT  NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id    TEXT    NOT NULL REFERENCES inventory(product_id),
    quantity      INTEGER NOT NULL CHECK (quantity > 0)
);

-- Notification log (written by the Notification module)
CREATE TABLE notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    type            TEXT NOT NULL CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_REJECTED', 'LOW_STOCK')),
    message         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed data
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);
