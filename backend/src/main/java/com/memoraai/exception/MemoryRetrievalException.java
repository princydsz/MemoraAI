package com.memoraai.exception;

public class MemoryRetrievalException extends RuntimeException {

    public MemoryRetrievalException(String message) {
        super(message);
    }

    public MemoryRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
