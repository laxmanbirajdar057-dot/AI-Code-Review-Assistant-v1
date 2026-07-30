package com.laxman.codereviewassistant.service;

import com.laxman.codereviewassistant.dto.RegisterRepoRequest;
import com.laxman.codereviewassistant.dto.RepoResponse;
import com.laxman.codereviewassistant.entity.Repository;
import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.exception.RepoAlreadyExistsException;
import com.laxman.codereviewassistant.repository.RepositoryRepository;
import com.laxman.codereviewassistant.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    public RepositoryService(RepositoryRepository repositoryRepository,
                              UserRepository userRepository) {
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
    }

    public RepoResponse registerRepo(RegisterRepoRequest request) {
        User owner = getCurrentUser();

        if (repositoryRepository.findByRepoUrl(request.getRepoUrl()).isPresent()) {
            throw new RepoAlreadyExistsException("Repository already registered");
        }

        Repository repo = new Repository();
        repo.setRepoUrl(request.getRepoUrl());
        repo.setWebhookSecret(request.getWebhookSecret()); // TODO: encrypt before storing
        repo.setOwner(owner);

        Repository saved = repositoryRepository.save(repo);

        return new RepoResponse(saved.getId(), saved.getRepoUrl(), buildWebhookUrl());
    }

    public List<RepoResponse> getMyRepos() {
        User owner = getCurrentUser();

        return repositoryRepository.findByOwner(owner).stream()
                .map(r -> new RepoResponse(r.getId(), r.getRepoUrl(), buildWebhookUrl()))
                .collect(Collectors.toList());
    }

    public void deleteRepo(Long id) {
        User owner = getCurrentUser();

        Repository repo = repositoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        if (!repo.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Not authorized to delete this repository");
        }

        repositoryRepository.deleteById(id);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private String buildWebhookUrl() {
        return "https://yourapp.com/webhooks/github";
    }
}