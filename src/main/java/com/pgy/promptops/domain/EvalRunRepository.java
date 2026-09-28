package com.pgy.promptops.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

    List<EvalRun> findByPromptIdOrderByIdDesc(Long promptId);

    List<EvalRun> findByPromptIdAndVersionNoOrderByIdDesc(Long promptId, Integer versionNo);

    /** 取某个版本最近一次评估结果 —— 对比版本时用 */
    Optional<EvalRun> findFirstByPromptIdAndVersionNoOrderByIdDesc(Long promptId, Integer versionNo);
}
