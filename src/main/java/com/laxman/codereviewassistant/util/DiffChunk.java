package com.laxman.codereviewassistant.util;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DiffChunk {
    private String fileName;
    private String content;   // the actual diff text for this file
    private int startLine;    // first line number in this chunk (for mapping LLM feedback back)
}