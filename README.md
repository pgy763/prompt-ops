# Prompt Ops

![java](https://img.shields.io/badge/Java-17%2B-orange)
![spring](https://img.shields.io/badge/Spring_Boot-3.x-green)
![status](https://img.shields.io/badge/状态-MVP-blue)
![lang](https://img.shields.io/badge/语言-中文-red)

> **把 Prompt 当代码管理：版本、diff、回归测试。**
>
> 属于一组内容：**[接入手册](https://pgy763.github.io/hello-ai-backend/) 讲怎么接**、**[踩坑手册](https://pgy763.github.io/llm-dev-pitfalls/all) 记怎么错**、这里是**上线之后怎么别把它改坏**。总入口：[pgy763.github.io](https://pgy763.github.io/)

---

## 一个你迟早会遇到的场景

你在做一个 AI 功能。今天模型答得挺好。

第二天你觉得「措辞再收敛一点会更好」，顺手把 system prompt 里一句话改了，上线。

一周后客服反馈：**最近回答变差了。**

你打开代码，看着那段 prompt，然后发现自己答不上来任何一个问题：

| 你想知道的 | 实际情况 |
| --- | --- |
| 一周前改了什么？ | 不知道 —— 老版本被覆盖了 |
| 差了多少？ | 不知道 —— 没有人测过 |
| 是哪句话导致的？ | 不知道 |
| 改回去？ | 改不回去 —— 旧的内容没留下 |

**根源不是「你改错了」，而是 Prompt 从来没有被当作代码对待。**

代码有 Git：版本、diff、回滚、CI。Prompt 呢？大多数人把它硬编码在字符串里，改了就丢了。

Prompt Ops 补的就是这一块。

---

## 它做什么

| 能力 | 解决什么 |
| --- | --- |
| **版本只增不改** | 每次修改留下一个不可变版本。老版本不会被覆盖，随时能看清改了什么 |
| **必填的变更说明** | 每个版本必须写清「为什么改」。三个月后你会需要它 |
| **行级 diff** | 两个版本摆在一起，逐行标出增删 |
| **一键回滚** | 回滚只是移动指针 —— 所以回滚之后还能再回滚回来 |
| **可执行的测试集** | 把「这条 prompt 应该做到什么」写成断言，而不是靠感觉 |
| **回归评估** | 改完跑一遍全套用例，拿到通过率 |
| **逐用例版本对比** | 不只告诉你「分数降了 8%」，而是精确指出**哪几条退化了** |

最后一条是这个项目的重点。**只看通过率，你永远不知道是哪里变坏的。**

---

## 快速开始

需要 JDK 17+ 和 Maven 3.9+（不需要数据库 —— 默认用内存库）。

```bash
git clone https://github.com/pgy763/prompt-ops.git
cd prompt-ops
mvn spring-boot:run
```

**没有 API Key 也能跑。** 未配置时会自动降级为 MOCK 模式：流程完整跑通，但输出是占位内容。
想看真实效果，配一个 OpenAI 兼容的 key：

```bash
export LLM_API_KEY=sk-xxxxxxxx
export LLM_BASE_URL=https://api.deepseek.com/v1   # 可选，默认就是它
export LLM_MODEL=deepseek-chat                     # 可选
mvn spring-boot:run
```

启动后会自动塞一份示例数据（一个客服 Prompt、4 条测试用例、两次评估结果），
可以直接看到完整效果：

```bash
curl "http://localhost:8080/api/prompts/customer-reply/eval/compare?baseline=1&candidate=2"
```

---

## 走一遍完整流程

### 1. 注册一个 Prompt

```bash
curl -X POST http://localhost:8080/api/prompts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "order-assistant",
    "description": "订单查询助手的系统提示词",
    "template": "你是订单助手。根据用户描述判断他想要什么。\n用户说：{{question}}",
    "note": "初始版本",
    "author": "pgy"
  }'
```

模板里的 `{{question}}` 是变量占位符。**变量没被全部替换时会直接报错** ——
静默漏替换会让一整轮评估结果失真，那比报错昂贵得多。

### 2. 加几条测试用例

```bash
curl -X POST http://localhost:8080/api/prompts/order-assistant/cases \
  -H "Content-Type: application/json" \
  -d '{
    "name": "不能承诺具体时限",
    "inputVars": "{\"question\":\"我的订单什么时候到\"}",
    "assertionType": "NOT_CONTAINS",
    "assertionValue": "明天"
  }'
```

四种断言：`CONTAINS` · `NOT_CONTAINS` · `REGEX` · `MAX_LENGTH`。

它们**都只靠输出本身判定，不需要再调一次模型** —— 所以 100 条用例跑起来也是毫秒级，
你才会愿意每次改完都跑。

### 3. 跑一次评估

```bash
curl -X POST "http://localhost:8080/api/prompts/order-assistant/eval/runs"
```

### 4. 改 Prompt —— 注意是「发新版本」，不是「覆盖」

```bash
curl -X POST http://localhost:8080/api/prompts/order-assistant/versions \
  -H "Content-Type: application/json" \
  -d '{
    "template": "...改进后的内容...",
    "note": "把「回答准确」换成「不得承诺具体时限」—— 原来的要求无法验证",
    "author": "pgy"
  }'
```

### 5. 再跑一次，然后对比

```bash
curl -X POST "http://localhost:8080/api/prompts/order-assistant/eval/runs"
curl "http://localhost:8080/api/prompts/order-assistant/eval/compare?baseline=1&candidate=2"
```

对比结果长这样：

```json
{
  "promptName": "order-assistant",
  "baselineVersion": 1, "baselinePassRate": 100.0,
  "candidateVersion": 2, "candidatePassRate": 75.0,
  "passRateDelta": 25.0,
  "regressedCount": 1, "fixedCount": 0,
  "cases": [
    { "name": "不能承诺具体时限", "verdict": "REGRESSED", "beforePassed": true, "afterPassed": false,
      "detail": "输出里出现了不该有的「明天」" }
  ]
}
```

`REGRESSED` 就是你要找的东西 —— **它精确回答了「这次改动让哪一条坏了」。**

### 6. 看 diff、回滚

```bash
curl "http://localhost:8080/api/prompts/order-assistant/diff?from=1&to=2"
curl -X POST "http://localhost:8080/api/prompts/order-assistant/rollback?to=1"
```

---

## 完整接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/prompts` | 列出所有 Prompt |
| POST | `/api/prompts` | 注册新 Prompt（自动写入 v1） |
| GET | `/api/prompts/{name}` | 详情（含当前生效的模板正文） |
| GET | `/api/prompts/{name}/versions` | 版本历史（倒序） |
| POST | `/api/prompts/{name}/versions` | **发布新版本**（修改内容的唯一入口） |
| GET | `/api/prompts/{name}/diff?from=1&to=2` | 两个版本的行级差异 |
| POST | `/api/prompts/{name}/rollback?to=1` | 回滚到指定版本 |
| GET | `/api/prompts/{name}/cases` | 测试用例列表 |
| POST | `/api/prompts/{name}/cases` | 新增测试用例 |
| POST | `/api/prompts/{name}/eval/runs` | 跑评估（`?version=` 可选，默认当前版本） |
| GET | `/api/prompts/{name}/eval/runs` | 评估历史 |
| GET | `/api/prompts/{name}/eval/compare?baseline=1&candidate=2` | **版本对比** |

---

## 几个设计决定

写在 [docs/design.md](docs/design.md) 里，这里只列结论：

1. **版本只增不改。** 因为「可回滚」和「可对比」都建立在「历史不可变」之上。
2. **回滚只是移动指针。** 所以没有「回滚导致历史丢失」这种事。
3. **断言只用本地可判定的四类。** 用模型给模型打分很诱人，但那会让回归测试变慢、变贵、变得不稳定 —— 而回归测试必须能被无脑地重复执行。
4. **没有 API Key 也能完整跑通。** 流程验证和效果验证是两件事，前者不该被 key 卡住。
5. **评估结果不覆盖，每次都留一条。** 「上一次是多少分」本身就是最重要的参照物。

---

## 排查：端口不是 8080

如果你配了 `server.port: 8080`，启动日志却显示 `Tomcat started on port 5xxxx`（随机端口），**大概率是环境变量覆盖了配置**。

Spring Boot 的宽松绑定会把环境变量映射成配置属性，而且**环境变量的优先级高于 `application.yml`**：

| 环境变量 | 会被解析成 |
| --- | --- |
| `SERVER_PORT` | `server.port` |
| `SERVER__PORT` | `server.port`（双下划线是分隔符转义） |
| `SPRING_APPLICATION_JSON` | 一组 JSON 形式的配置 |

**这类变量往往不是你设的** —— IDE、容器编排平台、CI、本地开发工具都可能注入
（例如某些平台用 `SERVER__PORT=0` 表示「随便挑个可用端口」）。这个坑在本项目开发时就真实踩到过。

排查方法：

```bash
# Linux / macOS / Git Bash
env | grep -iE "server.*port|spring"

# Windows PowerShell
Get-ChildItem Env: | Where-Object { $_.Name -match "SERVER|SPRING" }
```

确认之后，两个选择：

```bash
# 方案一：用命令行参数覆盖（优先级最高，已验证有效）
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080

# 方案二：清掉那个环境变量，让 application.yml 生效
```

> **通用结论**：`application.yml` 不是最高优先级。真实顺序大致是
> **命令行参数 > 环境变量 / 系统属性 > `application.yml`**。
> 配置「明明写了却不生效」的时候，先怀疑外部注入，而不是反复怀疑文件写错了。

---

## 为什么不用现成的 SaaS 工具

LangSmith、PromptLayer 这类工具做得很好，但它们有两个前提：**接受数据出网**，以及**团队接受一套新平台**。

如果你在的公司里，Prompt 是和业务代码一起 review、一起发布的（大多数 Java 团队是这样），
那你需要的不是另一个平台，而是**一个能嵌进现有流程的工具**。

另外，Java 生态里这块基本是空白的。

---

## 环境

| 组件 | 版本 |
| --- | --- |
| JDK | 17+ |
| Spring Boot | 3.3.4 |
| 数据库 | H2（内存，开箱即用）／可换 MySQL |
| 模型 | OpenAI 兼容协议（DeepSeek / 通义 / 智谱等） |

---

## License

[MIT](LICENSE)
