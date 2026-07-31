package com.laxman.codereviewassistant.mapper;

import java.util.List;
import java.util.stream.Collectors;

import com.laxman.codereviewassistant.dto.ReviewCommentResponse;
import com.laxman.codereviewassistant.dto.ReviewResponse;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;

public class ReviewMapper {

    public static ReviewResponse toResponse(Review review, List<ReviewComment> comments) {
        ReviewResponse response = new ReviewResponse();
        response.setReviewId(review.getId());
        response.setStatus(review.getStatus().name());
        response.setComments(
            comments.stream()
                .map(ReviewMapper::toCommentResponse)
                .collect(Collectors.toList())
        );
        return response;
    }

    private static ReviewCommentResponse toCommentResponse(ReviewComment comment) {
        ReviewCommentResponse dto = new ReviewCommentResponse();
        dto.setFile(comment.getFileName());
        dto.setLine(comment.getLineNumber());
        dto.setSeverity(comment.getSeverity().name());
        dto.setMessage(comment.getMessage());
        dto.setResolved(comment.isResolved());
        dto.setId(comment.getId());
        return dto;
    }
}