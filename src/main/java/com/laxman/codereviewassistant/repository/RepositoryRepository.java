package com.laxman.codereviewassistant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laxman.codereviewassistant.entity.Repository;
import com.laxman.codereviewassistant.entity.User;


public interface RepositoryRepository extends JpaRepository<Repository, Long> {
    Optional<Repository> findByRepoUrl(String repoUrl);
    List<Repository> findByOwner(User owner);

}
