# Feature brief：画布组保存为模板 + 组一键重跑（ROADMAP 1.6）

- 日期：2026-11-06
- 作者：pilipala engineering（Claude）
- 对应 WP / 里程碑：M6 / ROADMAP 1.6
- 状态：已验收

## 背景

画布已经能把多个节点「打组」（`CanvasNodeType.Group`，成员节点 `metadata.groupId` 指向组节点；`web/src/lib/canvas/canvas-node-geometry.ts` 的 `applyGroupSelection` / `applyUngroupSelection`）。短剧创作里一组节点通常就是一条可复用的流程（例如「角色描述 → 配置节点 → 出图 → 出视频」）。现在缺两件事：

1. 组不能存下来，下一个项目要重新搭。
2. 组里的生成节点只能一个一个点「重新生成」（`project.tsx` 的 `handleRetryNode`）。

## 目标

1. 组节点悬浮工具栏出现「存为模板」，点击后把该组（组节点 + 成员节点 + 成员之间的连线）存为本地模板，并提示成功。
2. 画布左侧面板新增「模板」标签页，列出已存模板（名称、节点数、保存时间）；点击一条模板，把它插入到当前画布视口中心；每条可删除（带确认）。
3. 组节点悬浮工具栏出现「一键重跑」，确认后按依赖顺序**依次**重新生成组内所有生成类节点；每个节点复用现有 `handleRetryNode` 的行为（含计费、错误提示、视频任务恢复）。
4. 插入的模板与粘贴行为一致：全部节点 / 连线换新 id，成员 `groupId` 指向新组节点。
5. 模板引用的本地图片 / 媒体不会被「未使用文件清理」删掉。

## 范围

只改 `web/`：

- 新增 `web/src/lib/canvas/canvas-group-workflow.ts`（纯函数，见下）。
- 新增 `web/src/stores/canvas/use-canvas-template-store.ts`。
- 修改 `web/src/stores/use-asset-store.ts`：清理函数把模板也算作「在用」。
- 修改 `web/src/pages/canvas/project.tsx`：存模板、插入模板、一键重跑三个动作，并接到工具栏与左侧面板。
- 修改 `web/src/components/canvas/canvas-node-hover-toolbar.tsx`：组节点多两个按钮。
- 修改 `web/src/components/canvas/canvas-side-panel.tsx`：新增「模板」标签页。
- 修改 `web/src/i18n/locales/zh-CN.ts`、`en-US.ts`：新增文案。
- 新增 `web/tests/canvas-group-workflow.test.ts`。
- 修改 `CHANGELOG.md`（Unreleased 加一条 `[新增]`）、`docs/content/docs/progress/pending-test.mdx`（加一条待测说明）。

## 非范围

- 不做云端同步模板、不做模板分享 / 广场 / 「克隆同款」（那是 ROADMAP 2.2）。
- 不做模板重命名、编辑、排序、标签。
- 不做并发重跑，不新增任何超时、重试次数、并发上限。
- 不改 `handleRetryNode` 内部逻辑、不改计费、不改 `api/`、`worker/`、`admin/`。
- 不重构 `project.tsx` 其他部分；除下面明确要求的 `pasteCopiedNodes` 抽取外不动现有函数。

## 实现要求

### 1. `canvas-group-workflow.ts`

```ts
export type CanvasWorkflowSnapshot = { nodes: CanvasNodeData[]; connections: CanvasConnection[] };

// 组节点 + metadata.groupId === groupId 的成员；连线只保留两端都在集合内的。
// 深拷贝 position / metadata（与 copySelectedNodes 一致）；
// 清掉运行态：节点和 metadata.images[] 里 status === "loading" 的改为 "idle"，删除 errorDetails、videoTaskId。
// groupId 不存在或不是组节点时返回 null。
export function snapshotGroupWorkflow(groupId: string, nodes: CanvasNodeData[], connections: CanvasConnection[]): CanvasWorkflowSnapshot | null;

// 组内可重跑节点：成员中 type 为 image / video / audio / text，且
//   metadata.generationType 有值，或 findRetrySourceNode(node.id, nodes, connections) 能找到配置节点。
// 排序：按组内成员之间的连线做拓扑排序（上游在前）；同一层保持 nodes 数组原顺序；有环时剩余节点按原顺序追加。
export function collectGroupRerunNodes(groupId: string, nodes: CanvasNodeData[], connections: CanvasConnection[]): CanvasNodeData[];
```

`findRetrySourceNode` 从 `@/lib/canvas/canvas-generation-helpers` 引入，不要复制实现。

### 2. 模板 store

- 写法照抄 `web/src/stores/canvas/use-plugin-store.ts`：`zustand` + `persist` + `createJSONStorage(() => localForageStorage)`，`name: "pilipala:canvas_template_store"`。
- 类型：`CanvasTemplate = { id: string; title: string; nodes: CanvasNodeData[]; connections: CanvasConnection[]; createdAt: string }`。
- 动作：`addTemplate(title, snapshot): string`（id 用 `nanoid()`，新模板放最前）、`removeTemplate(id)`。

### 3. 图片清理

