package com.laxman.codereviewassistant.llm;

import java.util.List;

import lombok.Data;

@Data
public class LlmReviewResponse {
    private List<LlmReviewItem> issues;
}