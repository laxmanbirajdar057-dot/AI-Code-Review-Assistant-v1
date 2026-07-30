package com.laxman.codereviewassistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laxman.codereviewassistant.dto.ResolveCommentRequest;
import com.laxman.codereviewassistant.dto.ReviewResponse;
import com.laxman.codereviewassistant.service.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{repoId}/{prNumber}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable Long repoId,
                                                     @PathVariable Integer prNumber) {
        return ResponseEntity.ok(reviewService.getReview(repoId, prNumber));
    }

    @PatchMapping("/comments/{id}")
    public ResponseEntity<Void> resolveComment(@PathVariable Long id,
                                                @Valid @RequestBody ResolveCommentRequest request) {
        reviewService.resolveComment(id, request.isResolved());
        return ResponseEntity.noContent().build();
    }
}