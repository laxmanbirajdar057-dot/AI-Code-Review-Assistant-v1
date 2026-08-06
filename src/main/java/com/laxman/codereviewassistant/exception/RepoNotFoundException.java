package com.laxman.codereviewassistant.exception;

public class RepoNotFoundException extends RuntimeException {

    public RepoNotFoundException() {
        super("Repository not found");
    }

    public RepoNotFoundException(String message) {
        super(message);
    }

    public RepoNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
