package com.rushcart.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProductControllerTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-PRODUCT-API";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createGetAndReplenishFlow() {
        var createRequest = new ProductController.CreateProductRequest(SKU, "API Test Item", new BigDecimal("12.50"), 10);
        ResponseEntity<ProductController.ProductResponse> createResponse =
                restTemplate.postForEntity("/api/v1/products", createRequest, ProductController.ProductResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().liveStock()).isEqualTo(10);

        ResponseEntity<ProductController.ProductResponse> getResponse =
                restTemplate.getForEntity("/api/v1/products/" + SKU, ProductController.ProductResponse.class);
        assertThat(getResponse.getBody().liveStock()).isEqualTo(10);

        var replenishRequest = new ProductController.ReplenishRequest(5);
        ResponseEntity<ProductController.ProductResponse> replenishResponse = restTemplate.postForEntity(
                "/api/v1/products/" + SKU + "/replenish", replenishRequest, ProductController.ProductResponse.class);
        assertThat(replenishResponse.getBody().liveStock()).isEqualTo(15);
    }
}
