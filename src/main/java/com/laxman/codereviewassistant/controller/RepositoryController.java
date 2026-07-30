package com.laxman.codereviewassistant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laxman.codereviewassistant.dto.RegisterRepoRequest;
import com.laxman.codereviewassistant.dto.RepoResponse;
import com.laxman.codereviewassistant.service.RepositoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/repos")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping
    public ResponseEntity<RepoResponse> registerRepo(@Valid @RequestBody RegisterRepoRequest request) {
        RepoResponse response = repositoryService.registerRepo(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RepoResponse>> getMyRepos() {
        return ResponseEntity.ok(repositoryService.getMyRepos());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepo(@PathVariable Long id) {
        repositoryService.deleteRepo(id);
        return ResponseEntity.noContent().build();
    }
}