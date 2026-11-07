# 编排模型试验

## 目的与顺序

先由 GPT-6 Astra ultra 编排完成 ROADMAP 1.3 的修复和完整验收，再比较 GPT-6.1 Terra。实现仍由 DeepSeek 执行，Grok 做完整验收，Astra 亲自复核。代码修复、必需自动检查及阻塞关闭必须有实际证据。本轮用户明确要求跳过外置 Chrome 验收，因此仅报告代码验收结论，页面和真实服务继续列为未验证。只读合同评分不能作为切换模型的条件。

本轮记录实际模型与推理档位、起始和最终基线、耗时、委派状态、首次交付遗漏、返工次数、逐项证据和未验证项。1.3 通过后再明确 Terra 的实际比较任务；若复做相同任务，须使用保存的同一初始基线及隔离环境，不回滚或覆盖已通过的成果。共享上下文使比较非盲，单次结果不证明模型普遍优劣。

## 当前完整任务

按 `feature-briefs/2026-11-06-storyboard-grid.md` 完成 1.3 全部适用验收，覆盖首次生成、失败重试、刷新、参考图、复制与模板、组重跑、用户内容保护、删除取消并发和既有功能回归。共享行为和覆盖策略按项目规则先确认；未确认不交执行者猜。合并、推送、真实计费调用均以用户当前授权为准，不从旧文档自动扩大权限。

## 已做的只读准备

以 ROADMAP 1.3 现有未提交实现为对象，只读检查「复制单个镜头、多选复制粘贴、组模板插入」之后，宫格与镜头的归属是否仍然正确。对照现有复制、组模板、组重跑和分镜切图实现，输出一个可交给执行者的最小任务合同：

1. 实际工作区、分支、HEAD、未提交文件及关键代码位置。
2. 用户可观察的业务结果、允许和禁止范围、已有流程与依赖。
3. 成功、独立复制、整体复制、缺少父节点、模板插入、组重跑及用户已有内容保护的验收项；每项包含触发条件、预期结果和验证方式。
4. 需用户确认的共享行为或产品决策、失败路径、未验证项；不得自行修改代码或调用计费服务。

上述只读检查仅是修复前准备，不等同于整体验收。浏览器交互、真实出图和扣费未执行时必须明确标注。不得据此提前切换 Terra。

## 准备材料评分（不替代功能验收）

每项 0、1 或 2 分：代码证据准确；关键场景覆盖；范围和用户数据保护；验收可执行且区分未验证。0 为缺失或错误，1 为部分满足，2 为满足且可核查。最高 8 分。没有准确基线、遗漏用户内容保护、越权改动或把未验证写成通过时，无论总分均不合格。该分数只评价准备材料；进入 Terra 比较的前提是 Astra 完成 1.3 的完整修复验收。

## Astra 本轮实际结果

结论：本轮限定代码验收通过，四项确认缺陷关闭；页面由用户要求跳过，真实出图和计费未测，未提交、未推送、未合并。尚未切换 Terra，也不据单次结果判断 Astra 优于 Terra。

- 角色实际执行：GPT-6 Astra ultra 编排与独立复核；DeepSeek V4.1 Flash high 一次主体交付；用户同意收敛后，Astra 直接完成四项小修；Cursor Grok 4.7 high 两次完整代码审查，第一次不通过、第二次通过。
- 首次交付遗漏：晚返回仍写图/报错、切图用旧父布局、复制继承无效父归属、删除来源连带中止宫格。原有 101 项全绿没有覆盖真正的页面回调竞争窗口；编排者未在首次交付前落实入口证据，执行测试也存在上传尚未开始便触发取消的缺口。
- 收敛后验证：同一组入口回归在修复前 14 pass / 11 fail，修复后 25 pass / 0 fail；全量 126 pass / 0 fail / 483 assertions / 15 files。类型检查、构建和 diff 检查通过。Grok 亲自复跑入口测试 25 pass / 0 fail / 135 assertions / 196ms。
- 基线：worktree `/Users/eric/.t3/worktrees/pilipala/t3-879d4719`，HEAD `542fd6f76a0fa8e8e69a9d744910f3791cb24d44`；起始 diff SHA-256 `3ed7ae24abd99e5f4cec93ec9366ce1af969c6baea19759a0cff4e353faa971b`；DeepSeek 交付时 diff `ac217b5e17ca1dc2ec9ab6b4658f9719440716bffbba9fe73ef944299449280c`。
- 终态：同一 HEAD，diff SHA-256 `25d60d3462d9caec595f5c5a680202b04ca36ca7b7b681f9df8c5312b2ad62a6`；未跟踪入口测试 `web/tests/canvas-storyboard-entry.test.ts` SHA-256 `b248b50209d84d1783becc4ef143e559be4528d5fd5373da8a83115511e837e4`，需要与 diff 一起交接。
- 验证限制：入口测试执行 production callback，但使用内存状态和可控服务，不覆盖 React 调度、DOM、真实图片解码、云同步、真实供应商及计费；首次生成参考图分支未被入口测试执行，重试 edit 分支已覆盖。没有已验证浏览器 URL。
- 时间证据：实现冻结确认于 2026-11-07 02:34:20 UTC，收尾核对于 02:47:37 UTC；全量测试 1052ms、构建 6.78s。整轮含用户暂停，未连续记录有效工作耗时，不能可靠排名模型速度；最终委派等待超过 10 分钟后由完成通知恢复。
- 本地证据：`/tmp/pilipala-astra-13-acceptance-20261107/final-report.md`、同目录日志与 `final.patch`；临时依赖软链已移除。

