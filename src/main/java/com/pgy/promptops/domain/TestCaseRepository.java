package com.pgy.promptops.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByPromptIdOrderByIdAsc(Long promptId);

    long countByPromptId(Long promptId);
}
