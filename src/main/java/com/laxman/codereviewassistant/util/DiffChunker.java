package com.laxman.codereviewassistant.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiffChunker {

    // Matches a unified diff's file header, e.g. "diff --git a/Foo.java b/Foo.java"
    private static final Pattern FILE_HEADER = Pattern.compile("^diff --git a/(.+?) b/(.+)$");

    // Matches a hunk header, e.g. "@@ -10,6 +10,8 @@" -> new file starts at line 10
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    public List<DiffChunk> chunk(String rawDiff) {
        List<DiffChunk> chunks = new ArrayList<>();

        String[] lines = rawDiff.split("\n");

        String currentFile = null;
        StringBuilder currentContent = new StringBuilder();
        int currentStartLine = 0;

        for (String line : lines) {
            Matcher fileMatcher = FILE_HEADER.matcher(line);
            Matcher hunkMatcher = HUNK_HEADER.matcher(line);

            if (fileMatcher.matches()) {
                // Starting a new file — flush whatever we were building
                if (currentFile != null && currentContent.length() > 0) {
                    chunks.add(new DiffChunk(currentFile, currentContent.toString(), currentStartLine));
                }
                currentFile = fileMatcher.group(2);
                currentContent = new StringBuilder();
                currentStartLine = 0;

            } else if (hunkMatcher.find()) {
                // A new hunk within the same file — record where it starts
                currentStartLine = Integer.parseInt(hunkMatcher.group(1));
                currentContent.append(line).append("\n");

            } else {
                currentContent.append(line).append("\n");
            }
        }

        // Flush the last file being built
        if (currentFile != null && currentContent.length() > 0) {
            chunks.add(new DiffChunk(currentFile, currentContent.toString(), currentStartLine));
        }

        return chunks;
    }
}