package com.rushcart.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.ProductController;
import com.rushcart.order.OrderController;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers the read endpoints the Angular admin dashboard (§14) consumes. */
class DashboardEndpointsTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void productListExposesRedisAndPostgresStock() {
        String sku = "SKU-DASH-" + UUID.randomUUID();
        restTemplate.postForEntity(
                "/api/v1/products",
                new ProductController.CreateProductRequest(sku, "Dash Item", new BigDecimal("9.99"), 7),
                ProductController.ProductResponse.class);

        ResponseEntity<ProductController.InventoryRow[]> response =
                restTemplate.getForEntity("/api/v1/products", ProductController.InventoryRow[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .anySatisfy(row -> {
                    assertThat(row.sku()).isEqualTo(sku);
                    assertThat(row.redisStock()).isEqualTo(7);
                    assertThat(row.pgAvailableQty()).isEqualTo(7);
                    assertThat(row.pgReservedQty()).isEqualTo(0);
                });
    }

    @Test
    void orderListAndTimelineReflectReservation() {
        String sku = "SKU-DASH-ORD-" + UUID.randomUUID();
        restTemplate.postForEntity(
                "/api/v1/products",
                new ProductController.CreateProductRequest(sku, "Dash Order Item", new BigDecimal("1.00"), 5),
                ProductController.ProductResponse.class);

        UUID customerId = UUID.randomUUID();
        ResponseEntity<OrderController.OrderResponse> reserve = restTemplate.postForEntity(
                "/api/v1/orders",
                new OrderController.ReserveOrderRequest(customerId, sku, 2),
                OrderController.OrderResponse.class);
        UUID orderId = reserve.getBody().id();

        ResponseEntity<OrderController.OrderResponse[]> list = restTemplate.exchange(
                "/api/v1/orders?customerId=" + customerId,
                HttpMethod.GET,
                null,
                OrderController.OrderResponse[].class);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody()[0].id()).isEqualTo(orderId);

        ResponseEntity<java.util.List<OrderController.OrderEventResponse>> events = restTemplate.exchange(
                "/api/v1/orders/" + orderId + "/events",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(events.getBody()).extracting(OrderController.OrderEventResponse::eventType).contains("RESERVED");
    }

    @Test
    void adminStatusReportsBreakerAndRateLimiter() {
        ResponseEntity<AdminStatusController.AdminStatus> response =
                restTemplate.getForEntity("/api/v1/admin/status", AdminStatusController.AdminStatus.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reservationWrite().name()).isEqualTo("reservationWrite");
        assertThat(response.getBody().reservationWrite().state()).isIn("CLOSED", "OPEN", "HALF_OPEN");
        assertThat(response.getBody().rateLimiter().capacity()).isGreaterThan(0);
        assertThat(response.getBody().rateLimiter().redisReachable()).isTrue();
    }
}
