package com.pgy.promptops.api;

import com.pgy.promptops.application.PromptService;
import com.pgy.promptops.domain.Prompt;
import com.pgy.promptops.domain.PromptVersion;
import com.pgy.promptops.domain.TestCase;
import com.pgy.promptops.domain.TestCaseRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Prompt 与测试用例的管理接口。
 */
@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private final PromptService promptService;
    private final TestCaseRepository testCases;

    public PromptController(PromptService promptService, TestCaseRepository testCases) {
        this.promptService = promptService;
        this.testCases = testCases;
    }

    // ------------------------------------------------------------ Prompt

    @GetMapping
    public List<PromptView> list() {
        return promptService.listAll().stream().map(PromptView::of).toList();
    }

    @GetMapping("/{name}")
    public PromptDetail detail(@PathVariable String name) {
        Prompt prompt = promptService.require(name);
        PromptVersion current = promptService.currentVersion(prompt);
        return new PromptDetail(
                PromptView.of(prompt),
                current.getTemplate(),
                current.getNote(),
                testCases.countByPromptId(prompt.getId()));
    }

    @PostMapping
    public PromptView register(@RequestBody RegisterRequest req) {
        Prompt prompt = promptService.register(
                req.name(), req.description(), req.template(), req.note(), req.author());
        return PromptView.of(prompt);
    }

    // ------------------------------------------------------------ 版本

    @GetMapping("/{name}/versions")
    public List<VersionView> versions(@PathVariable String name) {
        return promptService.history(promptService.require(name))
                .stream().map(VersionView::of).toList();
    }

    @PostMapping("/{name}/versions")
    public VersionView publish(@PathVariable String name, @RequestBody PublishRequest req) {
        return VersionView.of(
                promptService.publish(name, req.template(), req.note(), req.author()));
    }

    /** 两个版本的行级差异 */
    @GetMapping("/{name}/diff")
    public List<PromptService.DiffLine> diff(@PathVariable String name,
                                             @RequestParam int from,
                                             @RequestParam int to) {
        return promptService.diff(promptService.require(name), from, to);
    }

    /** 回滚到指定版本。不删数据，只是把「当前版本」指针移回去 */
    @PostMapping("/{name}/rollback")
    public PromptView rollback(@PathVariable String name, @RequestParam int to) {
        return PromptView.of(promptService.rollback(name, to));
    }

    // ------------------------------------------------------------ 测试用例

    @GetMapping("/{name}/cases")
    public List<CaseView> cases(@PathVariable String name) {
        Prompt prompt = promptService.require(name);
        return testCases.findByPromptIdOrderByIdAsc(prompt.getId())
                .stream().map(CaseView::of).toList();
    }

    @PostMapping("/{name}/cases")
    public CaseView addCase(@PathVariable String name, @RequestBody AddCaseRequest req) {
        Prompt prompt = promptService.require(name);
        TestCase saved = testCases.save(new TestCase(
                prompt.getId(), req.name(), req.inputVars(),
                req.assertionType(), req.assertionValue()));
        return CaseView.of(saved);
    }

    // ------------------------------------------------------------ DTO

    public record RegisterRequest(String name, String description,
                                  String template, String note, String author) {
    }

    public record PublishRequest(String template, String note, String author) {
    }

    public record AddCaseRequest(String name, String inputVars,
                                 TestCase.AssertionType assertionType, String assertionValue) {
    }

    public record PromptView(Long id, String name, String description,
                             Integer currentVersionNo, Instant createdAt, Instant updatedAt) {
        static PromptView of(Prompt p) {
            return new PromptView(p.getId(), p.getName(), p.getDescription(),
                    p.getCurrentVersionNo(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record PromptDetail(PromptView prompt, String currentTemplate,
                               String currentNote, long caseCount) {
    }

    public record VersionView(Integer versionNo, String note, String author,
                              Instant createdAt, String template) {
        static VersionView of(PromptVersion v) {
            return new VersionView(v.getVersionNo(), v.getNote(), v.getAuthor(),
                    v.getCreatedAt(), v.getTemplate());
        }
    }

    public record CaseView(Long id, String name, String inputVars,
                           TestCase.AssertionType assertionType, String assertionValue) {
        static CaseView of(TestCase c) {
            return new CaseView(c.getId(), c.getName(), c.getInputVars(),
                    c.getAssertionType(), c.getAssertionValue());
        }
    }
}
