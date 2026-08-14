package com.laxman.codereviewassistant.llm;

import lombok.Data;

@Data
public class LlmReviewItem {
    private Integer line;
    private String severity; // "HIGH", "MEDIUM", "LOW"
    private String message;
    public String getCategory() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCategory'");
    }
}