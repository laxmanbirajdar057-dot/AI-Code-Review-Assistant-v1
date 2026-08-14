package com.laxman.codereviewassistant.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RepoStatisticsResponse {
    private long totalReviews;
    private Double averageScore; // null if no completed reviews yet
    private long criticalIssueCount;
    private String mostCommonIssueCategory; // null if no issues found yet
    private List<ReviewSummaryResponse> scoreTrend; // newest first
}
