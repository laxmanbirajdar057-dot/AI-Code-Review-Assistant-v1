package com.laxman.codereviewassistant.service;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.dto.ReviewResponse;
import com.laxman.codereviewassistant.entity.Review;
import com.laxman.codereviewassistant.entity.ReviewComment;
import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.exception.InvalidCredentialsException;
import com.laxman.codereviewassistant.exception.NotAuthorizedException;
import com.laxman.codereviewassistant.exception.ReviewNotFoundException;
import com.laxman.codereviewassistant.mapper.ReviewMapper;
import com.laxman.codereviewassistant.repository.ReviewCommentRepository;
import com.laxman.codereviewassistant.repository.ReviewRepository;
import com.laxman.codereviewassistant.repository.UserRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                          ReviewCommentRepository reviewCommentRepository,
                          UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.userRepository = userRepository;
    }

    public ReviewResponse getReview(Long repoId, Integer prNumber) {
        User currentUser = getCurrentUser();

        Review review = reviewRepository.findByRepositoryIdAndPrNumber(repoId, prNumber)
                .orElseThrow(() -> new ReviewNotFoundException("No review found for this repo/PR"));

        assertOwnerOrAdmin(review, currentUser);

        List<ReviewComment> comments = reviewCommentRepository.findByReviewId(review.getId());
        return ReviewMapper.toResponse(review, comments);
    }

    public void resolveComment(Long commentId, boolean resolved) {
        User currentUser = getCurrentUser();

        ReviewComment comment = reviewCommentRepository.findById(commentId)
                .orElseThrow(() -> new ReviewNotFoundException("Comment not found"));

        // Fix: previously this method updated the comment with no ownership check at
        // all, letting any authenticated user resolve any other user's review
        // comments (IDOR). Walk the same relationship chain getReview() uses.
        assertOwnerOrAdmin(comment.getReview(), currentUser);

        comment.setResolved(resolved);
        reviewCommentRepository.save(comment);
    }

    /**
     * ADMIN accounts can act on any review; everyone else must own the
     * repository the review belongs to.
     */
    private void assertOwnerOrAdmin(Review review, User currentUser) {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (isAdmin) {
            return;
        }

        if (!review.getRepository().getOwner().getId().equals(currentUser.getId())) {
            throw new NotAuthorizedException();
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired session"));
    }
}