本轮说明编排和独立验收能拦住缺陷，但首次交付和证据落实仍有遗漏；经过一轮集中小修才通过。后续模型对比需另行明确同一初始基线、隔离环境和相同验证豁免，不覆盖这次成果。

## 1.3 交付与下一轮

用户在代码验收通过后授权提交推送 1.3，并开始 1.4。修复提交 `4c0778d`，分支 `ljx/fix/storyboard-grid-lifecycle`。原始未提交指纹保留作验收历史；以上“不提交/推送”描述对应修复验收阶段。合并 main 与部署尚未执行。

下一轮先继续 Astra ultra 编排、DeepSeek 实现、Grok 4.7 验收，检验收敛后的流程，不同时更换模型。1.4 从已验收代码建立独立基线，先明确可验收切片及共享修改范围。普通返工由执行者承担；编排者若接手小修，记录实际作者，不记为执行者首次通过。

## 1.4a 当前合同与用量记录

用户已确认 [1.4a 角色参考卡](./feature-briefs/2026-11-07-character-reference-card.md)：使用普通画布组、独立图片副本和角色描述，复用现有连线、显式引用和本地模板，并授权限定的图片工具栏、画布页面、文案与测试改动。基线为已推送的 `5c0aa869455174300ebc38ba06b12af1f7469b6c`，功能分支为 `ljx/feature/character-reference-card`。本轮已完成代码验收；页面在 Chrome 控制连接阻塞后由用户单独要求跳过，豁免仅适用于 1.4a。

从 1.4a 规划开始记录用量，每个小点收尾按模型列出输入、输出、缓存读取/写入、总 Mtoken、角色和返工轮次。`1 Mtoken = 1,000,000 tokens`；保留原始整数与运行归属，只汇总本切片，排除 1.3、网络排查与其他线程。失败运行若无 usage，明确写缺失；运行尚未结束时只报告截至采样时的部分统计。

- Codex 读取逐响应 `token_usage_record`，按 `response_id` 去重并关联实际 turn；原生子代理单独归属后计入。其 total 为 input + output，缓存输入和 reasoning 输出是子项，不重复相加。
- OpenCode 读取指定 session 中 assistant 消息的 `tokens`，按 message ID 去重，采用原始 `tokens.total`，不再叠加 session 累计或 part 记录。
- Cursor 读取 `run.completed` 的 `result.usage`，按 provider session 和 native run ID 去重；同份 update 不重复计入。原始 total、缓存及 reasoning 单列，未确认跨渠道语义相同前不强行统一口径。
- 不以界面上下文占用、账户全天统计、费用或文本长度反推功能用量。每次交付及时保存仅含运行 ID、模型和 usage 的统计快照，避免日志轮转丢失证据，不保存密钥或请求正文。

本切片的本机统计起点与来源索引已保存在 `/tmp/pilipala-14a-usage/manifest.json`；功能完成后在此补充实际结果、用量、返工与未验证项。

## 下一轮：1.4b 三视图

用户已确认输出合同：正面/侧面/背面合成一张排版图，严格一次图生图请求、一次计费、一个普通图片节点；不自动切图。合同见 [1.4b brief](./feature-briefs/2026-11-07-character-three-view.md)。继续 Astra ultra 编排 → DeepSeek V4.1 Flash high 实现 → Grok 4.7 high 独立验收，不切模型，以便增加同一流水线的可比轮次。

