package com.laxman.codereviewassistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;
import com.laxman.codereviewassistant.util.DiffChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClientImpl implements LlmClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String MODEL = "gemini-2.5-flash";

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

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json"
                )
        );

        try {
            String rawResponse = webClient.post()
                    .uri("/models/{model}:generateContent?key={apiKey}", MODEL, apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String content = extractMessageContent(rawResponse);
            LlmReviewResponse parsed = objectMapper.readValue(content, LlmReviewResponse.class);

            return mapToComments(parsed, chunk, review);

        } catch (Exception e) {
            // Fail this chunk without failing the whole review — return no comments for it
            return new ArrayList<>();
        }
    }

    private String extractMessageContent(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        return root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
    }

    private List<ReviewComment> mapToComments(LlmReviewResponse parsed, DiffChunk chunk, Review review) {
        List<ReviewComment> comments = new ArrayList<>();

        if (parsed.getIssues() == null) return comments;

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