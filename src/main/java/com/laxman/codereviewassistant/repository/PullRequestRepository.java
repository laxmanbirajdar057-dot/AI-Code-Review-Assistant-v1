package com.laxman.codereviewassistant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laxman.codereviewassistant.entity.PullRequest;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Optional<PullRequest> findByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);
}
