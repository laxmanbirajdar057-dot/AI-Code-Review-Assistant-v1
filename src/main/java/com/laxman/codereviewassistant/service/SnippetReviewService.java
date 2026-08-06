package com.laxman.codereviewassistant.service;

import com.laxman.codereviewassistant.dto.SnippetRequest;
import com.laxman.codereviewassistant.dto.SnippetResponse;
import com.laxman.codereviewassistant.exception.InvalidSnippetModeException;
import com.laxman.codereviewassistant.llm.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class SnippetReviewService {

    private final LlmClient llmClient;

    public SnippetReviewService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    private static final String REVIEW_PROMPT = """
            You are a senior code reviewer. Review the following code for bugs,
            security issues, performance problems, and style issues.

            Respond in markdown. For each issue found, use this format:
            **[SEVERITY]** line X — explanation and suggested fix.

            If there are no issues, say so briefly and note anything done well.

            Language: %s
            Code:
            ```
            %s
            ```
            """;

    private static final String EXPLAIN_PROMPT = """
            You are a patient senior engineer explaining code to a teammate.
            Explain what the following code does, step by step, in plain language.
            Keep it concise but complete — cover the overall purpose first,
            then walk through the key logic.

            Language: %s
            Code:
            ```
            %s
            ```
            """;

    private static final String FORMAT_PROMPT = """
            Reformat the following code to follow standard style conventions for
            its language (consistent indentation, spacing, naming conventions,
            brace placement). Do not change behavior.

            Respond with ONLY the reformatted code in a single fenced code block,
            no explanation before or after.

            Language: %s
            Code:
            ```
            %s
            ```
            """;

    public SnippetResponse process(SnippetRequest request) {
        String language = (request.getLanguage() == null || request.getLanguage().isBlank())
                ? "unspecified"
                : request.getLanguage();

        String prompt = switch (request.getMode().toUpperCase()) {
            case "REVIEW" -> REVIEW_PROMPT.formatted(language, request.getCode());
            case "EXPLAIN" -> EXPLAIN_PROMPT.formatted(language, request.getCode());
            case "FORMAT" -> FORMAT_PROMPT.formatted(language, request.getCode());
            default -> throw new InvalidSnippetModeException(
                    "Unknown mode '" + request.getMode() + "'. Use REVIEW, EXPLAIN, or FORMAT.");
        };

        String result = llmClient.complete(prompt);
        return new SnippetResponse(request.getMode().toUpperCase(), result);
    }
}
