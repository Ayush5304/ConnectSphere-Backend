package com.connectsphere.like.exception;

/** Thrown when the request contains invalid or missing data. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
