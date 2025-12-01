package com.inovexx.inventory_service.exception;

public class InvalidStockLevelException extends RuntimeException {
  public InvalidStockLevelException(String message) {
    super(message);
  }
}
