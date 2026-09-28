package com.pgy.promptops.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgy.promptops.domain.EvalRun;
import com.pgy.promptops.domain.EvalRunRepository;
import com.pgy.promptops.domain.Prompt;
import com.pgy.promptops.domain.PromptVersion;
import com.pgy.promptops.domain.TestCase;
import com.pgy.promptops.domain.TestCaseRepository;
import com.pgy.promptops.infrastructure.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 回归评估引擎。
 * <p>
 * 负责两件事：<b>跑一遍测试集拿到通过率</b>，以及<b>把两个版本的结果摆在一起，指出哪几条退化了</b>。
 * 第二件事才是重点 —— 只看通过率数字，你永远不知道是哪里变坏的。
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    /** 单条输出最多存这么多字符，避免评估表被模型的长回答撑爆 */
    private static final int OUTPUT_KEEP = 800;

    private final PromptService promptService;
    private final TestCaseRepository testCases;
    private final EvalRunRepository runs;
    private final LlmClient llm;
    private final ObjectMapper objectMapper;

    public EvalService(PromptService promptService,
                       TestCaseRepository testCases,
                       EvalRunRepository runs,
                       LlmClient llm,
                       ObjectMapper objectMapper) {
        this.promptService = promptService;
        this.testCases = testCases;
        this.runs = runs;
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    /**
     * 跑一次评估。versionNo 传 null 表示评估当前生效版本。
     */
    @Transactional
    public EvalRun run(String promptName, Integer versionNo) {
        Prompt prompt = promptService.require(promptName);
        int target = versionNo != null ? versionNo : prompt.getCurrentVersionNo();
        PromptVersion version = promptService.versionOf(prompt, target);

        List<TestCase> cases = testCases.findByPromptIdOrderByIdAsc(prompt.getId());
        if (cases.isEmpty()) {
            throw new IllegalStateException(
                    "Prompt「%s」还没有测试用例，先加几条再来评估".formatted(promptName));
        }

        EvalRun run = runs.save(new EvalRun(prompt.getId(), target, llm.modelName()));

        List<CaseResult> results = new ArrayList<>(cases.size());
        int passed = 0;

        for (TestCase testCase : cases) {
            Map<String, String> vars = parseVars(testCase.getInputVars());

            String output = "";
            String detail;
            boolean ok;

            try {
                String promptText = promptService.render(version.getTemplate(), vars);
                output = llm.complete(promptText);
                ok = assertPass(testCase, output);
                detail = ok ? "断言通过" : describeFailure(testCase, output);
            } catch (Exception e) {
                ok = false;
                detail = "执行异常：" + e.getMessage();
                log.warn("用例「{}」执行失败：{}", testCase.getName(), e.getMessage());
            }

            if (ok) {
                passed++;
            }
            results.add(new CaseResult(
                    testCase.getId(), testCase.getName(),
                    truncate(output), ok, detail));
        }

        run.finish(cases.size(), passed, toJson(results));
        log.info("评估完成：{} v{} —— {}/{} 通过（{}%）",
                promptName, target, passed, cases.size(), run.getPassRate());
        return runs.save(run);
    }

    /**
     * 对比两个版本的最近一次评估结果。
     * <p>
     * 返回的每条都带一个判定，其中 {@code REGRESSED} 是最需要看的一类 ——
     * 它精确回答了「改了这句之后，到底是哪几条坏了」。
     */
    @Transactional(readOnly = true)
    public Comparison compare(String promptName, int baselineVersion, int candidateVersion) {
        Prompt prompt = promptService.require(promptName);

        EvalRun baseline = runs.findFirstByPromptIdAndVersionNoOrderByIdDesc(
                        prompt.getId(), baselineVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "v%d 还没有评估结果，先跑一次再说".formatted(baselineVersion)));
        EvalRun candidate = runs.findFirstByPromptIdAndVersionNoOrderByIdDesc(
                        prompt.getId(), candidateVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "v%d 还没有评估结果，先跑一次再说".formatted(candidateVersion)));

        Map<Long, CaseResult> baseMap = indexById(readResults(baseline));
        Map<Long, CaseResult> candMap = indexById(readResults(candidate));

        List<CaseComparison> items = new ArrayList<>();
        int regressed = 0;
        int fixed = 0;

        for (Map.Entry<Long, CaseResult> entry : baseMap.entrySet()) {
            CaseResult before = entry.getValue();
            CaseResult after = candMap.get(entry.getKey());
            if (after == null) {
                continue;
            }

            Verdict verdict;
            if (before.passed() && !after.passed()) {
                verdict = Verdict.REGRESSED;
                regressed++;
            } else if (!before.passed() && after.passed()) {
                verdict = Verdict.FIXED;
                fixed++;
            } else if (before.passed()) {
                verdict = Verdict.STILL_PASS;
            } else {
                verdict = Verdict.STILL_FAIL;
            }

            items.add(new CaseComparison(
                    entry.getKey(), before.name(), verdict,
                    before.passed(), after.passed(), after.detail()));
        }

        return new Comparison(
                promptName,
                baselineVersion, baseline.getPassRate(),
                candidateVersion, candidate.getPassRate(),
                round(baseline.getPassRate() - candidate.getPassRate()),
                regressed, fixed, items);
    }

    @Transactional(readOnly = true)
    public List<EvalRun> history(String promptName) {
        Prompt prompt = promptService.require(promptName);
        return runs.findByPromptIdOrderByIdDesc(prompt.getId());
    }

    // ---------------------------------------------------------------- 断言

    private boolean assertPass(TestCase testCase, String output) {
        String value = testCase.getAssertionValue();
        return switch (testCase.getAssertionType()) {
            case CONTAINS -> output.contains(value);
            case NOT_CONTAINS -> !output.contains(value);
            case REGEX -> matches(output, value);
            case MAX_LENGTH -> output.length() <= parseLength(value);
        };
    }

    private String describeFailure(TestCase testCase, String output) {
        return switch (testCase.getAssertionType()) {
            case CONTAINS -> "输出里没有出现「%s」".formatted(testCase.getAssertionValue());
            case NOT_CONTAINS -> "输出里出现了不该有的「%s」".formatted(testCase.getAssertionValue());
            case REGEX -> "输出不匹配正则 /%s/".formatted(testCase.getAssertionValue());
            case MAX_LENGTH -> "输出 %d 字，超过上限 %s 字"
                    .formatted(output.length(), testCase.getAssertionValue());
        };
    }

    private boolean matches(String output, String regex) {
        try {
            return Pattern.compile(regex, Pattern.DOTALL).matcher(output).find();
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("测试用例的正则写错了：" + regex, e);
        }
    }

    private int parseLength(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("MAX_LENGTH 断言的值必须是数字，当前是：" + value);
        }
    }

    // ---------------------------------------------------------------- 工具

    private Map<String, String> parseVars(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("测试用例的变量不是合法 JSON 对象：" + json, e);
        }
    }

    private Map<Long, CaseResult> indexById(List<CaseResult> results) {
        Map<Long, CaseResult> map = new LinkedHashMap<>();
        for (CaseResult r : results) {
            map.put(r.caseId(), r);
        }
        return map;
    }

    private String toJson(List<CaseResult> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            throw new IllegalStateException("评估结果序列化失败", e);
        }
    }

    private List<CaseResult> readResults(EvalRun run) {
        if (run.getResultsJson() == null || run.getResultsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(run.getResultsJson(), new TypeReference<List<CaseResult>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("评估结果解析失败，runId=" + run.getId(), e);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= OUTPUT_KEEP ? s : s.substring(0, OUTPUT_KEEP) + "…（已截断）";
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ---------------------------------------------------------------- 结果模型

    /** 单条用例的执行结果，会被序列化进 EvalRun.resultsJson */
    public record CaseResult(Long caseId, String name, String output, boolean passed, String detail) {
    }

    /** 两次运行的对比判定 */
    public enum Verdict {
        /** 之前通过，现在不通过了 —— 这就是「退化」，最该关注的 */
        REGRESSED,
        /** 之前不通过，现在通过了 */
        FIXED,
        STILL_PASS,
        STILL_FAIL
    }

    public record CaseComparison(Long caseId, String name, Verdict verdict,
                                 boolean beforePassed, boolean afterPassed, String detail) {
    }

    public record Comparison(String promptName,
                             int baselineVersion, double baselinePassRate,
                             int candidateVersion, double candidatePassRate,
                             double passRateDelta,
                             int regressedCount, int fixedCount,
                             List<CaseComparison> cases) {
    }
}
