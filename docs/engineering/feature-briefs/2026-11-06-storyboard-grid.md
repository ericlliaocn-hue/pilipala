# Feature brief：文本节点一键生成 9 / 25 宫格分镜（ROADMAP 1.3）

- 日期：2026-11-06
- 作者：pilipala engineering（Claude）
- 对应 WP / 里程碑：M6 / ROADMAP 1.3
- 状态：本轮代码验收通过；浏览器由用户要求跳过，真实出图与计费未验证；修复已提交 `4c0778d`，未合并

## 背景

短剧创作的第一步是把一段剧情拆成多个镜头。LibTV 等竞品的做法是「多机位 / 分镜宫格」：模型**一次**出一张 3×3（或 5×5）的宫格图，再切成单格当镜头参考。这样只计费一次、画风统一，比逐格单独出图便宜且一致性更好。

画布已有全部零件：文本节点、托管出图（`requestGeneration` / `requestEdit`，托管模式自动走 pilipala 任务与钱包）、按行列切图（`project.tsx` 的 `splitImageNode` + `lib/canvas/canvas-image-data.ts` 的 `splitDataUrl`）。本步只是把它们串成一个按钮。

## 目标

1. 文本节点悬浮工具栏新增「分镜宫格」按钮；文本为空时提示 `canvas.storyboard.empty` 并不发请求。
2. 点击弹出一个小弹窗：选择 `9 宫格（3×3）` 或 `25 宫格（5×5）`（默认 9），显示「将生成 1 张宫格图并自动切成 N 个镜头，按 1 次出图计费」，确认后开始。
3. 生成一张 1:1 宫格图，作为新图片节点放在文本节点右侧并连线（文本 → 宫格图）；生成期间该节点显示 loading，失败显示错误并可用现有「重试」。
4. 成功后自动调用现有切图逻辑，把宫格图切成 N 个镜头图片节点（与手动「切图」结果一致：子节点从宫格图连出）。
5. 文本节点若已连接上游参考图（例如角色图），生成时带上参考图（走 `requestEdit`），否则走 `requestGeneration`。

## 范围

只改 `web/`：

- 新增 `web/src/lib/canvas/canvas-storyboard.ts`：`buildStoryboardPrompt(script: string, size: 3 | 5): string`（纯函数）。
- 新增 `web/src/components/canvas/canvas-storyboard-dialog.tsx`：选择 3 / 5 的弹窗。
- 修改 `web/src/pages/canvas/project.tsx`：新增 `generateStoryboardGrid(node, size)`，接弹窗与工具栏。
- 本轮经用户确认增加 `web/src/lib/canvas/canvas-group-workflow.ts`、`web/src/types/canvas.ts` 及相关测试，仅用于下方修复合同中的分镜归属、保护与组重跑行为。
- 修改 `web/src/components/canvas/canvas-node-hover-toolbar.tsx`：文本节点多一个按钮（新增可选 prop `onStoryboard?`）。
- 修改 `web/src/i18n/locales/zh-CN.ts`、`en-US.ts`。
- 新增 `web/tests/canvas-storyboard.test.ts`。
- 修改 `CHANGELOG.md`（Unreleased 加一条 `[新增]`）、`docs/content/docs/progress/pending-test.mdx`。

## 非范围

- 不新增后端能力、不改计费、不改 `api/`、`worker/`、`admin/`。
- 不做逐格单独出图、不做自定义行列数（只有 3 和 5）、不做分镜文字说明节点、不做剧本拆解（剧本节点是 ROADMAP 1.2）。
- 不新增任何超时、重试、并发上限；不改 `splitDataUrl` / `splitImageNode` 的切图算法。
- 不重构 `project.tsx` 其他部分。

## 实现要求

### 1. `buildStoryboardPrompt`

返回中文提示词，固定结构（`{N}` = size，`{TOTAL}` = size²）：

```
生成一张 {N}×{N} 的电影分镜宫格图，共 {TOTAL} 格，所有格子等宽等高、紧密排列成正方形网格，格子之间不要留白边、不要分隔线、不要编号和文字。
从左到右、从上到下依次表现下面剧情中的连续镜头，景别和机位要有变化（远景、中景、近景、特写交替），同一角色在所有格子中的外貌、服装和画风保持一致。

剧情：
{script}
```

