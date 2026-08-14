package com.laxman.codereviewassistant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.laxman.codereviewassistant.entity.ReviewComment;


public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long>{

    List<ReviewComment> findByReviewId(Long reviewId);

    long countByReview_PullRequest_Repository_IdAndSeverity(Long repositoryId, com.laxman.codereviewassistant.entity.Severity severity);

    @Query("SELECT rc.category, COUNT(rc) FROM ReviewComment rc "
            + "WHERE rc.review.pullRequest.repository.id = :repositoryId "
            + "GROUP BY rc.category ORDER BY COUNT(rc) DESC")
    List<Object[]> countByCategoryForRepository(@Param("repositoryId") Long repositoryId);
}