`use-asset-store.ts` 里调用 `cleanupUnusedImages` / `cleanupUnusedMedia` 的对象增加 `templates: useCanvasTemplateStore.getState().templates`，其他不动。

### 4. `project.tsx`

- 把 `pasteCopiedNodes` 里「按剪贴板生成新节点 / 连线并插入到视口中心」的部分抽成同文件内的 `insertWorkflowSnapshot(snapshot: CanvasClipboard, titleSuffix: string)`；`pasteCopiedNodes` 调它并传 `" Copy"`，粘贴行为必须保持完全不变。插入模板时传 `""`。
- `saveGroupAsTemplate(node)`：`snapshotGroupWorkflow` → `addTemplate(node.title || t("canvas.template.defaultTitle"), snapshot)` → `message.success`。
- `insertTemplate(templateId)`：从 store 取模板 → `insertWorkflowSnapshot(template, "")`。
- `rerunGroup(node)`：`collectGroupRerunNodes`；为空则 `message.info(t("canvas.template.rerunEmpty"))`；否则 `modal.confirm`（文案说明将依次重新生成 N 个节点、每个节点单独计费），确认后 `for` 循环依次 `await handleRetryNode(最新节点)`（每轮从 `nodesRef.current` 取同 id 的最新节点；节点已被删除则跳过）。全部结束后 `message.success`。
- 未登录托管模式等前置校验交给 `handleRetryNode` 自己处理，不要重复写。

### 5. 悬浮工具栏

仿照现有 `ungroup` 按钮：组节点且传了回调时，在 `baseToolbarTools` 里增加 `saveTemplate`（图标 `lucide-react` 的 `LayoutTemplate`）和 `rerunGroup`（图标 `RefreshCw`）。新增可选 props `onSaveTemplate?`、`onRerunGroup?`。

### 6. 左侧面板「模板」标签

- `PanelTab` 增加 `"templates"`，标签按钮放在「提示词库」之后。
- 新组件 `CanvasTemplatesTab` 写在 `canvas-side-panel.tsx` 内，直接用 `useCanvasTemplateStore` 读模板（不要从 project.tsx 透传列表）；插入通过新增的 `onInsertTemplate: (templateId: string) => void` prop。
- 每行：`LayoutTemplate` 图标（无底色）+ 标题 + `{{count}} 个节点 · 日期`；点击整行插入；右侧删除按钮用 `App.useApp().modal.confirm` 二次确认。
- 空状态显示 `t("canvas.template.empty")`。
- 样式遵守 `AGENTS.md「画布 UI 规范」`：颜色走 `theme`（`canvasThemes`），按钮扁平透明、仅 `hover:bg-black/5 dark:hover:bg-white/10`，不要用 `theme.node.fill` / `theme.toolbar.activeBg` 做底色。

### 7. 文案（zh-CN / en-US 都要）

新增 `canvas.template`：`tab`（模板）、`saveTitle`（把这个组存为模板）、`save`（存模板）、`saved`（已存为模板）、`defaultTitle`（未命名模板）、`empty`（暂无模板，选中一个组点「存模板」）、`nodeCount`（{{count}} 个节点）、`inserted`（已插入模板）、`removeTitle`（删除该模板？）、`removed`（模板已删除）、`rerunTitle`（依次重新生成组内节点）、`rerun`（一键重跑）、`rerunConfirm`（将依次重新生成 {{count}} 个节点，每个节点单独计费。）、`rerunEmpty`（组内没有可重新生成的节点）、`rerunDone`（组内节点已重新生成）。英文自行翻译。

## 数据表字段

无（仅浏览器本地 localforage）。

## API

无。

## 状态机

无新状态；重跑沿用节点现有 `idle / loading / success / error`。

## 验收

- [ ] `web/tests/canvas-group-workflow.test.ts` 覆盖：快照只含组与成员、跨组连线被丢弃、loading 被重置、非组 id 返回 null；重跑集合排除配置节点和纯手写文本节点、按连线上游优先、有环不死循环。
- [ ] `cd web && bun run typecheck && bun test && bun run build` 全部通过（CI 用 bun 1.4.4，本地若 `bun install --frozen-lockfile` 报锁文件版本错误，说明本地 bun 太旧，用已装好的 node_modules 直接跑即可，不要改 bun.lock）。
- [ ] 粘贴（Ctrl/Cmd+V）行为不变。
- [ ] `git diff` 只包含「范围」内文件。

## 非目标

- 模板不是作品，不进素材库，不参与项目云同步。

## 给实现者的额外约束

- 先读 `AGENTS.md` 和本文件；严格按本文件，文件里没写的东西不要加。
- 代码保持短小，不新增只做转发的组件，不拆额外文件。
- 本任务**要求**你本地跑验收命令自检，并把每条命令结果贴进最终汇报。
- 在当前分支提交一个 commit（中文信息：`feat(canvas): 组存为模板与一键重跑（ROADMAP 1.6）`，不加署名行）。**不要 push、不要 merge、不要开 PR**。
- 最终汇报：分支名、commit SHA、改动文件清单、每条自检命令结果。
