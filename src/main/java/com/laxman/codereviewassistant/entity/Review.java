package com.laxman.codereviewassistant.entity;

import java.time.LocalDateTime;

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
@Table(name = "reviews")
@Data
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)// foreign key to the Repository entity
    private Repository repository;

    private Integer prNumber; // pull request number

    @Enumerated(EnumType.STRING)
    private ReviewStatus status; // PENDING, IN_PROGRESS, COMPLETED, REJECTED
   
    private LocalDateTime createdAt;

}