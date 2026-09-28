package com.pgy.promptops.infrastructure;

/**
 * 没有配置 API Key 时使用的占位实现。
 * <p>
 * 它<b>不是</b>在模拟模型的效果 —— 它的唯一作用是让人 clone 下来就能跑通整条链路：
 * 注册 Prompt、建测试集、跑评估、看通过率、对比版本。
 * <p>
 * 换句话说：它验证的是「你的流程搭对了没」，不是「你的 prompt 写得好不好」。
 * 想看真实效果，配一个 key 就行。
 */
public class MockLlmClient implements LlmClient {

    private static final String PLACEHOLDER =
            "【MOCK 模式】未配置 LLM_API_KEY，这是占位输出。"
                    + "调用链路已经完全跑通 —— 配上 key 之后，这里就是真实模型的回答。";

    @Override
    public String complete(String prompt) {
        return PLACEHOLDER;
    }

    @Override
    public String modelName() {
        return "mock";
    }
}
