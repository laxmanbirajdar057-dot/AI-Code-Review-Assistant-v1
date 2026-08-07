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

            %s

            Respond in markdown. For each issue found, use this format:
            **[SEVERITY]** line X — explanation and suggested fix.

            If there are no issues, say so briefly and note anything done well.

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

            %s

            Code:
            ```
            %s
            ```
            """;

    private static final String FORMAT_PROMPT = """
            Reformat the following code to follow standard style conventions for
            its language (consistent indentation, spacing, naming conventions,
            brace placement). Do not change behavior.

            %s

            Respond with ONLY the reformatted code in a single fenced code block,
            no explanation before or after.

            Code:
            ```
            %s
            ```
            """;

    // Used when the user didn't pick a language ("Auto-detect"): the model has to
    // identify it from the code itself and say so, rather than silently guessing.
    private static final String AUTO_DETECT_INSTRUCTION =
            "The language was not specified — first identify the programming language "
            + "from the code itself and state it as \"**Detected language:** <name>\" on its own "
            + "line before anything else.";

    private static final String KNOWN_LANGUAGE_INSTRUCTION = "Language: %s";

    public SnippetResponse process(SnippetRequest request) {
        boolean languageProvided = request.getLanguage() != null && !request.getLanguage().isBlank();
        String languageInstruction = languageProvided
                ? KNOWN_LANGUAGE_INSTRUCTION.formatted(request.getLanguage())
                : AUTO_DETECT_INSTRUCTION;

        String mode = request.getMode().toUpperCase();
        String prompt = switch (mode) {
            case "REVIEW" -> REVIEW_PROMPT.formatted(languageInstruction, request.getCode());
            case "EXPLAIN" -> EXPLAIN_PROMPT.formatted(languageInstruction, request.getCode());
            // FORMAT must return only a code block, so auto-detect there is implicit
            // (the model still has to infer the language to know how to format it),
            // no "Detected language" line since that would break the fenced-code-only contract.
            case "FORMAT" -> FORMAT_PROMPT.formatted(
                    languageProvided ? languageInstruction : "Language: auto-detect from the code below.",
                    request.getCode());
            default -> throw new InvalidSnippetModeException(
                    "Unknown mode '" + request.getMode() + "'. Use REVIEW, EXPLAIN, or FORMAT.");
        };

        String result = llmClient.complete(prompt);
        return new SnippetResponse(mode, result);
    }
}
