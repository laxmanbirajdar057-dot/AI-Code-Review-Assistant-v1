package com.laxman.codereviewassistant.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.laxman.codereviewassistant.entity.CommentSource;
import com.laxman.codereviewassistant.entity.IssueCategory;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;
import com.laxman.codereviewassistant.util.DiffChunk;

/**
 * Deterministic, rule-based analysis that runs on every diff chunk
 * independently of the LLM. This is the "don't depend completely on the
 * LLM" half of the review pipeline — same input always produces the same
 * findings, which the AI layer alone can't guarantee.
 *
 * Operates only on *added* lines (diff lines starting with "+", excluding
 * the "+++" file-header line) — we only want to flag code being introduced,
 * not pre-existing code just passing through context lines.
 *
 * Line numbers are tracked by walking the unified diff the same way a
 * human reading it would: added and context lines both advance the new
 * file's line counter, removed lines don't (they only existed in the old
 * file).
 */
@Component
public class StaticAnalysisEngine {

    private static final Pattern HARDCODED_SECRET = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\s*=\\s*\"[^\"]{3,}\"");

    private static final Pattern SQL_INJECTION_RISK = Pattern.compile(
            "\"\\s*(SELECT|INSERT|UPDATE|DELETE)\\b.*\"\\s*\\+", Pattern.CASE_INSENSITIVE);

    private static final Pattern DEBUG_STATEMENT = Pattern.compile(
            "System\\.(out|err)\\.print(ln)?\\s*\\(|\\.printStackTrace\\s*\\(");

    private static final Pattern TODO_FIXME = Pattern.compile(
            "(?i)//\\s*(TODO|FIXME)\\b");

    // Heuristic stand-in for "long method": a single hunk introducing a large
    // number of added lines in one file is a signal the change (or the method
    // it's inside) is doing too much and is worth a human's attention.
    private static final int LARGE_HUNK_ADDED_LINE_THRESHOLD = 40;

    public List<ReviewComment> analyze(DiffChunk chunk, Review review) {
        List<ReviewComment> comments = new ArrayList<>();

        String[] lines = chunk.getContent().split("\n", -1);
        int currentLine = chunk.getStartLine();
        int addedLineCount = 0;

        for (String rawLine : lines) {
            if (rawLine.startsWith("+++") || rawLine.startsWith("---") || rawLine.startsWith("@@")) {
                continue; // diff metadata, not content
            }

            if (rawLine.startsWith("+")) {
                String content = rawLine.substring(1);
                addedLineCount++;
                checkLine(content, chunk.getFileName(), currentLine, review, comments);
                currentLine++;
            } else if (rawLine.startsWith("-")) {
                // removed line — only existed in the old file, new-line counter
                // does not advance
            } else {
                // context line — present in both old and new file
                currentLine++;
            }
        }

        if (addedLineCount > LARGE_HUNK_ADDED_LINE_THRESHOLD) {
            comments.add(buildComment(review, chunk.getFileName(), chunk.getStartLine(),
                    Severity.MEDIUM, IssueCategory.MAINTAINABILITY,
                    "This change adds " + addedLineCount + " lines in a single hunk — "
                            + "consider whether it should be split into smaller, more reviewable pieces."));
        }

        return comments;
    }

    private void checkLine(String content, String fileName, int lineNumber, Review review,
            List<ReviewComment> out) {
        if (HARDCODED_SECRET.matcher(content).find()) {
            out.add(buildComment(review, fileName, lineNumber, Severity.CRITICAL, IssueCategory.SECURITY,
                    "Possible hardcoded credential or secret. Move this to an environment variable "
                            + "or a secrets manager — never commit secrets directly in source."));
        }

        if (SQL_INJECTION_RISK.matcher(content).find()) {
            out.add(buildComment(review, fileName, lineNumber, Severity.HIGH, IssueCategory.SECURITY,
                    "SQL string appears to be built via concatenation. Use a parameterized query "
                            + "(PreparedStatement / JPA query parameters) to avoid SQL injection."));
        }

        if (DEBUG_STATEMENT.matcher(content).find()) {
            out.add(buildComment(review, fileName, lineNumber, Severity.LOW, IssueCategory.QUALITY,
                    "Debug/print statement left in code. Use a logger (e.g. SLF4J) instead so output "
                            + "can be controlled by log level in production."));
        }

        if (TODO_FIXME.matcher(content).find()) {
            out.add(buildComment(review, fileName, lineNumber, Severity.INFO, IssueCategory.MAINTAINABILITY,
                    "TODO/FIXME comment introduced — consider filing a tracked issue instead of "
                            + "leaving it in code where it's easy to lose track of."));
        }
    }

    private ReviewComment buildComment(Review review, String fileName, int lineNumber,
            Severity severity, IssueCategory category, String message) {
        ReviewComment comment = new ReviewComment();
        comment.setReview(review);
        comment.setFileName(fileName);
        comment.setLineNumber(lineNumber);
        comment.setSeverity(severity);
        comment.setCategory(category);
        comment.setSource(CommentSource.STATIC);
        comment.setMessage(message);
        comment.setResolved(false);
        return comment;
    }
}
