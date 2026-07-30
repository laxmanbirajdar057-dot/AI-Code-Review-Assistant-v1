package com.laxman.codereviewassistant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laxman.codereviewassistant.entity.ReviewComment;


public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long>{

    List<ReviewComment> findByReviewId(Long reviewId);
}
