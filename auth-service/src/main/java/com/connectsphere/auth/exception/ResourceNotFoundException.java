package com.connectsphere.auth.exception;

/** Thrown when a requested user is not found in the database. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
