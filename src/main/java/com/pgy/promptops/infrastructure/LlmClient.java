package com.pgy.promptops.infrastructure;

/**
 * 模型调用的最小抽象。
 * <p>
 * 刻意不做多轮对话、工具调用、流式 —— 评估场景只需要「给一段 prompt，拿一段输出」。
 * 保持最小接口，替换实现才没有心理负担。
 */
public interface LlmClient {

    String complete(String prompt);

    /** 模型标识，会记进评估结果。用来判断「分数变化是不是因为换了模型」 */
    String modelName();
}
