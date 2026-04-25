package com.connectsphere.comment.exception;

/** Thrown when the comment request contains invalid or missing data. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
