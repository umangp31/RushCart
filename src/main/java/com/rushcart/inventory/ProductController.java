package com.rushcart.inventory;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "products")
public class ProductController {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ReplenishmentService replenishmentService;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;

    public ProductController(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            ReplenishmentService replenishmentService,
            StockService stockService,
            StringRedisTemplate redisTemplate) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.replenishmentService = replenishmentService;
        this.stockService = stockService;
        this.redisTemplate = redisTemplate;
    }

    public record CreateProductRequest(
            @NotBlank String sku, @NotBlank String name, @NotNull BigDecimal price, @Min(0) int initialQty) {}

    public record ReplenishRequest(@Min(1) int qty) {}

    public record ProductResponse(UUID id, String sku, String name, BigDecimal price, long liveStock) {}

    /** Dashboard inventory row (§14.1): Redis hot-path count vs Postgres source of truth. */
    public record InventoryRow(
            UUID id,
            String sku,
            String name,
            BigDecimal price,
            long redisStock,
            int pgAvailableQty,
            int pgReservedQty) {}

    /** Full catalog with live stock for the dashboard inventory view (§14.1). */
    @GetMapping("/api/v1/products")
    public List<InventoryRow> list() {
        return productRepository.findAll().stream()
                .map(product -> {
                    String stock = redisTemplate.opsForValue().get("stock:" + product.getSku());
                    Inventory inv = inventoryRepository.findById(product.getId()).orElse(null);
                    return new InventoryRow(
                            product.getId(),
                            product.getSku(),
                            product.getName(),
                            product.getPrice(),
                            parseStock(stock),
                            inv != null ? inv.getAvailableQty() : 0,
                            inv != null ? inv.getReservedQty() : 0);
                })
                .sorted(Comparator.comparing(InventoryRow::sku))
                .toList();
    }

    @GetMapping("/api/v1/products/{sku}")
    public ProductResponse get(@PathVariable String sku) {
        Product product = productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
        String stock = redisTemplate.opsForValue().get("stock:" + sku);
        return new ProductResponse(
                product.getId(), product.getSku(), product.getName(), product.getPrice(), parseStock(stock));
    }

    @PostMapping("/api/v1/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        Product product = productRepository.save(new Product(request.sku(), request.name(), request.price()));
        inventoryRepository.save(new Inventory(product.getId(), request.initialQty()));
        stockService.seed(request.sku(), request.initialQty());
        return new ProductResponse(
                product.getId(), product.getSku(), product.getName(), product.getPrice(), request.initialQty());
    }

    @PostMapping("/api/v1/products/{sku}/replenish")
    public ProductResponse replenish(@PathVariable String sku, @Valid @RequestBody ReplenishRequest request) {
        replenishmentService.replenish(sku, request.qty());
        return get(sku);
    }

    private static long parseStock(String stock) {
        return stock != null ? Long.parseLong(stock) : 0L;
    }
}
