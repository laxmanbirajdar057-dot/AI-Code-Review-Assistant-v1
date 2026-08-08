package com.laxman.codereviewassistant.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laxman.codereviewassistant.entity.EventStatus;
import com.laxman.codereviewassistant.entity.Repository;
import com.laxman.codereviewassistant.entity.WebhookEvent;
import com.laxman.codereviewassistant.exception.InvalidWebhookSignatureException;
import com.laxman.codereviewassistant.repository.RepositoryRepository;
import com.laxman.codereviewassistant.repository.WebhookEventRepository;
import com.laxman.codereviewassistant.security.EncryptionService;
import com.laxman.codereviewassistant.security.WebhookSignatureValidator;
import com.laxman.codereviewassistant.service.ReviewWorkerService;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final RepositoryRepository repositoryRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookSignatureValidator signatureValidator;
    private final ReviewWorkerService reviewWorkerService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(RepositoryRepository repositoryRepository,
                              WebhookEventRepository webhookEventRepository,
                              WebhookSignatureValidator signatureValidator,
                              ReviewWorkerService reviewWorkerService,
                              EncryptionService encryptionService) {
        this.repositoryRepository = repositoryRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.signatureValidator = signatureValidator;
        this.reviewWorkerService = reviewWorkerService;
        this.encryptionService = encryptionService;
    }

    @PostMapping("/github")
    public ResponseEntity<String> receiveGithubWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Hub-Signature-256") String signatureHeader,
            @RequestHeader("X-GitHub-Delivery") String deliveryId) throws Exception {

        // Step 1: figure out which repo this event is for
        JsonNode root = objectMapper.readTree(payload);
        String repoUrl = root.path("repository").path("html_url").asText();

        Repository repo = repositoryRepository.findByRepoUrl(repoUrl)
                .orElseThrow(() -> new InvalidWebhookSignatureException("Unknown repository"));

        // Step 2: verify the signature using THIS repo's secret
        // (webhookSecret is stored encrypted at rest — decrypt it here, in memory,
        // just for the HMAC comparison; it's never persisted or logged in plaintext)
        String decryptedSecret = encryptionService.decrypt(repo.getWebhookSecret());
        boolean valid = signatureValidator.isValid(payload, decryptedSecret, signatureHeader);
        if (!valid) {
            throw new InvalidWebhookSignatureException("Invalid webhook signature");
        }

        // Step 3: idempotency check — has this exact delivery already been processed?
        Optional<WebhookEvent> existing = webhookEventRepository.findByDeliveryId(deliveryId);
        if (existing.isPresent()) {
            return ResponseEntity.ok("Already processed");
        }

        // Step 4: save the raw event fast, mark PENDING
        WebhookEvent event = new WebhookEvent();
        event.setRepository(repo);
        event.setDeliveryId(deliveryId);
        event.setRawPayload(payload);
        event.setStatus(EventStatus.PENDING);
        event.setReceivedAt(LocalDateTime.now());

        webhookEventRepository.save(event);

        // Step 5: hand off to the async worker — this is the missing piece
        reviewWorkerService.processEvent(event.getId());

        // Step 6: return immediately — GitHub gets a fast response regardless of LLM processing time
        return ResponseEntity.ok("Received");
    }
}