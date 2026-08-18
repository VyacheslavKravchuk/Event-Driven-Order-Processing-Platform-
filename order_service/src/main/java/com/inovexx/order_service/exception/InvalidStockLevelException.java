package com.inovexx.order_service.exception;

public class InvalidStockLevelException extends RuntimeException {
  public InvalidStockLevelException(String message) {
    super(message);
  }
}
