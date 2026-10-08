-- What was bought, as it was at order time (price is already stored this way). Lets order
-- responses be built without asking product-service about every item, and keeps old orders
-- readable after a product is renamed or deleted. Older rows are filled in on first read.
ALTER TABLE order_items ADD COLUMN title varchar(255);
ALTER TABLE order_items ADD COLUMN image varchar(255);
