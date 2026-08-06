package com.laxman.codereviewassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SnippetResponse {
    private String mode;
    private String result; // markdown-formatted text from the LLM
}
