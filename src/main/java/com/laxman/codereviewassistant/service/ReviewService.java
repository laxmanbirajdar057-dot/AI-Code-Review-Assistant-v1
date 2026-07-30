package com.laxman.codereviewassistant.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.dto.ReviewResponse;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.exception.ReviewNotFoundException;
import com.laxman.codereviewassistant.mapper.ReviewMapper;
import com.laxman.codereviewassistant.repository.ReviewCommentRepository;
import com.laxman.codereviewassistant.repository.ReviewRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;

    public ReviewService(ReviewRepository reviewRepository,
                          ReviewCommentRepository reviewCommentRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
    }

    public ReviewResponse getReview(Long repoId, Integer prNumber) {
        Review review = reviewRepository.findByRepositoryIdAndPrNumber(repoId, prNumber)
                .orElseThrow(() -> new ReviewNotFoundException("No review found for this repo/PR"));

        List<ReviewComment> comments = reviewCommentRepository.findByReviewId(review.getId());

        return ReviewMapper.toResponse(review, comments);
    }

    public void resolveComment(Long commentId, boolean resolved) {
        ReviewComment comment = reviewCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        comment.setResolved(resolved);
        reviewCommentRepository.save(comment);
    }
}