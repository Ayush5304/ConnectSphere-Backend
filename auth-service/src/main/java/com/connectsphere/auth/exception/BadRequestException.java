package com.connectsphere.auth.exception;

/** Thrown when registration/login data is invalid or duplicate. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
