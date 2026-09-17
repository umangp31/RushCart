-- Local-profile-only demo images (§11). FLASH-SCARCE-001 is left NULL on purpose to exercise the placeholder.
UPDATE products SET image_url = 'https://picsum.photos/seed/rushcart-sneaker/640/480' WHERE sku = 'SNEAKER-LTD-001';
UPDATE products SET image_url = 'https://picsum.photos/seed/rushcart-hoodie/640/480'  WHERE sku = 'HOODIE-STD-001';
