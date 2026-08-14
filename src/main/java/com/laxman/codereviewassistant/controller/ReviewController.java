package com.laxman.codereviewassistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.laxman.codereviewassistant.dto.ResolveCommentRequest;
import com.laxman.codereviewassistant.dto.ReviewComparisonResponse;
import com.laxman.codereviewassistant.dto.ReviewResponse;
import com.laxman.codereviewassistant.service.ReviewAnalyticsService;
import com.laxman.codereviewassistant.service.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewAnalyticsService reviewAnalyticsService;

    public ReviewController(ReviewService reviewService, ReviewAnalyticsService reviewAnalyticsService) {
        this.reviewService = reviewService;
        this.reviewAnalyticsService = reviewAnalyticsService;
    }

    @GetMapping("/{repoId}/{prNumber}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable Long repoId,
                                                     @PathVariable Integer prNumber) {
        return ResponseEntity.ok(reviewService.getReview(repoId, prNumber));
    }

    // New: compare two PRs' latest reviews within the same repo, e.g.
    // GET /reviews/compare?repoId=3&from=21&to=22
    @GetMapping("/compare")
    public ResponseEntity<ReviewComparisonResponse> compare(@RequestParam Long repoId,
                                                              @RequestParam Integer from,
                                                              @RequestParam Integer to) {
        return ResponseEntity.ok(reviewAnalyticsService.compare(repoId, from, to));
    }

    @PatchMapping("/comments/{id}")
    public ResponseEntity<Void> resolveComment(@PathVariable Long id,
                                                @Valid @RequestBody ResolveCommentRequest request) {
        reviewService.resolveComment(id, request.isResolved());
        return ResponseEntity.noContent().build();
    }
}