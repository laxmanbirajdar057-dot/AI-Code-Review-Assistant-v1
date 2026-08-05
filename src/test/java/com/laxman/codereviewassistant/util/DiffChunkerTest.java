package com.laxman.codereviewassistant.util;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DiffChunkerTest {

    private final DiffChunker chunker = new DiffChunker();

    @Test
    void singleFileDiffProducesOneChunkWithTheFileName() {
        String diff = """
                diff --git a/UserService.java b/UserService.java
                @@ -10,3 +10,4 @@
                +    if (user == null) {
                +        throw new IllegalArgumentException();
                +    }
                """;

        List<DiffChunk> chunks = chunker.chunk(diff);

        assertEquals(1, chunks.size());
        assertEquals("UserService.java", chunks.get(0).getFileName());
    }

    @Test
    void multiFileDiffProducesOneChunkPerFile() {
        String diff = """
                diff --git a/UserService.java b/UserService.java
                @@ -10,3 +10,4 @@
                +    log.info("saving user");
                diff --git a/UserRepository.java b/UserRepository.java
                @@ -5,2 +5,3 @@
                +    // added index hint
                """;

        List<DiffChunk> chunks = chunker.chunk(diff);

        assertEquals(2, chunks.size());
        assertEquals("UserService.java", chunks.get(0).getFileName());
        assertEquals("UserRepository.java", chunks.get(1).getFileName());
    }

    @Test
    void hunkHeaderStartLineIsCapturedOnTheChunk() {
        String diff = """
                diff --git a/UserService.java b/UserService.java
                @@ -42,6 +42,8 @@
                +    validate(user);
                """;

        List<DiffChunk> chunks = chunker.chunk(diff);

        assertEquals(42, chunks.get(0).getStartLine());
    }

    @Test
    void chunkContentIncludesTheAddedLines() {
        String diff = """
                diff --git a/UserService.java b/UserService.java
                @@ -1,1 +1,2 @@
                +    // new comment
                """;

        List<DiffChunk> chunks = chunker.chunk(diff);

        assertTrue(chunks.get(0).getContent().contains("// new comment"));
    }

    @Test
    void emptyDiffProducesNoChunks() {
        List<DiffChunk> chunks = chunker.chunk("");

        assertTrue(chunks.isEmpty());
    }
}
