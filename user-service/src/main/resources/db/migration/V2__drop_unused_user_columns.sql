-- Never written or read (left over from an early design where users listed their orders,
-- cart and products); every row held null / 0. Each service owns that data now.
ALTER TABLE users DROP COLUMN order_ids;
ALTER TABLE users DROP COLUMN product_ids;
ALTER TABLE users DROP COLUMN cart_id;
