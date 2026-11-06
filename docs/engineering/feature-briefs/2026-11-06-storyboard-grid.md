# Feature brief：文本节点一键生成 9 / 25 宫格分镜（ROADMAP 1.3）

- 日期：2026-11-06
- 作者：pilipala engineering（Claude）
- 对应 WP / 里程碑：M6 / ROADMAP 1.3
- 状态：已确认

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

## 验收

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
- 在当前分支提交一个 commit（中文：`feat(canvas): 文本节点一键生成分镜宫格（ROADMAP 1.3）`，不加署名行）。**不要 push、不要 merge、不要开 PR**。
- 最终汇报：分支名、commit SHA、改动文件清单、每条自检命令结果。
