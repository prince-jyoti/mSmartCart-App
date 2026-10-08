-- OrderExpiryJob looks for unpaid orders by status and age every minute.
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);
