package com.pgy.promptops.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromptRepository extends JpaRepository<Prompt, Long> {

    Optional<Prompt> findByName(String name);

    boolean existsByName(String name);
}