1.4a 的基线仍为 HEAD `5c0aa869455174300ebc38ba06b12af1f7469b6c` 加未提交 diff。1.4a 尚未提交，因此下一切片在同一功能分支接着实现，必须严格按 brief 限定范围并保留现有全部改动；1.4b 的评估同时对照本轮新增 diff 与最终集成后的功能行为。每个模型的 1.4a 用量封账后，从本轮后续实际 run ID 建立独立 1.4b usage 清单，不复用或重复累加 1.4a 总量。继续分别记录首次交付遗漏、自检/修复次数、审查退回数、验收首次通过情况和未验证项。

## 1.4a 实际结果

结论：限定代码验收通过，未提交、推送、合并或部署。DeepSeek 一次主体交付，内部自检修正/补测 4 次；Astra 在送审前完成两项小修；Grok 首次完整独立审查通过，未发生审查退回后的返工。没有切换模型，没有新增专门返工角色。

Astra 亲跑全量 139 pass / 0 fail / 561 assertions / 16 files，类型检查和构建通过；Grok 亲跑角色卡 13 pass / 0 fail / 78 assertions。两项编排者收尾修正是：去掉合同外新增的名称/描述长度限制；批量图片主图 ID 失效时对齐现有画布的取图逻辑，补回归。它们说明一次主体交付仍有遗漏，不记为 DeepSeek 原样首次通过。

页面按用户本轮明确要求跳过；React/DOM、实际工具栏/弹窗/引用操作、页面保存重开、真实云恢复、供应商与计费未测。临时本地服务、依赖软链和构建产物已清理。逐项证据见功能 brief 与 `/tmp/pilipala-14a-acceptance/grok-review-01.md`。

用量采样于 `2026-11-07T04:43:09.414096+00:00`，从 1.4a 规划起计，包含编排复核、用量审计子代理、实现自检和独立审查，排除 1.3 与网络排查。Astra 当前收尾回合未结束，因此此项为截至采样的部分统计，不含采样之后及最终回复的消耗；DeepSeek 与 Grok 均为已完成运行。

| 模型 | 输入 M | 输出 M | reasoning M | 缓存读取 M | 缓存写入 M | 原始总 M |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| GPT-6 Astra ultra | 11.255520 | 0.039238 | 0.009418 | 10.121684 | 0.000000 | 11.294758 |
| DeepSeek V4.1 Flash high | 0.154228 | 0.022253 | 0.020406 | 5.301376 | 0.000000 | 5.498263 |
| Grok 4.7 high（Cursor 原始口径） | 4.729070 | 0.044277 | 0.035448 | 4.564480 | 0.000000 | 9.337827 |

`M = 1,000,000 tokens`。Codex 的输入已含缓存、输出已含 reasoning，不重复相加。OpenCode 原始 total 包含输入、输出、reasoning、缓存读写。Cursor 原始 total 为输入 + 输出 + 缓存读写，reasoning 单列；其输入与缓存的语义尚未独立确认与其他渠道一致，故不合计三家作为统一口径或费用排名。原始整数、逐响应/消息/运行去重记录保存在 `/tmp/pilipala-14a-usage/`；未提供 usage 的请求不能估算或按零计。

## 1.4b 实际结果（代码验收通过）

结论：Grok 4.7 high 独立代码验收通过，没有代码阻塞；Astra 复核与自动检查通过。未提交、推送、合并或部署。实现接在既有「多角度」弹窗与 `generateAngleNode` 请求/上传/节点收尾路径内：三视图一张排版图、一次 `requestEdit`（`count: "1"`）、一次上传、一个普通图片节点，不自动切图；单视角保持原参数和提示词。没有新增模型、API、任务、角色表、引用注入或计费规则。1.4a 的未提交工作区改动完整保留。

实施链路：Astra ultra 编排 → DeepSeek V4.1 Flash high 实现 → Grok 4.7 high 只读验收 → Astra 复核。DeepSeek 实现期间自修 2 次，都是测试接线问题；没有需求返工。Astra 增加了一条断言确认三视图结果可被现有 `sourceNodeReferenceImages` 读取。Grok 首次独立审查通过，没有退回修复。

