package com.laxman.codereviewassistant.dto;

import java.util.List;

import lombok.Data;

@Data
public class ReviewResponse {
    private Long reviewId;
    private String status;
    private List<ReviewCommentResponse> comments;
}