package com.rushcart.inventory;

public record StockReservationResult(boolean reserved, int remainingStock) {

    public static StockReservationResult reserved(int remainingStock) {
        return new StockReservationResult(true, remainingStock);
    }

    public static StockReservationResult insufficientStock() {
        return new StockReservationResult(false, -1);
    }
}
