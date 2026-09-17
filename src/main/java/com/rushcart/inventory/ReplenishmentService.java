package com.rushcart.inventory;

import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates a write that must land in both Redis and Postgres together (§5.2) —
 * the one place a Redisson distributed lock is used, since the hot reservation
 * path (§5.1) relies on Lua atomicity alone and doesn't need one.
 */
@Service
public class ReplenishmentService {

    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 10;

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockService stockService;

    public ReplenishmentService(
            RedissonClient redissonClient,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            StockService stockService) {
        this.redissonClient = redissonClient;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockService = stockService;
    }

    @Transactional
    public void replenish(String sku, int qty) {
        RLock lock = redissonClient.getLock("lock:replenish:" + sku);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring replenishment lock for " + sku, e);
        }
        if (!acquired) {
            throw new IllegalStateException("Could not acquire replenishment lock for " + sku);
        }
        try {
            Product product = productRepository.findBySku(sku).orElseThrow();
            Inventory inventory = inventoryRepository.findById(product.getId()).orElseThrow();
            inventory.increaseAvailable(qty);
            inventoryRepository.save(inventory);
            stockService.compensate(sku, qty);
        } finally {
            lock.unlock();
        }
    }
}
