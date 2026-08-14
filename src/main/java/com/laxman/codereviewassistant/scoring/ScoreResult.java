package com.laxman.codereviewassistant.scoring;

import java.util.Map;

import com.laxman.codereviewassistant.entity.IssueCategory;
import com.laxman.codereviewassistant.entity.RiskLevel;

public record ScoreResult(
        int overallScore,
        RiskLevel riskLevel,
        Map<IssueCategory, Integer> categoryScores) {
}
