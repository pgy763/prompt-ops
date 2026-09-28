package com.pgy.promptops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Prompt 的某一个版本。
 * <p>
 * <b>一旦写入就不再修改</b> —— 这是「可回滚」和「可对比」的前提。
 * 想改内容就新建一个版本，不要动老的。
 */
@Entity
@Table(
        name = "prompt_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"prompt_id", "version_no"})
)
public class PromptVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_id", nullable = false)
    private Long promptId;

    /** 从 1 开始递增，同一个 prompt 内唯一 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 模板正文，变量用 {{name}} 占位 */
    @Column(nullable = false, length = 20000)
    private String template;

    /**
     * 这次改动的说明。<b>必填</b>。
     * 没写清「为什么改」的版本，三个月后等于没有 —— 你只会看到两个版本的 diff，
     * 却不知道当时在想什么。
     */
    @Column(nullable = false, length = 500)
    private String note;

    @Column(length = 100)
    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PromptVersion() {
    }

    public PromptVersion(Long promptId, int versionNo, String template, String note, String author) {
        this.promptId = promptId;
        this.versionNo = versionNo;
        this.template = template;
        this.note = note;
        this.author = author;
        this.createdAt = Instant.now();
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

    public String getTemplate() {
        return template;
    }

    public String getNote() {
        return note;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
