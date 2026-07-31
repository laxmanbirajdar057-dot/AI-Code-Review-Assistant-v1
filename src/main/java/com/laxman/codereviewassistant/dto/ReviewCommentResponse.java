package com.laxman.codereviewassistant.dto;

import lombok.Data;

@Data
public class ReviewCommentResponse {
    private Long id;
    private String file;
    private Integer line;
    private String severity;
    private String message;
    private boolean resolved;
}