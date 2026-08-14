package com.laxman.codereviewassistant.analysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.laxman.codereviewassistant.entity.CommentSource;
import com.laxman.codereviewassistant.entity.IssueCategory;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.Severity;
import com.laxman.codereviewassistant.util.DiffChunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticAnalysisEngineTest {

    private final StaticAnalysisEngine engine = new StaticAnalysisEngine();
    private final Review review = new Review();

    @Test
    void flagsHardcodedSecretAsCriticalSecurityIssue() {
        DiffChunk chunk = chunk("Config.java", 10,
                "+String password = \"hunter2\";");

        List<ReviewComment> comments = engine.analyze(chunk, review);

        assertTrue(comments.stream().anyMatch(c ->
                c.getSeverity() == Severity.CRITICAL
                        && c.getCategory() == IssueCategory.SECURITY
                        && c.getSource() == CommentSource.STATIC));
    }

    @Test
    void flagsStringConcatenatedSqlAsHighSecurityIssue() {
        DiffChunk chunk = chunk("UserRepo.java", 5,
                "+String sql = \"SELECT * FROM users WHERE id = \" + userId;");

        List<ReviewComment> comments = engine.analyze(chunk, review);

        assertTrue(comments.stream().anyMatch(c ->
                c.getSeverity() == Severity.HIGH && c.getCategory() == IssueCategory.SECURITY));
    }

    @Test
    void flagsDebugPrintAsLowQualityIssue() {
        DiffChunk chunk = chunk("Foo.java", 1,
                "+System.out.println(\"debugging here\");");

        List<ReviewComment> comments = engine.analyze(chunk, review);

        assertTrue(comments.stream().anyMatch(c ->
                c.getSeverity() == Severity.LOW && c.getCategory() == IssueCategory.QUALITY));
    }

    @Test
    void cleanCodeProducesNoFindings() {
        DiffChunk chunk = chunk("Clean.java", 1,
                "+public int add(int a, int b) {\n"
                        + "+    return a + b;\n"
                        + "+}");

        List<ReviewComment> comments = engine.analyze(chunk, review);

        assertTrue(comments.isEmpty());
    }

    @Test
    void lineNumberAdvancesForAddedAndContextLinesButNotRemovedLines() {
        // starts at line 10; one context line, one removed line (doesn't
        // advance), one added line with a secret -> should land on line 11
        DiffChunk chunk = chunk("Config.java", 10,
                " unrelated context line\n"
                        + "-String old = \"gone\";\n"
                        + "+String apiKey = \"sk-abcdef123456\";");

        List<ReviewComment> comments = engine.analyze(chunk, review);

        ReviewComment secretComment = comments.stream()
                .filter(c -> c.getCategory() == IssueCategory.SECURITY)
                .findFirst()
                .orElseThrow();
        assertEquals(11, secretComment.getLineNumber());
    }

    private DiffChunk chunk(String fileName, int startLine, String content) {
        return new DiffChunk(fileName, content, startLine);
    }
}
