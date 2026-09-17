package com.rushcart.config;

import com.rushcart.ratelimit.RateLimiterInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;
    private final String[] corsAllowedOrigins;

    public WebMvcConfig(
            RateLimiterInterceptor rateLimiterInterceptor,
            @Value("${rushcart.cors.allowed-origins:http://localhost:4200}") String[] corsAllowedOrigins) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiterInterceptor).addPathPatterns("/api/v1/**");
    }

    /** Allows the standalone Angular admin dashboard (§14) to call {@code /api/v1} directly, no BFF. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(corsAllowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        registry.addMapping("/actuator/**").allowedOrigins(corsAllowedOrigins).allowedMethods("GET");
    }
}
