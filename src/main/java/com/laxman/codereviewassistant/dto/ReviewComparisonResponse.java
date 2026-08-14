package com.laxman.codereviewassistant.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewComparisonResponse {
    private Integer fromPrNumber;
    private Integer toPrNumber;
    private Integer fromScore;
    private Integer toScore;
    private Integer scoreDelta; // toScore - fromScore; positive = improvement
    private long fromIssueCount;
    private long toIssueCount;
    private Map<String, Integer> fromCategoryScores;
    private Map<String, Integer> toCategoryScores;
}
