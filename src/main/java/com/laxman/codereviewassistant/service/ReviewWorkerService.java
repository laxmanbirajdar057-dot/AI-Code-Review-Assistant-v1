package com.laxman.codereviewassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laxman.codereviewassistant.entity.*;
import com.laxman.codereviewassistant.llm.LlmClient;
import com.laxman.codereviewassistant.repository.ReviewCommentRepository;
import com.laxman.codereviewassistant.repository.ReviewRepository;
import com.laxman.codereviewassistant.repository.WebhookEventRepository;
import com.laxman.codereviewassistant.util.DiffChunk;
import com.laxman.codereviewassistant.util.DiffChunker;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewWorkerService {

    private final WebhookEventRepository webhookEventRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final DiffChunker diffChunker;
    private final LlmClient llmClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewWorkerService(WebhookEventRepository webhookEventRepository,
                                ReviewRepository reviewRepository,
                                ReviewCommentRepository reviewCommentRepository,
                                DiffChunker diffChunker,
                                LlmClient llmClient,
                                WebClient.Builder webClientBuilder) {
        this.webhookEventRepository = webhookEventRepository;
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.diffChunker = diffChunker;
        this.llmClient = llmClient;
        this.webClient = webClientBuilder.build();
    }

    @Async
    public void processEvent(Long webhookEventId) {
        WebhookEvent event = webhookEventRepository.findById(webhookEventId)
                .orElseThrow(() -> new RuntimeException("Webhook event not found"));

        Review review = new Review();
        review.setRepository(event.getRepository());

        try {
            JsonNode root = objectMapper.readTree(event.getRawPayload());
            int prNumber = root.path("pull_request").path("number").asInt();
            String diffUrl = root.path("pull_request").path("diff_url").asText();

            review.setPrNumber(prNumber);
            review.setStatus(ReviewStatus.IN_PROGRESS);
            review.setCreatedAt(LocalDateTime.now());
            review = reviewRepository.save(review);

            // Step 1: fetch the raw diff text
            String diffText = webClient.get()
                    .uri(diffUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // blocking call is fine here — we're already inside an @Async method

            // Step 2: split into per-file chunks
            List<DiffChunk> chunks = diffChunker.chunk(diffText);

            // Step 3: send each chunk to the LLM and collect comments
            for (DiffChunk chunk : chunks) {
                List<ReviewComment> comments = llmClient.review(chunk, review);
                reviewCommentRepository.saveAll(comments);
            }

            // Step 4: mark complete
            review.setStatus(ReviewStatus.COMPLETED);
            reviewRepository.save(review);

            event.setStatus(EventStatus.PROCESSED);
            webhookEventRepository.save(event);

        } catch (Exception e) {
            review.setStatus(ReviewStatus.FAILED);
            reviewRepository.save(review);

            event.setStatus(EventStatus.FAILED);
            webhookEventRepository.save(event);
        }
    }
}