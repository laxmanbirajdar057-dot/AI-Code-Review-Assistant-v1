package com.laxman.codereviewassistant.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.entity.ReviewComment;

/**
 * Combines the deterministic StaticAnalysisEngine output with the LLM's
 * output into a single report per chunk. Kept as its own small service
 * (rather than inlined in ReviewWorkerService) because "combine + dedupe +
 * order the two analysis layers" is a distinct responsibility that's worth
 * being able to test and reason about on its own.
 */
@Service
public class ReviewAggregatorService {

    public List<ReviewComment> combine(List<ReviewComment> staticComments, List<ReviewComment> aiComments) {
        // Drop exact duplicates (same file+line+severity+message) — e.g. a debug
        // print statement the static rule catches AND the LLM independently
        // flags. Keeps the static-engine version since it's deterministic.
        Set<String> seen = new LinkedHashSet<>();
        List<ReviewComment> merged = new ArrayList<>();

        for (ReviewComment comment : staticComments) {
            if (seen.add(dedupeKey(comment))) {
                merged.add(comment);
            }
        }
        for (ReviewComment comment : aiComments) {
            if (seen.add(dedupeKey(comment))) {
                merged.add(comment);
            }
        }

        // Most severe first — CRITICAL enum constant sorts first by ordinal
        merged.sort(Comparator.comparing(c -> c.getSeverity().ordinal()));
        return merged;
    }

    private String dedupeKey(ReviewComment comment) {
        return comment.getFileName() + "|" + comment.getLineNumber() + "|"
                + comment.getSeverity() + "|" + comment.getMessage();
    }
}
