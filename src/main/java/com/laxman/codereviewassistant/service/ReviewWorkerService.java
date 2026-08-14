package com.laxman.codereviewassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laxman.codereviewassistant.analysis.ReviewAggregatorService;
import com.laxman.codereviewassistant.analysis.StaticAnalysisEngine;
import com.laxman.codereviewassistant.entity.*;
import com.laxman.codereviewassistant.llm.LlmClient;
import com.laxman.codereviewassistant.repository.PullRequestRepository;
import com.laxman.codereviewassistant.repository.ReviewCommentRepository;
import com.laxman.codereviewassistant.repository.ReviewRepository;
import com.laxman.codereviewassistant.repository.WebhookEventRepository;
import com.laxman.codereviewassistant.scoring.ScoreResult;
import com.laxman.codereviewassistant.scoring.ScoringService;
import com.laxman.codereviewassistant.security.EncryptionService;
import com.laxman.codereviewassistant.util.DiffChunk;
import com.laxman.codereviewassistant.util.DiffChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ReviewWorkerService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final PullRequestRepository pullRequestRepository;
    private final DiffChunker diffChunker;
    private final LlmClient llmClient;
    private final StaticAnalysisEngine staticAnalysisEngine;
    private final ReviewAggregatorService reviewAggregatorService;
    private final ScoringService scoringService;
    private final EncryptionService encryptionService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewWorkerService(WebhookEventRepository webhookEventRepository,
                                ReviewRepository reviewRepository,
                                ReviewCommentRepository reviewCommentRepository,
                                PullRequestRepository pullRequestRepository,
                                DiffChunker diffChunker,
                                LlmClient llmClient,
                                StaticAnalysisEngine staticAnalysisEngine,
                                ReviewAggregatorService reviewAggregatorService,
                                ScoringService scoringService,
                                EncryptionService encryptionService,
                                WebClient.Builder webClientBuilder) {
        this.webhookEventRepository = webhookEventRepository;
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.diffChunker = diffChunker;
        this.llmClient = llmClient;
        this.staticAnalysisEngine = staticAnalysisEngine;
        this.reviewAggregatorService = reviewAggregatorService;
        this.scoringService = scoringService;
        this.encryptionService = encryptionService;
        this.webClient = webClientBuilder.build();
    }

    @Async
    public void processEvent(Long webhookEventId) {
        WebhookEvent event = webhookEventRepository.findById(webhookEventId)
                .orElseThrow(() -> new RuntimeException("Webhook event not found"));

        Repository repository = event.getRepository();
        Review review = new Review();

        try {
            JsonNode root = objectMapper.readTree(event.getRawPayload());
            int prNumber = root.path("pull_request").path("number").asInt();
            String prTitle = root.path("pull_request").path("title").asText(null);
            String diffUrl = root.path("pull_request").path("diff_url").asText();

            // Fix: Review previously carried its own repository+prNumber fields
            // with no PullRequest entity behind them. Find-or-create the
            // PullRequest so multiple reviews of the same PR (e.g. one per
            // "synchronize" event as new commits land) are grouped together —
            // this is what review history/comparison/trend queries need.
            PullRequest pullRequest = pullRequestRepository
                    .findByRepositoryIdAndPrNumber(repository.getId(), prNumber)
                    .orElseGet(() -> {
                        PullRequest pr = new PullRequest();
                        pr.setRepository(repository);
                        pr.setPrNumber(prNumber);
                        pr.setCreatedAt(LocalDateTime.now());
                        return pr;
                    });
            pullRequest.setTitle(prTitle);
            pullRequest.setUpdatedAt(LocalDateTime.now());
            pullRequest = pullRequestRepository.save(pullRequest);

            review.setPullRequest(pullRequest);
            review.setStatus(ReviewStatus.IN_PROGRESS);
            review.setCreatedAt(LocalDateTime.now());
            review = reviewRepository.save(review);

            // Step 1: fetch the raw diff text.
            // Fix: previously always fetched anonymously, so private repos
            // silently failed (GitHub 404s diff_url without auth for them).
            // If the repo has a GitHub token on file, decrypt it and send it —
            // public repos work exactly as before with no token needed.
            WebClient.RequestHeadersSpec<?> request = webClient.get().uri(diffUrl);
            if (repository.getGithubToken() != null) {
                String token = encryptionService.decrypt(repository.getGithubToken());
                request = request.header("Authorization", "token " + token);
            }
            String diffText = request
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // blocking call is fine here — we're already inside an @Async method

            // Step 2: split into per-file chunks
            List<DiffChunk> chunks = diffChunker.chunk(diffText);

            // Step 3: run BOTH analysis layers per chunk and combine them.
            // Fix: previously this was 100% LLM-dependent with no deterministic
            // layer at all — same diff could get different findings between
            // runs, and the review had no baseline that didn't depend on an
            // external AI service being up.
            List<ReviewComment> allComments = new ArrayList<>();
            for (DiffChunk chunk : chunks) {
                List<ReviewComment> staticComments = staticAnalysisEngine.analyze(chunk, review);
                List<ReviewComment> aiComments = llmClient.review(chunk, review);
                List<ReviewComment> combined = reviewAggregatorService.combine(staticComments, aiComments);
                reviewCommentRepository.saveAll(combined);
                allComments.addAll(combined);
            }

            // Step 4: score the review from everything we just found, then mark complete
            ScoreResult score = scoringService.calculateScore(allComments);
            review.setOverallScore(score.overallScore());
            review.setRiskLevel(score.riskLevel());
            review.setStatus(ReviewStatus.COMPLETED);
            reviewRepository.save(review);

            event.setStatus(EventStatus.PROCESSED);
            webhookEventRepository.save(event);

        } catch (Exception e) {
            log.error("Review processing failed for webhook event {}: {}", webhookEventId, e.getMessage(), e);

            // review.pullRequest is NOT NULL — if we failed before that was ever
            // set (e.g. malformed webhook payload), there's no valid row to save;
            // only try to persist a FAILED status if the review actually made it
            // past that point.
            if (review.getPullRequest() != null) {
                review.setStatus(ReviewStatus.FAILED);
                reviewRepository.save(review);
            }

            event.setStatus(EventStatus.FAILED);
            webhookEventRepository.save(event);
        }
    }
}
