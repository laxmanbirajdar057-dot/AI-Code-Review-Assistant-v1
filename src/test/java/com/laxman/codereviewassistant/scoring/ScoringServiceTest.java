package com.laxman.codereviewassistant.scoring;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.laxman.codereviewassistant.entity.CommentSource;
import com.laxman.codereviewassistant.entity.IssueCategory;
import com.laxman.codereviewassistant.entity.RiskLevel;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringServiceTest {

    private final ScoringService scoringService = new ScoringService();

    @Test
    void noIssuesProducesAPerfectScoreAndLowRisk() {
        ScoreResult result = scoringService.calculateScore(List.of());

        assertEquals(100, result.overallScore());
        assertEquals(RiskLevel.LOW, result.riskLevel());
    }

    @Test
    void singleCriticalIssueForcesHighRiskRegardlessOfOverallScore() {
        // One CRITICAL SECURITY issue: security category drops 100 -> 60,
        // weighted overall score is still fairly high (security is only 30%
        // of the total), but a leaked secret should never be masked as
        // low/medium risk by a decent weighted average.
        ReviewComment critical = comment(Severity.CRITICAL, IssueCategory.SECURITY);

        ScoreResult result = scoringService.calculateScore(List.of(critical));

        assertEquals(RiskLevel.HIGH, result.riskLevel());
    }

    @Test
    void issuesOnlyAffectTheirOwnCategoryScore() {
        ReviewComment qualityIssue = comment(Severity.LOW, IssueCategory.QUALITY);

        ScoreResult result = scoringService.calculateScore(List.of(qualityIssue));

        assertEquals(95, result.categoryScores().get(IssueCategory.QUALITY)); // 100 - 5
        assertEquals(100, result.categoryScores().get(IssueCategory.SECURITY)); // untouched
    }

    @Test
    void categoryScoreNeverGoesBelowZero() {
        // Five CRITICAL security issues would be 100 - (5*40) = -100 on paper
        List<ReviewComment> comments = List.of(
                comment(Severity.CRITICAL, IssueCategory.SECURITY),
                comment(Severity.CRITICAL, IssueCategory.SECURITY),
                comment(Severity.CRITICAL, IssueCategory.SECURITY),
                comment(Severity.CRITICAL, IssueCategory.SECURITY),
                comment(Severity.CRITICAL, IssueCategory.SECURITY));

        ScoreResult result = scoringService.calculateScore(comments);

        assertEquals(0, result.categoryScores().get(IssueCategory.SECURITY));
    }

    @Test
    void mediumOverallScoreWithoutCriticalIssuesIsMediumRisk() {
        // 4 HIGH security issues: security category = 100 - (4*20) = 20.
        // Weighted: 20*0.30 + 100*0.20 + 100*0.20 + 100*0.15 + 100*0.15 = 76
        // -> lands in the 60-79 MEDIUM band, with no CRITICAL override triggered.
        List<ReviewComment> comments = List.of(
                comment(Severity.HIGH, IssueCategory.SECURITY),
                comment(Severity.HIGH, IssueCategory.SECURITY),
                comment(Severity.HIGH, IssueCategory.SECURITY),
                comment(Severity.HIGH, IssueCategory.SECURITY));

        ScoreResult result = scoringService.calculateScore(comments);

        assertEquals(76, result.overallScore());
        assertEquals(RiskLevel.MEDIUM, result.riskLevel());
    }

    private ReviewComment comment(Severity severity, IssueCategory category) {
        ReviewComment comment = new ReviewComment();
        comment.setSeverity(severity);
        comment.setCategory(category);
        comment.setSource(CommentSource.STATIC);
        comment.setMessage("test issue");
        return comment;
    }
}
