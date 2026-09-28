package com.pgy.promptops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一条测试用例：给定一组变量，断言模型输出应该满足什么条件。
 * <p>
 * 断言故意做得简单（关键词 / 正则 / 长度）—— 因为它要能<b>快速、免费、稳定</b>地跑，
 * 才能成为每次改 prompt 后都会执行的回归测试。用另一个模型来打分是后面的事。
 */
@Entity
@Table(name = "test_case")
public class TestCase {

    /**
     * 断言类型。
     * <p>
     * 只有这四种，是刻意的取舍：它们都<b>不需要调用模型</b>即可判定，
     * 所以 100 条用例跑起来也是毫秒级。
     */
    public enum AssertionType {
        /** 输出必须包含某段文字 */
        CONTAINS,
        /** 输出必须不含某段文字（比如竞品名、承诺性话术） */
        NOT_CONTAINS,
        /** 输出必须匹配某个正则 */
        REGEX,
        /** 输出长度不得超过 N 个字符 */
        MAX_LENGTH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_id", nullable = false)
    private Long promptId;

    @Column(nullable = false, length = 200)
    private String name;

    /** 变量映射，JSON 对象，如 {"question":"退货运费谁承担"} */
    @Column(name = "input_vars", nullable = false, length = 5000)
    private String inputVars;

    @Enumerated(EnumType.STRING)
    @Column(name = "assertion_type", nullable = false, length = 30)
    private AssertionType assertionType;

    @Column(name = "assertion_value", nullable = false, length = 2000)
    private String assertionValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TestCase() {
    }

    public TestCase(Long promptId, String name, String inputVars,
                    AssertionType assertionType, String assertionValue) {
        this.promptId = promptId;
        this.name = name;
        this.inputVars = inputVars;
        this.assertionType = assertionType;
        this.assertionValue = assertionValue;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPromptId() {
        return promptId;
    }

    public String getName() {
        return name;
    }

    public String getInputVars() {
        return inputVars;
    }

    public AssertionType getAssertionType() {
        return assertionType;
    }

    public String getAssertionValue() {
        return assertionValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
