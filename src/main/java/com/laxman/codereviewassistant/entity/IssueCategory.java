package com.laxman.codereviewassistant.entity;

/**
 * Category an issue falls into. Drives both the weighted scoring in
 * ScoringService and the "most common issue category" analytics query.
 */
public enum IssueCategory {
    SECURITY,
    QUALITY,
    MAINTAINABILITY,
    PERFORMANCE,
    RELIABILITY
}
