package com.laxman.codereviewassistant.exception;

/**
 * NotAuthorizedException
 */
public class NotAuthorizedException extends RuntimeException {

    public NotAuthorizedException() {
        super("Not authorized");
    }

    public NotAuthorizedException(String message) {
        super(message);
    }

    public NotAuthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

}
