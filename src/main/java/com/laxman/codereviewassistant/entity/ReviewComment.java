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

@Entity
@Table(name = "review_comments")
@Data
public class ReviewComment {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @ManyToOne
    @JoinColumn(name = "review_id", nullable = false) // foreign key to the Review entity   
    private Review review;

    private String fileName;

    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    private Severity severity; // LOW, MEDIUM, HIGH


    @Column(length = 1000) // limit the length of the comment message
    private String message;

    private boolean resolved = false;
}
