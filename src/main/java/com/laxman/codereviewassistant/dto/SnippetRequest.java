package com.laxman.codereviewassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SnippetRequest {

    @NotBlank(message = "Paste some code first")
    private String code;

    private String language; // optional, e.g. "java" — helps the model, not required

    @NotBlank(message = "mode is required")
    private String mode; // REVIEW | EXPLAIN | FORMAT
}
