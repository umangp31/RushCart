package com.rushcart.inventory;

import com.rushcart.common.ApiException;
import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends ApiException {

    public ProductNotFoundException(String sku) {
        super("Product not found for SKU " + sku);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
