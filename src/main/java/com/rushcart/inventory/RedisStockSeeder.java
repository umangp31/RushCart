package com.rushcart.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * §11: reconciles Redis {@code stock:{sku}} keys from Postgres {@code inventory} on startup,
 * local profile only — production Redis state is expected to already be live/consistent.
 */
@Component
@Profile("local")
public class RedisStockSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockService stockService;

    public RedisStockSeeder(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            StockService stockService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockService = stockService;
    }

    @Override
    public void run(String... args) {
        for (Product product : productRepository.findAll()) {
            inventoryRepository
                    .findById(product.getId())
                    .ifPresent(inventory -> stockService.seed(product.getSku(), inventory.getAvailableQty()));
        }
    }
}
