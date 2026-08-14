package com.laxman.codereviewassistant.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laxman.codereviewassistant.entity.Review;



public interface ReviewRepository extends JpaRepository<Review, Long> {
    // You can add custom query methods here if needed
    Optional<Review> findById(Long id);

    Optional<Review> findByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);

    List<Review> findByPullRequestRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    Optional<Review> findFirstByPullRequestRepositoryIdAndPullRequestPrNumberOrderByCreatedAtDesc(Long repositoryId,
            Integer prNumber);

    Double findAverageScoreByRepositoryId(Long repositoryId);

}
