package com.laxman.codereviewassistant.scoring;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.entity.IssueCategory;
import com.laxman.codereviewassistant.entity.RiskLevel;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;

/**
 * Turns a review's flat list of issues into a single overallScore (0-100)
 * and a RiskLevel, using the weighted category breakdown from the project
 * spec:
 *
 *   Security          30%
 *   Code Quality      20%
 *   Maintainability   20%
 *   Performance       15%
 *   Reliability       15%
 *
 * How it works: each category starts at a perfect 100. Every issue in that
 * category subtracts a penalty based on its severity (CRITICAL hurts a lot,
 * INFO doesn't hurt at all). The five category scores are then combined
 * using the weights above to produce the overall score.
 */
@Service
public class ScoringService {

    private static final Map<IssueCategory, Double> WEIGHTS = Map.of(
            IssueCategory.SECURITY, 0.30,
            IssueCategory.QUALITY, 0.20,
            IssueCategory.MAINTAINABILITY, 0.20,
            IssueCategory.PERFORMANCE, 0.15,
            IssueCategory.RELIABILITY, 0.15);

    private static final Map<Severity, Integer> SEVERITY_PENALTY = Map.of(
            Severity.CRITICAL, 40,
            Severity.HIGH, 20,
            Severity.MEDIUM, 10,
            Severity.LOW, 5,
            Severity.INFO, 0);

    private static final int RISK_LOW_THRESHOLD = 80;
    private static final int RISK_MEDIUM_THRESHOLD = 60;

    public ScoreResult calculateScore(List<ReviewComment> comments) {
        Map<IssueCategory, Integer> categoryScores = new EnumMap<>(IssueCategory.class);
        for (IssueCategory category : IssueCategory.values()) {
            categoryScores.put(category, 100);
        }

        for (ReviewComment comment : comments) {
            IssueCategory category = comment.getCategory();
            if (category == null) {
                continue; // defensive — every comment we create always sets one
            }
            int penalty = SEVERITY_PENALTY.getOrDefault(comment.getSeverity(), 0);
            categoryScores.merge(category, -penalty, Integer::sum);
        }

        // Clamp each category to [0, 100] — penalties can stack past zero on a
        // genuinely bad diff, but a score can't go negative.
        categoryScores.replaceAll((category, score) -> Math.max(0, Math.min(100, score)));

        double weighted = 0;
        for (Map.Entry<IssueCategory, Double> entry : WEIGHTS.entrySet()) {
            weighted += categoryScores.get(entry.getKey()) * entry.getValue();
        }
        int overallScore = (int) Math.round(weighted);

        boolean hasCriticalIssue = comments.stream().anyMatch(c -> c.getSeverity() == Severity.CRITICAL);
        RiskLevel riskLevel = determineRiskLevel(overallScore, hasCriticalIssue);

        return new ScoreResult(overallScore, riskLevel, categoryScores);
    }

    private RiskLevel determineRiskLevel(int overallScore, boolean hasCriticalIssue) {
        // A single CRITICAL finding (e.g. a hardcoded secret) makes the change
        // HIGH risk regardless of how the weighted average nets out — you don't
        // want one great score in another category masking a leaked credential.
        if (hasCriticalIssue) {
            return RiskLevel.HIGH;
        }
        if (overallScore >= RISK_LOW_THRESHOLD) {
            return RiskLevel.LOW;
        }
        if (overallScore >= RISK_MEDIUM_THRESHOLD) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }
}
