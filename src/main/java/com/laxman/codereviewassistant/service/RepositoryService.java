package com.laxman.codereviewassistant.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.dto.RegisterRepoRequest;
import com.laxman.codereviewassistant.dto.RepoResponse;
import com.laxman.codereviewassistant.entity.Repository;
import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.exception.InvalidCredentialsException;
import com.laxman.codereviewassistant.exception.NotAuthorizedException;
import com.laxman.codereviewassistant.exception.RepoAlreadyExistsException;
import com.laxman.codereviewassistant.exception.RepoNotFoundException;
import com.laxman.codereviewassistant.repository.RepositoryRepository;
import com.laxman.codereviewassistant.repository.UserRepository;
import com.laxman.codereviewassistant.security.EncryptionService;

@Service
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final String appBaseUrl;

    public RepositoryService(RepositoryRepository repositoryRepository,
            UserRepository userRepository,
            EncryptionService encryptionService,
            @Value("${app.base-url}") String appBaseUrl) {
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
        this.appBaseUrl = appBaseUrl;
    }

    public RepoResponse registerRepo(RegisterRepoRequest request) {
        User owner = getCurrentUser();

        if (repositoryRepository.findByRepoUrl(request.getRepoUrl()).isPresent()) {
            throw new RepoAlreadyExistsException("Repository already registered");
        }

        Repository repo = new Repository();
        repo.setRepoUrl(request.getRepoUrl());
        // Fix: was stored in plaintext despite the field comment claiming otherwise.
        // Encrypted at rest with AES-256-GCM; decrypted only when needed to verify
        // an incoming webhook signature (see WebhookController).
        repo.setWebhookSecret(encryptionService.encrypt(request.getWebhookSecret()));
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
        User currentUser = getCurrentUser();

        Repository repo = repositoryRepository.findById(id)
                .orElseThrow(RepoNotFoundException::new);

        if (!isAdmin() && !repo.getOwner().getId().equals(currentUser.getId())) {
            throw new NotAuthorizedException("Not authorized to delete this repository");
        }

        repositoryRepository.deleteById(id);
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired session"));
    }

    // Fix: was hardcoded to "https://yourapp.com/webhooks/github" — every user
    // was shown the same fake URL regardless of where the app was actually
    // deployed. Now sourced from app.base-url (APP_BASE_URL env var).
    private String buildWebhookUrl() {
        return appBaseUrl + "/webhooks/github";
    }
}