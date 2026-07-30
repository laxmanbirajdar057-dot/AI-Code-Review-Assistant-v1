package com.laxman.codereviewassistant.exception;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private int status;
    private List<String> details; // used for field-validation errors; null otherwise
}