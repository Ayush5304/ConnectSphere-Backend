package com.connectsphere.post.exception;

/** Thrown when a requested post is not found in the database. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
