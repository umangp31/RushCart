package com.rushcart.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"rushcart.ratelimit.capacity=5", "rushcart.ratelimit.refill-per-second=0.001"})
class RateLimiterInterceptorTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exceedingBudgetReturns429AndWithinBudgetSucceeds() {
        String apiKey = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        int rejected = 0;
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/orders/" + UUID.randomUUID(), HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().value() == 429) {
                rejected++;
            } else {
                allowed++;
                assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            }
        }

        assertThat(allowed).isEqualTo(5);
        assertThat(rejected).isEqualTo(5);
    }
}
