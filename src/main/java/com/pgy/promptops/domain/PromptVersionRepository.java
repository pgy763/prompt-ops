package com.pgy.promptops.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {

    /** 倒序返回，最新的在最前面 —— 版本列表页就是这么展示的 */
    List<PromptVersion> findByPromptIdOrderByVersionNoDesc(Long promptId);

    Optional<PromptVersion> findByPromptIdAndVersionNo(Long promptId, Integer versionNo);

    long countByPromptId(Long promptId);
}
