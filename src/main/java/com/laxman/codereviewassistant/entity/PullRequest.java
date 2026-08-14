package com.laxman.codereviewassistant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * A GitHub pull request tracked for a Repository. Previously the PR number
 * lived directly on Review with no entity of its own, which made it
 * impossible to group multiple reviews (e.g. one per "synchronize" webhook
 * event as new commits land) under the same PR for history/comparison.
 */
@Entity
@Table(name = "pull_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "pr_number"}))
@Data
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
