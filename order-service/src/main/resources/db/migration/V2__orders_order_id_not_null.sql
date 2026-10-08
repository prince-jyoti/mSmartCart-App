-- Every order gets an ORD-<uuid> id when it is created, and payment-service looks orders up
-- by it. ddl-auto=update never tightened the existing column, so the entity's
-- nullable = false was only enforced for new tables. Fails (and changes nothing) if an
-- order without an id exists.
ALTER TABLE orders ALTER COLUMN order_id SET NOT NULL;
