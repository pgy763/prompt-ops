package com.pgy.promptops.application;

import com.pgy.promptops.domain.Prompt;
import com.pgy.promptops.domain.PromptRepository;
import com.pgy.promptops.domain.PromptVersion;
import com.pgy.promptops.domain.PromptVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Prompt 的版本管理。
 * <p>
 * 三条不可动摇的规则：
 * <ol>
 *   <li><b>版本只增不改。</b> 想改内容就发新版本，老版本一个字都不动</li>
 *   <li><b>回滚只是移动指针。</b> 所以回滚之后还能再回滚回来</li>
 *   <li><b>每个版本必须写清「为什么改」。</b> 这是三个月后你唯一能依靠的信息</li>
 * </ol>
 */
@Service
public class PromptService {

    private final PromptRepository prompts;
    private final PromptVersionRepository versions;

    public PromptService(PromptRepository prompts, PromptVersionRepository versions) {
        this.prompts = prompts;
        this.versions = versions;
    }

    /** 注册一个新 Prompt，同时写入 v1 */
    @Transactional
    public Prompt register(String name, String description, String template, String note, String author) {
        if (prompts.existsByName(name)) {
            throw new IllegalArgumentException("Prompt 已存在：" + name);
        }
        Prompt prompt = prompts.save(new Prompt(name, description));
        versions.save(new PromptVersion(prompt.getId(), 1, template, note, author));
        prompt.moveTo(1);
        return prompts.save(prompt);
    }

    /** 发布新版本。这是修改 Prompt 内容的唯一入口 */
    @Transactional
    public PromptVersion publish(String name, String template, String note, String author) {
        Prompt prompt = require(name);
        int nextNo = (int) versions.countByPromptId(prompt.getId()) + 1;
        PromptVersion version = versions.save(
                new PromptVersion(prompt.getId(), nextNo, template, note, author));
        prompt.moveTo(nextNo);
        prompts.save(prompt);
        return version;
    }

    /** 回滚 = 把「当前版本」指针移回去。不删除任何数据 */
    @Transactional
    public Prompt rollback(String name, int versionNo) {
        Prompt prompt = require(name);
        versions.findByPromptIdAndVersionNo(prompt.getId(), versionNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "版本不存在：%s v%d".formatted(name, versionNo)));
        prompt.moveTo(versionNo);
        return prompts.save(prompt);
    }

    @Transactional(readOnly = true)
    public Prompt require(String name) {
        return prompts.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Prompt 不存在：" + name));
    }

    @Transactional(readOnly = true)
    public PromptVersion currentVersion(Prompt prompt) {
        return versions.findByPromptIdAndVersionNo(prompt.getId(), prompt.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException(
                        "当前版本数据缺失（指针指向 v" + prompt.getCurrentVersionNo() + "），数据可能不一致"));
    }

    @Transactional(readOnly = true)
    public PromptVersion versionOf(Prompt prompt, int versionNo) {
        return versions.findByPromptIdAndVersionNo(prompt.getId(), versionNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "版本不存在：%s v%d".formatted(prompt.getName(), versionNo)));
    }

    @Transactional(readOnly = true)
    public List<PromptVersion> history(Prompt prompt) {
        return versions.findByPromptIdOrderByVersionNoDesc(prompt.getId());
    }

    @Transactional(readOnly = true)
    public List<Prompt> listAll() {
        return prompts.findAll();
    }

    /**
     * 渲染模板。
     * <p>
     * <b>变量没替换干净就抛错，不做静默放过。</b>
     * 静默漏替换会让评估结果全部失真，而你可能跑几十次才发现 —— 那比直接报错昂贵得多。
     */
    public String render(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        if (result.contains("{{")) {
            int idx = result.indexOf("{{");
            String fragment = result.substring(idx, Math.min(idx + 40, result.length()));
            throw new IllegalArgumentException(
                    "模板变量未全部替换，检查测试用例的变量是否齐全。残留片段：" + fragment);
        }
        return result;
    }

    /** 两个版本之间的行级差异 */
    @Transactional(readOnly = true)
    public List<DiffLine> diff(Prompt prompt, int fromNo, int toNo) {
        String from = versionOf(prompt, fromNo).getTemplate();
        String to = versionOf(prompt, toNo).getTemplate();
        return lcsDiff(
                Arrays.asList(from.split("\n", -1)),
                Arrays.asList(to.split("\n", -1)));
    }

    /**
     * 一个差异行。type 的取值：{@code " "} 未变 · {@code "-"} 删除 · {@code "+"} 新增
     */
    public record DiffLine(String type, String text) {
    }

    /**
     * 行级 LCS diff。
     * <p>
     * 自己写而不引三方库，是因为需求就只有「按行对比」这一种 —— 留一个 30 行的实现，
     * 比多一个依赖更容易维护，也更容易讲清楚。
     */
    private List<DiffLine> lcsDiff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];

        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = a.get(i).equals(b.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<DiffLine> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                out.add(new DiffLine(" ", a.get(i)));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(new DiffLine("-", a.get(i)));
                i++;
            } else {
                out.add(new DiffLine("+", b.get(j)));
                j++;
            }
        }
        while (i < n) {
            out.add(new DiffLine("-", a.get(i++)));
        }
        while (j < m) {
            out.add(new DiffLine("+", b.get(j++)));
        }
        return out;
    }
}
