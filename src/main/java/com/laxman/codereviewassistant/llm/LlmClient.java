package com.laxman.codereviewassistant.llm;

import java.util.List;

import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.util.DiffChunk;

public interface LlmClient {
    List<ReviewComment> review(DiffChunk chunk, Review review);
}