`script` 先 `trim()`。

### 2. `generateStoryboardGrid(node, size)`（`project.tsx`）

照现有 `handleGenerateNode` 的前置校验与请求方式写，不要复制无关分支：

1. `buildGenerationConfig(effectiveConfig, node, "image")`，并覆盖 `size: "1:1"`、`count: "1"`。
2. 未登录且 `channelMode === "remote"` → `setAuthModalOpen(true)` 后返回；`!isAiConfigReady(...)` → `openConfigDialog(true)` 后返回。
3. 用 `createCanvasNode(CanvasNodeType.Image, 位置)` 在文本节点右侧（`x = node.position.x + node.width + 96`，与 `generateImageFromTextNode` 相同的摆放方式）建一个 loading 状态的图片节点，标题 `t("canvas.storyboard.gridTitle", { count: size * size })`，并连线 文本 → 宫格图。
4. `hydrateNodeGenerationContext(buildNodeGenerationContext(node.id, nodesRef.current, connectionsRef.current, buildStoryboardPrompt(文本内容, size)))` 取上下文；有 `referenceImages` 用 `requestEdit`，否则 `requestGeneration`；都传 `{ signal: controller.signal }`，`controller` 用现有 `startGenerationRequest(gridId, node.id, gridId)` 拿，结束时 `finishGenerationRequest`。
5. 成功：`uploadImage` → 按 `fitNodeSize` 设置节点尺寸与 `imageMetadata(...)`、`buildImageGenerationMetadata(有参考图 ? "edit" : "generation", generationConfig, 1, referenceImages)`、`prompt`；然后用更新后的节点调用现有 `splitImageNode(gridNode, { rows: size, columns: size })`。
6. 失败：`isGenerationCanceled` 直接返回；否则 `message.error` 并把节点置为 `NODE_STATUS_ERROR` + `errorDetails`（与 `handleRetryNode` 失败分支一致）。
7. 文本内容取 `node.metadata?.content || node.metadata?.prompt`，为空时 `message.warning(t("canvas.storyboard.empty"))` 返回（在打开弹窗前就检查）。

### 3. 弹窗

- antd `Modal` + `Segmented`（选项 9 / 25），遵守 `AGENTS.md「画布 UI 规范」`：颜色走 `canvasThemes` / `useThemeStore`，参照 `canvas-node-split-dialog.tsx` 的视觉风格。
- 文案显示 `t("canvas.storyboard.hint", { count })`；确定按钮 `t("canvas.storyboard.confirm")`。
- 弹窗开关状态放在 `project.tsx`（与 `splitNodeId` 同样的写法：`storyboardNodeId`）。

### 4. 悬浮工具栏

文本节点（`isText`）且传了 `onStoryboard` 时，在 `generateImage` 按钮后加 `{ id: "storyboard", title: t("canvas.storyboard.title"), label: t("canvas.storyboard.label"), icon: <LayoutGrid className="size-4" /> }`（`lucide-react`）。

### 5. 文案（zh-CN / en-US）

新增 `canvas.storyboard`：`title`（生成分镜宫格）、`label`（分镜宫格）、`empty`（文本为空，无法生成分镜）、`nine`（9 宫格）、`twentyFive`（25 宫格）、`hint`（将生成 1 张宫格图并自动切成 {{count}} 个镜头，按 1 次出图计费。）、`confirm`（生成分镜）、`gridTitle`（分镜宫格 {{count}}）。英文自行翻译。

## 数据表字段

无。

## API

无（复用现有出图接口与托管任务）。

## 状态机

无新状态；宫格图节点沿用 `loading / success / error`。

## 本轮代码验收结果

Astra 已关闭四项确认缺陷：取消/删除/替换后晚返回、上传期间父布局变化、复制无效父归属、删除来源文本连带取消宫格。Grok 4.7 最终复验通过，没有报告阻断本轮代码收尾的缺陷。