验证证据：DeepSeek 初次全量测试 148 pass / 0 fail / 653 assertions / 17 files，聚焦测试 9 pass / 92 assertions；Astra 加上引用读取断言后，亲自重跑全量测试为 148 pass / 0 fail / 654 assertions / 17 files，`bun run typecheck`、`bun run build` 和 `git diff --check` 均通过。Grok 按送审指纹复核一致，亲跑聚焦测试 9 pass / 0 fail / 93 assertions 与 `git diff --check`。测试用受控 Promise stub 调用生产 `generateAngleNode` 回调；未发真实供应商请求或计费。

页面检查改用用户指定的外置 Edge 可见窗口完成部分验证：`http://127.0.0.1:3104/canvas/-1z0i-jqiNOMan6m2tc9q` 中打开图片节点的多角度弹窗，切换到三视图后确认模式选择、角度设置隐藏、说明文案及生成按钮可见；没有点击生成。为暴露隐藏的多角度快捷工具，测试时临时启用后已恢复为原关闭设置。Chrome 连接本身仍未恢复。Edge 画布库原为空，验收过程中用户加了一张图片；本地测试画布与图片按用户要求保留。已验证的页面范围不包括浅色主题、生成后布局、失败/取消的页面反馈、云恢复、供应商结果与计费；这些仍未验证。`3104` Vite 服务保持运行供用户继续查看，`web/node_modules` 保留为指向主工作区依赖的临时符号链接；构建产物和类型缓存已清理。

1.4b 基线为同分支 1.4a 未提交工作区。开始时 tracked diff SHA-256 `e4d2518bb4a676c8e9062c24408b9e24acdab8059a33595f85197e34e2065ea4`；送 Grok 时 HEAD `5c0aa869455174300ebc38ba06b12af1f7469b6c`、tracked diff `e80e6f7d95078d50fa5cf25365d6032219312ba7b1f09b3ea87590aae27f8612`、全部未跟踪文件聚合 `a40cbc08ee5211f91e6f422d3b3672edcbd05f1a2a6e45e1085c3fcfd73ad30c`，复核结束后均未变化。

1.4b 本轮代码/测试文件：`web/src/components/canvas/canvas-node-angle-dialog.tsx`、`web/src/lib/canvas/canvas-generation-helpers.ts`、`web/src/pages/canvas/project.tsx`、`web/src/i18n/locales/zh-CN.ts`、`web/src/i18n/locales/en-US.ts`、`web/tests/canvas-angle-three-view.test.ts`。相关版本/验收文档：`CHANGELOG.md`、`docs/content/docs/progress/pending-test.mdx`、`docs/engineering/ROADMAP.md`、`docs/engineering/feature-briefs/2026-11-07-character-three-view.md`、本文件。画布页面、locale 和文档同时保留 1.4a 改动。

### 1.4b 用量

用量范围从 `/tmp/pilipala-14b-usage/manifest.json` 的根线程第 75 轮开始，包含本轮 Astra 编排/复核以及对应 DeepSeek、Grok 运行；排除 1.4a、1.3 与网络排查。`M = 1,000,000 tokens`。Codex 输入含缓存且输出含 reasoning；OpenCode total 含 input/output/reasoning/cache；Cursor total 是 SDK input/output/cache read/write，reasoning 单列。不同渠道缓存口径未确认相同，不合并为统一消费或成本排名。

| 模型 | 输入 M | 输出 M | reasoning M | 缓存读取 M | 缓存写入 M | 原始总 M | 覆盖 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| GPT-6 Astra ultra | 22.094927 | 0.114221 | 0.082483 | 21.110528 | 0.000000 | 22.209148 | 138 条响应；采样时当前轮仍在运行，部分统计 |
| DeepSeek V4.1 Flash high | 0.120306 | 0.017657 | 0.017346 | 4.446336 | 0.000000 | 4.601645 | 完成会话 |
| Grok 4.7 high（Cursor 原始口径） | 4.729070 | 0.044277 | 0.035448 | 4.564480 | 0.000000 | 9.337827 | 完成审查运行 |

Astra 用量采样于 `2026-11-07T09:58:29.499689Z`，含该时点之前的 138 条响应；此时当前轮仍运行，最终回复和采样后的收尾不在其中。OpenCode 与 Cursor totals 按各自原生口径保留。原始记录和 session/run ID 在 `/tmp/pilipala-14b-usage/`，Grok 代码报告在 `/tmp/pilipala-14b-acceptance/grok-review-01.md`。
