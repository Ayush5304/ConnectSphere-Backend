package com.connectsphere.like.exception;

/** Thrown when a requested resource (like/reaction) is not found in the database. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
