package com.pgy.promptops.infrastructure;

import com.pgy.promptops.application.EvalService;
import com.pgy.promptops.application.PromptService;
import com.pgy.promptops.domain.Prompt;
import com.pgy.promptops.domain.TestCase;
import com.pgy.promptops.domain.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 塞一份示例数据，让 clone 下来的人一启动就能看到完整流程：
 * 两个版本、一批测试用例、两次评估结果、以及一次能说明问题的版本对比。
 * <p>
 * 示例刻意选了「客服回复」这个场景 —— 因为它的约束是<b>可写断言</b>的
 * （禁止亲昵称呼、禁止承诺时限、长度上限），能直接把「prompt 治理」的价值演示出来。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String PROMPT_NAME = "customer-reply";

    private final PromptService promptService;
    private final EvalService evalService;
    private final TestCaseRepository testCases;

    public DataSeeder(PromptService promptService,
                      EvalService evalService,
                      TestCaseRepository testCases) {
        this.promptService = promptService;
        this.evalService = evalService;
        this.testCases = testCases;
    }

    @Override
    public void run(String... args) {
        if (!promptService.listAll().isEmpty()) {
            return;
        }
        try {
            seed();
        } catch (Exception e) {
            log.warn("示例数据初始化失败（不影响服务启动）：{}", e.getMessage());
        }
    }

    private void seed() {
        // 同一段意图的两种写法。差别在于：v1 的要求无法验证，v2 的要求可以直接写成断言。
        String v1 = """
                你是电商平台的客服助手。请针对用户的问题给出一段回复。

                要求：
                1. 语气礼貌
                2. 回答准确

                用户的问题：{{question}}
                """;

        String v2 = """
                你是电商平台的客服助手。请针对用户的问题给出一段回复。

                要求：
                1. 不超过 100 字
                2. 语气礼貌，但不要使用「亲」「亲亲」这类过度亲昵的称呼
                3. 不要承诺具体时限（不要说「24小时」「马上」）
                4. 不确定的信息不要编造，引导用户联系人工客服

                用户的问题：{{question}}
                """;

        promptService.register(PROMPT_NAME, "电商客服自动回复：礼貌、简短、不做承诺", v1, "初始版本", "pgy");

        Prompt prompt = promptService.require(PROMPT_NAME);
        Long id = prompt.getId();
        TestCase.AssertionType notContains = TestCase.AssertionType.NOT_CONTAINS;

        testCases.save(new TestCase(id, "不出现过度亲昵的称呼",
                "{\"question\":\"我的快递怎么还没到\"}", notContains, "亲"));

        testCases.save(new TestCase(id, "不承诺具体时限",
                "{\"question\":\"退款多久能到账\"}", notContains, "24小时"));

        testCases.save(new TestCase(id, "回答长度受控",
                "{\"question\":\"你们支持哪些支付方式\"}", TestCase.AssertionType.MAX_LENGTH, "200"));

        testCases.save(new TestCase(id, "不确定的信息不编造，引导人工",
                "{\"question\":\"你们在上海有几家门店\"}", TestCase.AssertionType.CONTAINS, "客服"));

        log.info("示例数据：已注册 Prompt「{}」并写入 {} 条测试用例", PROMPT_NAME, testCases.countByPromptId(id));

        // 先评估 v1，再发布 v2 并评估 —— 这样启动后立刻能拿到一次版本对比
        evalService.run(PROMPT_NAME, 1);
        promptService.publish(PROMPT_NAME, v2,
                "把模糊要求换成可检查的约束：原来的「语气礼貌」「回答准确」无法验证，测试用例也写不出断言",
                "pgy");
        evalService.run(PROMPT_NAME, 2);

        log.info("示例数据就绪。查看版本对比："
                + "curl \"http://localhost:8080/api/prompts/{}/eval/compare?baseline=1&candidate=2\"", PROMPT_NAME);
    }
}
