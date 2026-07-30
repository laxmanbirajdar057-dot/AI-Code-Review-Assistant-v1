package com.laxman.codereviewassistant.exception;

public class RepoAlreadyExistsException extends RuntimeException {
    public RepoAlreadyExistsException(String message) {
        super(message);
    }
}