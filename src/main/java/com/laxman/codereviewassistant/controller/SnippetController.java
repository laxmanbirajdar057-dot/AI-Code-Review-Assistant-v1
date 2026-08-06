package com.laxman.codereviewassistant.controller;

import com.laxman.codereviewassistant.dto.SnippetRequest;
import com.laxman.codereviewassistant.dto.SnippetResponse;
import com.laxman.codereviewassistant.service.SnippetReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/snippets")
public class SnippetController {

    private final SnippetReviewService snippetReviewService;

    public SnippetController(SnippetReviewService snippetReviewService) {
        this.snippetReviewService = snippetReviewService;
    }

    @PostMapping("/review")
    public ResponseEntity<SnippetResponse> review(@Valid @RequestBody SnippetRequest request) {
        return ResponseEntity.ok(snippetReviewService.process(request));
    }
}
