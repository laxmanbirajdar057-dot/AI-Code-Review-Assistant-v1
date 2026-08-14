package com.laxman.codereviewassistant.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewSummaryResponse {
    private Integer prNumber;
    private String status;
    private Integer overallScore;
    private String riskLevel;
    private LocalDateTime createdAt;
}
