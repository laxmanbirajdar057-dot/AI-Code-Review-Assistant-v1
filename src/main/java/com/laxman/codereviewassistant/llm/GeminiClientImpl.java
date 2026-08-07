package com.laxman.codereviewassistant.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;
import com.laxman.codereviewassistant.exception.LlmServiceException;
import com.laxman.codereviewassistant.util.DiffChunk;

@Component
public class GeminiClientImpl implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClientImpl.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    // Use the rolling alias rather than a dated model id — Google frequently
    // retires specific Gemini snapshots (e.g. gemini-2.5-flash is slated for
    // shutdown), and "gemini-flash-latest" always points at Google's current
    // stable Flash model without needing a code change on every cutover.
    private static final String MODEL = "gemini-flash-latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String PROMPT_TEMPLATE = """
            You are a senior code reviewer. Review the following code diff for bugs,
            security issues, and style problems.

            Respond ONLY with valid JSON in this exact shape, nothing else,
            no markdown fences, no explanation text:
            {
              "issues": [
                { "line": <int>, "severity": "HIGH|MEDIUM|LOW", "message": "<string>" }
              ]
            }

            If there are no issues, return {"issues": []}.

            File: %s
            Diff:
            %s
            """;

    public GeminiClientImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    @Override
    public List<ReviewComment> review(DiffChunk chunk, Review review) {
        String prompt = String.format(PROMPT_TEMPLATE, chunk.getFileName(), chunk.getContent());

        try {
            String rawResponse = callGemini(prompt, 0);
            String content = extractMessageContent(rawResponse);
            LlmReviewResponse parsed = objectMapper.readValue(content, LlmReviewResponse.class);
            return mapToComments(parsed, chunk, review);
        } catch (Exception e) {
            // Fail this chunk without failing the whole review — return no comments for it.
            // Still logged, so a silently-empty review is diagnosable.
            log.error("Gemini review call failed for file '{}': {}", chunk.getFileName(), e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public String complete(String prompt) {
        String rawResponse = callGemini(prompt, 0.2);
        try {
            return extractMessageContent(rawResponse);
        } catch (LlmServiceException e) {
            // Already a clear, specific message (e.g. blocked by safety filters) — don't
            // re-wrap it.
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response. Raw response: {}", rawResponse, e);
            throw new LlmServiceException(
                    "The AI service returned an unexpected response. Please try again.", e);
        }
    }

    /**
     * Calls Gemini and returns the raw JSON body, or throws a LlmServiceException
     * with a clear, specific message (bad API key, quota, timeout, network, ...)
     * instead of letting a low-level WebClient exception bubble up unexplained.
     */
    private String callGemini(String prompt, double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("gemini.api.key is not configured (GEMINI_API_KEY env var missing or blank)");
            throw new LlmServiceException(
                    "The AI service is not configured. Set the GEMINI_API_KEY environment variable and restart the app.");
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", temperature));

        try {
            return webClient.post()
                    .uri("/models/{model}:generateContent?key={apiKey}", MODEL, apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException e) {
            // Gemini responded, but with an error status (bad key, quota, invalid model,
            // etc.)
            log.error("Gemini API returned {} - body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            String reason = switch (e.getStatusCode().value()) {
                case 400 -> "The request to the AI service was rejected (bad request).";
                case 401, 403 -> "The AI service rejected the API key. Check GEMINI_API_KEY.";
                case 404 -> "The AI model '" + MODEL + "' was not found for this API key. "
                        + "Verify the key is a Gemini API key from https://aistudio.google.com/apikey "
                        + "(not a Vertex AI/service-account key), and that it has access to this model.";
                case 429 -> "The AI service rate limit or quota was exceeded. Try again shortly.";
                default -> "The AI service returned an error (" + e.getStatusCode().value() + ").";
            };
            throw new LlmServiceException(reason, e);
        } catch (WebClientRequestException e) {
            // Could not even reach Gemini (DNS, connection refused, etc.)
            log.error("Could not reach Gemini API: {}", e.getMessage(), e);
            throw new LlmServiceException(
                    "Could not reach the AI service. Check your network/proxy settings and try again.", e);
        } catch (RuntimeException e) {
            // Reactor's .timeout() operator raises java.util.concurrent.TimeoutException,
            // but it
            // does so as an unchecked/sneaky-thrown exception via block() — the compiler
            // can't see
            // it as a checked throw, so we detect it here by inspecting the exception chain
            // instead
            // of catching TimeoutException directly (which javac rejects as unreachable).
            if (isTimeout(e)) {
                log.error("Gemini API call timed out after {}s", TIMEOUT.getSeconds());
                throw new LlmServiceException("The AI service took too long to respond. Please try again.", e);
            }
            log.error("Unexpected error calling Gemini API: {}", e.getMessage(), e);
            throw new LlmServiceException("The AI service call failed unexpectedly.", e);
        }
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String extractMessageContent(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            // e.g. the prompt was blocked -> response has "promptFeedback" instead of
            // "candidates"
            log.error("Gemini response had no candidates. Raw response: {}", rawResponse);
            throw new LlmServiceException(
                    "The AI service didn't return a result for this request (it may have been blocked by safety filters).");
        }

        return candidates.get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
    }

    private List<ReviewComment> mapToComments(LlmReviewResponse parsed, DiffChunk chunk, Review review) {
        List<ReviewComment> comments = new ArrayList<>();

        if (parsed.getIssues() == null)
            return comments;

        for (LlmReviewItem item : parsed.getIssues()) {
            ReviewComment comment = new ReviewComment();
            comment.setReview(review);
            comment.setFileName(chunk.getFileName());
            comment.setLineNumber(item.getLine());
            comment.setSeverity(Severity.valueOf(item.getSeverity()));
            comment.setMessage(item.getMessage());
            comment.setResolved(false);
            comments.add(comment);
        }

        return comments;
    }
}