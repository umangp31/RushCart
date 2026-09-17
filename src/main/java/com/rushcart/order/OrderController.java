package com.rushcart.order;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    public record ReserveOrderRequest(@NotNull UUID customerId, @NotBlank String sku, @Min(1) int qty) {}

    public record OrderResponse(
            UUID id, UUID customerId, UUID productId, int qty, OrderStatus status, Instant reservationExpiresAt) {

        static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId(),
                    order.getCustomerId(),
                    order.getProductId(),
                    order.getQty(),
                    order.getStatus(),
                    order.getReservationExpiresAt());
        }
    }

    @PostMapping("/api/v1/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse reserve(@Valid @RequestBody ReserveOrderRequest request) {
        Order order = orderService.reserve(request.customerId(), request.sku(), request.qty());
        return OrderResponse.from(order);
    }

    @GetMapping("/api/v1/orders/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.get(id));
    }

    public record OrderEventResponse(UUID id, String eventType, String payload, Instant createdAt) {
        static OrderEventResponse from(OrderEvent e) {
            return new OrderEventResponse(e.getId(), e.getEventType(), e.getPayload(), e.getCreatedAt());
        }
    }

    /** Dashboard order list (§14.1). Newest first; optional status/customer filter. */
    @GetMapping("/api/v1/orders")
    public List<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "100") int limit) {
        return orderService.list(status, customerId, limit).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** Full state-transition timeline for one order (§6.1, §14.1). */
    @GetMapping("/api/v1/orders/{id}/events")
    public List<OrderEventResponse> events(@PathVariable UUID id) {
        return orderService.events(id).stream().map(OrderEventResponse::from).toList();
    }

    @PostMapping("/api/v1/orders/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancel(id));
    }

    @PostMapping("/api/v1/orders/{id}/pay")
    public OrderResponse pay(@PathVariable UUID id) {
        return OrderResponse.from(orderService.markPaid(id));
    }
}
