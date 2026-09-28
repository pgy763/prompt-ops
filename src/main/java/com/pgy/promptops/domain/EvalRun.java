package com.pgy.promptops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一次评估运行的结果快照。
 * <p>
 * 逐条用例的明细用 JSON 存在 resultsJson 里，不为它单开一张表 ——
 * 明细只在「看某一次运行」和「对比两次运行」时用，不需要单独查询，JSON 足够了。
 */
@Entity
@Table(name = "eval_run")
public class EvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_id", nullable = false)
    private Long promptId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 这次跑用的模型标识，便于判断「分数变化是不是因为换了模型」 */
    @Column(length = 100)
    private String model;

    @Column(nullable = false)
    private Integer total;

    @Column(nullable = false)
    private Integer passed;

    /** 通过率，0~100 */
    @Column(name = "pass_rate", nullable = false)
    private Double passRate;

    /** 逐用例明细，JSON 数组 */
    @Column(name = "results_json", length = 50000)
    private String resultsJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected EvalRun() {
    }

    public EvalRun(Long promptId, int versionNo, String model) {
        this.promptId = promptId;
        this.versionNo = versionNo;
        this.model = model;
        this.startedAt = Instant.now();
        this.total = 0;
        this.passed = 0;
        this.passRate = 0.0;
    }

    public void finish(int total, int passed, String resultsJson) {
        this.total = total;
        this.passed = passed;
        this.passRate = total == 0 ? 0.0 : Math.round(passed * 1000.0 / total) / 10.0;
        this.resultsJson = resultsJson;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPromptId() {
        return promptId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getModel() {
        return model;
    }

    public Integer getTotal() {
        return total;
    }

    public Integer getPassed() {
        return passed;
    }

    public Double getPassRate() {
        return passRate;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
