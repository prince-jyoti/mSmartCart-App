-- Never written or read: user_id was always 0 and active always true (an unused soft-delete idea).
ALTER TABLE products DROP COLUMN user_id;
ALTER TABLE products DROP COLUMN active;
