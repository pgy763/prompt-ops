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
 * 一个 Prompt 的元信息。正文不在这里 —— 在 {@link PromptVersion} 里。
 * <p>
 * 「当前生效版本」用 currentVersionNo 指向，而不是在 Prompt 上直接存一份模板。
 * 这样回滚只是改一个指针，历史永远完整、永远可追溯。
 */
@Entity
@Table(name = "prompt", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Prompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务标识，如 customer-reply。全局唯一 */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** 当前生效的版本号。回滚 = 把这个值改回旧版本号 */
    @Column(name = "current_version_no")
    private Integer currentVersionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Prompt() {
    }

    public Prompt(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void moveTo(int versionNo) {
        this.currentVersionNo = versionNo;
        this.updatedAt = Instant.now();
    }

    public void describe(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
