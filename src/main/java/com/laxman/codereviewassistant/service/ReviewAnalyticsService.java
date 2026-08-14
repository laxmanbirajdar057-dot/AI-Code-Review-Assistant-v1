package com.laxman.codereviewassistant.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.dto.RepoStatisticsResponse;
import com.laxman.codereviewassistant.dto.ReviewComparisonResponse;
import com.laxman.codereviewassistant.dto.ReviewSummaryResponse;
import com.laxman.codereviewassistant.entity.Repository;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;
import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.exception.InvalidCredentialsException;
import com.laxman.codereviewassistant.exception.NotAuthorizedException;
import com.laxman.codereviewassistant.exception.RepoNotFoundException;
import com.laxman.codereviewassistant.exception.ReviewNotFoundException;
import com.laxman.codereviewassistant.repository.RepositoryRepository;
import com.laxman.codereviewassistant.repository.ReviewCommentRepository;
import com.laxman.codereviewassistant.repository.ReviewRepository;
import com.laxman.codereviewassistant.repository.UserRepository;
import com.laxman.codereviewassistant.scoring.ScoreResult;
import com.laxman.codereviewassistant.scoring.ScoringService;

/**
 * Fix: previously the only way to look at a review was one-at-a-time via
 * GET /reviews/{repoId}/{prNumber} — there was no way to see a repo's
 * history, aggregate stats, or how two PRs compare. This is the "more than
 * CRUD" half of the spec: trend/aggregate queries, not just row lookups.
 */
@Service
public class ReviewAnalyticsService {

    private final RepositoryRepository repositoryRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;

    public ReviewAnalyticsService(RepositoryRepository repositoryRepository,
            ReviewRepository reviewRepository,
            ReviewCommentRepository reviewCommentRepository,
            UserRepository userRepository,
            ScoringService scoringService) {
        this.repositoryRepository = repositoryRepository;
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.userRepository = userRepository;
        this.scoringService = scoringService;
    }

    public List<ReviewSummaryResponse> getHistory(Long repoId) {
        assertOwnerOrAdmin(getRepoOrThrow(repoId), getCurrentUser());

        return reviewRepository.findByPullRequestRepositoryIdOrderByCreatedAtDesc(repoId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public RepoStatisticsResponse getStatistics(Long repoId) {
        assertOwnerOrAdmin(getRepoOrThrow(repoId), getCurrentUser());

        List<Review> reviews = reviewRepository.findByPullRequestRepositoryIdOrderByCreatedAtDesc(repoId);

        Double averageScore = reviewRepository.findAverageScoreByRepositoryId(repoId);
        long criticalIssueCount = reviewCommentRepository
                .countByReview_PullRequest_Repository_IdAndSeverity(repoId, Severity.CRITICAL);

        List<Object[]> categoryCounts = reviewCommentRepository.countByCategoryForRepository(repoId);
        String mostCommonCategory = categoryCounts.isEmpty() ? null : categoryCounts.get(0)[0].toString();

        List<ReviewSummaryResponse> trend = reviews.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return new RepoStatisticsResponse(reviews.size(), averageScore, criticalIssueCount,
                mostCommonCategory, trend);
    }

    public ReviewComparisonResponse compare(Long repoId, Integer fromPrNumber, Integer toPrNumber) {
        Repository repository = getRepoOrThrow(repoId);
        assertOwnerOrAdmin(repository, getCurrentUser());

        Review fromReview = latestReviewForPr(repoId, fromPrNumber);
        Review toReview = latestReviewForPr(repoId, toPrNumber);

        List<ReviewComment> fromComments = reviewCommentRepository.findByReviewId(fromReview.getId());
        List<ReviewComment> toComments = reviewCommentRepository.findByReviewId(toReview.getId());

        ScoreResult fromScore = scoringService.calculateScore(fromComments);
        ScoreResult toScore = scoringService.calculateScore(toComments);

        return new ReviewComparisonResponse(
                fromPrNumber, toPrNumber,
                fromScore.overallScore(), toScore.overallScore(),
                toScore.overallScore() - fromScore.overallScore(),
                fromComments.size(), toComments.size(),
                toStringKeyedMap(fromScore.categoryScores()),
                toStringKeyedMap(toScore.categoryScores()));
    }

    private Review latestReviewForPr(Long repoId, Integer prNumber) {
        return reviewRepository
                .findFirstByPullRequestRepositoryIdAndPullRequestPrNumberOrderByCreatedAtDesc(repoId, prNumber)
                .orElseThrow(() -> new ReviewNotFoundException("No review found for PR #" + prNumber));
    }

    private Map<String, Integer> toStringKeyedMap(Map<com.laxman.codereviewassistant.entity.IssueCategory, Integer> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
    }

    private ReviewSummaryResponse toSummary(Review review) {
        return new ReviewSummaryResponse(
                review.getPullRequest().getPrNumber(),
                review.getStatus().name(),
                review.getOverallScore(),
                review.getRiskLevel() != null ? review.getRiskLevel().name() : null,
                review.getCreatedAt());
    }

    private Repository getRepoOrThrow(Long repoId) {
        return repositoryRepository.findById(repoId).orElseThrow(RepoNotFoundException::new);
    }

    private void assertOwnerOrAdmin(Repository repository, User currentUser) {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (isAdmin) {
            return;
        }

        if (!repository.getOwner().getId().equals(currentUser.getId())) {
            throw new NotAuthorizedException();
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired session"));
    }
}
