-- Money was stored as double precision (binary floating point: 0.1 + 0.2 != 0.3, and totals can
-- drift by fractions of a paisa). numeric(12,2) stores exact rupees and paise; existing values are
-- rounded to 2 decimals, which only removes floating-point noise.
ALTER TABLE order_items ALTER COLUMN price TYPE numeric(12,2) USING round(price::numeric, 2);
ALTER TABLE orders ALTER COLUMN total_amount TYPE numeric(12,2) USING round(total_amount::numeric, 2);
