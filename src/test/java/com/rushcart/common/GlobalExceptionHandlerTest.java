package com.rushcart.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.order.OrderController;
import com.rushcart.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void validationFailureReturnsProblemJsonWithFieldErrors() {
        var invalidRequest = new OrderController.ReserveOrderRequest(null, "", 0);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/orders", invalidRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).contains("\"errors\"");
    }

    @Test
    void apiExceptionReturnsProblemJson() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/orders/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).contains("\"status\":404");
    }
}
