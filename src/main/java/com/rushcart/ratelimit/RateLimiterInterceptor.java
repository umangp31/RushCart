package com.rushcart.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Gateway-layer rate limiter (§7), implemented as a servlet filter in front of
 * {@code /api/v1/**} rather than a separate Spring Cloud Gateway process — this project
 * is a single deployable (§1 non-goals), so the edge concern lives in-process.
 *
 * <p>Fails open (§7, §24): if Redis is unreachable, the request is allowed through rather
 * than blocked — availability of the sale outranks throttling during a Redis outage.
 */
@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterInterceptor.class);

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final int capacity;
    private final double refillPerSecond;

    public RateLimiterInterceptor(
            RateLimiterService rateLimiterService,
            ObjectMapper objectMapper,
            @Value("${rushcart.ratelimit.capacity:20}") int capacity,
            @Value("${rushcart.ratelimit.refill-per-second:5}") double refillPerSecond) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String clientKey = clientKey(request);
        boolean allowed;
        try {
            allowed = rateLimiterService.tryAcquire(clientKey, capacity, refillPerSecond);
        } catch (RuntimeException e) {
            log.warn("Rate limiter Redis call failed, failing open: {}", e.getMessage());
            return true;
        }
        if (!allowed) {
            writeTooManyRequests(response);
            return false;
        }
        return true;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "1");
        response.setContentType("application/problem+json");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded, retry after 1 second");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private String clientKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-Api-Key");
        return apiKey != null ? apiKey : request.getRemoteAddr();
    }
}
