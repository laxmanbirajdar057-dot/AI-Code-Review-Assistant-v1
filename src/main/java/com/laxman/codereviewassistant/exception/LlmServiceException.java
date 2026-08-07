package com.laxman.codereviewassistant.exception;

/**
 * Thrown when the call to the LLM provider (Gemini) fails or returns
 * something we can't parse — bad/missing API key, network error, rate
 * limit, unexpected response shape, etc. Kept distinct from a generic
 * 500 so the API and the UI can tell the user "the AI service failed"
 * instead of a blanket "something went wrong".
 */
public class LlmServiceException extends RuntimeException {

    public LlmServiceException(String message) {
        super(message);
    }

    public LlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
