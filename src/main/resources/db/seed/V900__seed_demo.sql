-- Local-profile-only demo data (§11). Loaded via the extra Flyway location added in
-- application-local.yml, never applied against a production/shared database.

INSERT INTO products (id, sku, name, price) VALUES
    (gen_random_uuid(), 'SNEAKER-LTD-001', 'Limited Edition Sneaker', 129.99),
    (gen_random_uuid(), 'HOODIE-STD-001', 'Standard Hoodie', 49.99),
    (gen_random_uuid(), 'FLASH-SCARCE-001', 'Flash Sale Scarce Item', 9.99);

INSERT INTO inventory (product_id, available_qty)
SELECT id, CASE sku
    WHEN 'SNEAKER-LTD-001' THEN 100
    WHEN 'HOODIE-STD-001' THEN 500
    WHEN 'FLASH-SCARCE-001' THEN 3
END
FROM products
WHERE sku IN ('SNEAKER-LTD-001', 'HOODIE-STD-001', 'FLASH-SCARCE-001');
