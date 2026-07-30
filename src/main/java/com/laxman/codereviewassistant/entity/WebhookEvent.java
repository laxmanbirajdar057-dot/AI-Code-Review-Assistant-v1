package com.laxman.codereviewassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events")
@Data
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(unique = true, nullable = false)
    private String deliveryId; // GitHub's X-GitHub-Delivery header

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    private EventStatus status; // PENDING, PROCESSED, FAILED

    private LocalDateTime receivedAt;
}