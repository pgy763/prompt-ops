package com.pgy.promptops.api;

import com.pgy.promptops.application.EvalService;
import com.pgy.promptops.domain.EvalRun;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 评估与版本对比接口。
 */
@RestController
@RequestMapping("/api/prompts/{name}/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /**
     * 跑一次评估。不传 version 就评估当前生效版本。
     * <p>
     * 这是幂等的写操作 —— 每次都会产生一条新的运行记录，而不是覆盖上一次。
     * 因为「上一次的分数」本身就是最重要的参照物。
     */
    @PostMapping("/runs")
    public RunView run(@PathVariable String name,
                       @RequestParam(required = false) Integer version) {
        return RunView.of(evalService.run(name, version));
    }

    @GetMapping("/runs")
    public List<RunView> history(@PathVariable String name) {
        return evalService.history(name).stream().map(RunView::of).toList();
    }

    /** 版本对比：直接告诉你哪几条用例退化了 */
    @GetMapping("/compare")
    public EvalService.Comparison compare(@PathVariable String name,
                                          @RequestParam int baseline,
                                          @RequestParam int candidate) {
        return evalService.compare(name, baseline, candidate);
    }

    /** 运行摘要。刻意不含 resultsJson —— 列表页不需要逐条明细 */
    public record RunView(Long id, Integer versionNo, String model,
                          Integer total, Integer passed, Double passRate,
                          Instant startedAt, Instant finishedAt) {

        static RunView of(EvalRun r) {
            return new RunView(r.getId(), r.getVersionNo(), r.getModel(),
                    r.getTotal(), r.getPassed(), r.getPassRate(),
                    r.getStartedAt(), r.getFinishedAt());
        }
    }
}
