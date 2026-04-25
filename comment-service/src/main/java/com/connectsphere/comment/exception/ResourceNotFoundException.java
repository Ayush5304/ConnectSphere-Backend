package com.connectsphere.comment.exception;

/** Thrown when a requested comment is not found in the database. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
