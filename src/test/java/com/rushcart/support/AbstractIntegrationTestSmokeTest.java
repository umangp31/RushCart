package com.rushcart.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbstractIntegrationTestSmokeTest extends AbstractIntegrationTest {

    @Test
    void contextLoadsAgainstRealContainers() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
    }
}