Astra 亲自执行全量测试 126 pass / 0 fail、类型检查和构建，均通过；Grok 亲自复跑实际回调入口测试 25 pass / 0 fail。目标 worktree、指纹、交付次数及验证限制见 [编排试验记录](../orchestration-trial.md#astra-本轮实际结果)。

该结论仅覆盖本轮代码合同。浏览器按用户要求跳过；真实出图、计费、云同步、React/DOM 与图片解码未验证；入口测试也没有执行首次生成带参考图分支（已覆盖重试 edit 分支）。以下完整清单保留未验证状态，不把自动回调测试冒充端到端通过。

## 验收

### 本轮已确认的修复合同

用户已明确要求先由 Astra 编排完成 1.3 修复与完整验收，再比较 Terra；以下共享行为与保护策略已经确认，不需重复询问。

- 宫格重跑只刷新图片/生成提示词未被用户修改且仍与宫格同组的自动镜头；双方均未分组视为同范围。改过或移出组的镜头保留为独立图片，解除自动归属，缺格生成新镜头。原标题、位置、尺寸、普通参考连线与下游连线保留；新镜头归入当前宫格所在组。独立图片后续遵守既有普通组重跑规则，不能在本次重跑中解除归属后又立即被纳入执行。
- 以最近一次自动切图的稳定素材标识与生成提示词识别用户改图，不能把刷新生成的新 blob URL 误判为改图。缺少判断依据的节点不静默覆盖；类型变成视频/音频、非法或重复 cell 归属也保留用户内容，不任意选一份覆盖。只做本轮必要的归属解除与补格，不做历史存储迁移。
- 单镜头复制不带自动归属；完整父子或部分父子粘贴时，只按本次快照完整 idMap 映射父 ID。快照缺少有效父时解除副本归属，原有参考入边保持。源画布父已删除不影响完整快照或模板在空画布创建新父。禁止原地改源 metadata、剪贴板或模板。多父同 index、多次粘贴必须隔离。
- 删除宫格取消其未完成请求，留下的镜头解除自动归属；普通删除语义保持。取消或新请求替换旧请求后，旧请求不得再落节点、连线、成功提示或清掉新请求 running 状态。图片已生成而切图取消/失败时保留宫格图，不重复出图；错误与取消不冒充镜头成功。
- 首次生成及重试成功只在当前节点上补生成结果，保留期间移动、改名、分组和用户编辑；新增镜头按当前宫格位置/分组布局，不使用创建时的过期快照。父节点不存在时不提交。
- 组重跑确认文案列出宫格生成及将刷新/保留/补充的镜头范围，不能宣称只影响一个节点却静默覆盖组外图片。成功判断必须反映本次请求与切图结果，不能把“有旧图所以 status=success”当本次成功。单次组任务的目标固定，不重新吸纳本次解除归属的镜头。
- 手动切图仍为独立图片，不自动认领手动结果；如已有手动图片，后续自动补切也不得覆盖它们。失败重试使用保存请求的提示词/模型/参考图；用户改文本后点分镜生成是新任务，不把两种动作混同。

允许在既有未提交实现基础上修改：`web/src/pages/canvas/project.tsx`（上述相关入口）、`web/src/lib/canvas/canvas-storyboard.ts`、`web/src/lib/canvas/canvas-group-workflow.ts`、`web/src/types/canvas.ts`、必要 `zh-CN.ts` / `en-US.ts` 文案及 `web/tests/` 相关测试。允许使用现有画布 UI 和取消/重试入口，不新增并行请求/状态管理框架，不顺手重构，不改 API/计费/配置/依赖锁/超时或并发阈值。切图算法本身保持。收尾仅更新已有 CHANGELOG / pending-test 的相关条目。

实际基线：worktree `/Users/eric/.t3/worktrees/pilipala/t3-879d4719`，HEAD `542fd6f76a0fa8e8e69a9d744910f3791cb24d44`，起始 diff SHA-256 `3ed7ae24abd99e5f4cec93ec9366ce1af969c6baea19759a0cff4e353faa971b`。保留全部已有改动；修复阶段不提交、不推送、不合并、不发真实计费请求。验收后用户已授权提交并推送 1.3，修复提交为 `4c0778d`，目标分支 `ljx/fix/storyboard-grid-lifecycle`；未授权扩大到真实计费或部署。

交付时至少运行 `bun run typecheck`、`bun test`、`bun run build`，并验证实际复制入口与异步编排，不能仅测试纯 helper 或 JSON 往返。失败/取消/删除/新旧请求竞争使用受控 Promise 与假请求，不请求真实供应商。浏览器只允许用户外置 Chrome 可见窗口；控制连接失败且重试未恢复后，用户明确要求本轮跳过浏览器验收。页面项记录“用户要求跳过，未验证”，不以自动测试或其他浏览器替代，不再作为本轮等待条件。实现完成后 Grok 对照整份 brief 验收，Astra 亲自复核；其他必需项未测不写通过，最终结论必须注明浏览器和真实计费链路的验证限制。

以下是完整链路验收，尚未因以往局部审查通过而勾选。共享删除、复制、模板和组重跑按上方已确认合同处理；新增超出合同的共享行为仍须先确认。

- [ ] 3×3 / 5×5：成功后分别生成 9 / 25 个对应镜头，宫格和镜头连线正确；仅发起一次出图请求。
- [ ] 出图失败后重试：保留提示词、模型、1:1、参考图和宫格尺寸；成功后自动切图，刷新后仍可重试。
- [ ] 失败宫格连接普通节点后重试：普通连线不影响自动切图，原有节点和连线保留。
- [ ] 宫格和镜头参与组重跑、镜头被编辑或部分删除时：按事先确认的策略处理，不静默覆盖用户内容、不产生新旧图混用或多余扣费。
- [ ] 复制单个镜头、多选复制粘贴、保存并插入组模板后：宫格与镜头归属在新旧节点间正确隔离；同一宫格存在重复格子归属时不可静默选中一份并遗留旧图。
- [ ] 出图过程中移动、改名或分组宫格：结果完成后保留用户的最新修改，切出的镜头与当前宫格位置及连线一致。
- [ ] 删除宫格后保留镜头、将镜头移出组或对失败切图手动切分后重跑：按事先确认的归属和重跑策略处理，不静默改写组外或用户编辑的图片。
- [ ] 区分失败后重试原请求与用户修改源文本后重新生成，分别按明确的提示词来源执行。
- [ ] 出图及切图期间删除、取消或发起新请求：不落孤立镜头或无效连线，不误清其他请求状态；需验证实际异步交互而非仅检查辅助函数。
- [ ] 出图成功、切图失败：保留宫格图并清楚告知结果；不声称镜头生成成功，也不重复出图扣费。
- [ ] 普通生图、手动切图及组重跑的既有行为不回归；涉及共享行为的修改先取得确认。
- [ ] 以上每项记录自动测试或外置 Chrome 可见窗口的验证结果；真实出图与参考图链路未执行时明确列为未验证。

- [ ] `web/tests/canvas-storyboard.test.ts`：size=3 时提示词含 `3×3`、`共 9 格` 和 trim 后的剧情；size=5 时含 `5×5`、`共 25 格`。
- [ ] `cd web && bun run typecheck && bun test && bun run build` 全部通过（本地 bun 旧导致 `--frozen-lockfile` 失败时用已有 node_modules 跑，不要提交 bun.lock 改动）。
- [ ] 文本节点工具栏出现「分镜宫格」；空文本不发请求；未登录托管模式打开登录弹层而不是报错。
- [ ] 原有「生图」「切图」行为不变。
- [ ] `git diff` 只包含「范围」内文件。

## 非目标

- 分镜宫格不是剧本拆解；不解析剧情结构、不生成镜头描述文字。

## 给实现者的额外约束

- 先读 `AGENTS.md` 和本文件；严格按本文件，没写的不要加。
- 代码保持短小；复用 `splitImageNode`、`buildGenerationConfig`、`startGenerationRequest` 等现有函数，不要复制实现。
- 本任务**要求**本地跑验收命令自检，并把每条命令结果贴进最终汇报。
- 本轮保留未提交改动，**不提交、不推送、不合并、不开 PR**；后续操作按用户授权进行。
- 最终汇报：分支名、基线 HEAD 与最终 diff、实际改动文件、逐项自检结果、未验证与阻塞，不把局部复验升级为整项通过。
