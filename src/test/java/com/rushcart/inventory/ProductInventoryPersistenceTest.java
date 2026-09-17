package com.rushcart.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductInventoryPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void persistsAndReadsBackProductAndInventory() {
        Product product = productRepository.save(
                new Product("SKU-001", "Limited Edition Sneaker", new BigDecimal("129.99")));

        Inventory inventory = inventoryRepository.save(new Inventory(product.getId(), 50));

        Product foundProduct = productRepository.findById(product.getId()).orElseThrow();
        Inventory foundInventory = inventoryRepository.findById(inventory.getProductId()).orElseThrow();

        assertThat(foundProduct.getSku()).isEqualTo("SKU-001");
        assertThat(foundInventory.getAvailableQty()).isEqualTo(50);
        assertThat(foundInventory.getReservedQty()).isZero();
    }
}
