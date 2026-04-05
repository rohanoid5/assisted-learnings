package com.taskforge.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(resource + " not found with " + field + ": " + value);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
