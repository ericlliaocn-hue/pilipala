# Feature brief：1.4a 角色参考卡与显式引用

- 对应里程碑：M6 / ROADMAP 1.4 的第一个可验收切片。
- 状态：代码验收通过；本轮页面验收由用户明确要求跳过，真实服务未验证；未提交、推送或合并。
- 角色：GPT-6 Astra ultra 编排与复核；DeepSeek V4.1 Flash high 实现；Cursor Grok 4.7 high 独立验收。
- 基线：1.3 已推送的 `5c0aa869455174300ebc38ba06b12af1f7469b6c`；1.4 使用独立 `ljx/feature/character-reference-card` 分支，不覆盖主工作区未提交文档，不把本切片记为整个 1.4 完成。

## 业务结果与已确认决策

创作者从已有角色图片创建一张可复用的参考卡，填写角色名称及外貌、服装等描述，再将参考卡明确引用到生成配置中，让已有生成流程同时取得角色图片与描述。

本切片采用已有画布组承载参考卡：一个独立组，包含一张普通图片副本和一个描述文本节点。名称必填，描述选填；不调用模型生成描述。副本是创建当时图片的快照，后续编辑任一方都不联动修改另一方。组名与文本均保留角色名称，图片与文字仍使用普通节点编辑方式。

用户已明确确认以下两项，无需重复请求许可：

1. 接受上述普通组形式及独立快照规则；跨画布复用现有本地模板，不在本切片新建云端角色库。三视图安排为后续 1.4b，1.4 的整体目标仍包含三视图。
2. 同意在下述限定范围新增图片工具栏入口及页面私有弹窗；普通节点、生成请求、引用优先级、鉴权、计费、删除和模板行为沿用现有规则。

## 实际入口、成熟流程与依赖

代码证据基于上述提交；实施前核对实际分支和目标文件 diff。

- 路由：`web/src/router.tsx` 的 `/canvas/:id` → `web/src/pages/canvas/project.tsx`。
- 类似创建流程：`project.tsx` 的 `createImageReversePromptNodes` / `generateImageFromTextNode`，在画布创建普通节点与配置，不因创建操作直接发起生成请求。
- 工具入口：`canvas-node-hover-toolbar.tsx` 调用 `canvas-image-toolbar-tools.tsx` 的 `buildImageToolbarTools`。有图片时工具会被快捷配置筛选，不能仅向 `extraTools` 添加按钮。沿用既有工具注册与“更多”配置入口，不提升 `canvas-image-quick-tools-v7` 版本或重置用户选择。
- 参考读取：`canvas-resource-references.ts` 的 `getGenerationResourceNodes` / `getGroupResourceNodes`，以及 `canvas-node-generation.ts` 的 `buildNodeGenerationInputs` / `buildNodeGenerationContext`。组只展开直属资源成员，并按节点 ID 去重。
- 既有 Config 规则：非空 `composerContent` 使用 `@[node:…]` 明确选择资源；没有引用标记时不自动注入资源。其他既有生成路径继续按已连接资源读取。不得另写一套角色注入流程。
- 布局：复用已有节点工厂、图片比例逻辑和 `getGroupWrapRect`；新组包住自己的图片与文本，不能把原图或原分组收进去。
- 保存：`use-canvas-store.ts` 的画布快照沿用账号级云端保存及本地缓存；`use-canvas-template-store.ts` 的模板目前使用 localforage。模板插入沿用 `hydrateCanvasImages` 和 `insertWorkflowSnapshot`，不宣称模板已云同步。

## 允许修改范围

- `web/src/pages/canvas/project.tsx`：打开/提交角色卡弹窗，提交时按源节点 ID 读取最新状态，追加新组和成员，接入工具回调。
- `web/src/pages/canvas/components/canvas-character-card-dialog.tsx`：新增页面私有弹窗，复用画布主题和既有 Ant Design Modal/Form 写法。
- `web/src/lib/canvas/canvas-character-card.ts`：必要的小型节点构造与输入校验，不新增存储或生成状态机。
- `web/src/components/canvas/canvas-image-toolbar-tools.tsx`、`canvas-node-hover-toolbar.tsx`：注册并接通“创建角色卡”操作；兼容现有快捷工具选择。
- `web/src/i18n/locales/zh-CN.ts`、`en-US.ts`：本功能文案。
- `web/tests/canvas-character-card.test.ts`、必要的角色卡入口测试：验证用户路径和引用隔离，复用现有测试约定，不搭建新测试框架。
- 本 brief、`docs/engineering/ROADMAP.md`、`docs/engineering/orchestration-trial.md`、`CHANGELOG.md`、`docs/content/docs/progress/pending-test.mdx`；检查 `todo.mdx`，仅存在对应待办时迁移该项。

如实现必须改动其他共享文件，先给出具体证据和最小追加范围；不得顺手改存储、通用引用、模板、删除、生成或分镜生命周期。

## 行为合同

1. 图片工具注册“创建角色卡”。默认工具配置可见；已有自定义快捷配置保持原选择，新工具可从已有“更多”配置中开启。空图片不创建角色卡。
2. 弹窗显示角色名称与描述。名称 trim 后为空时阻止提交；描述允许为空，不静默生成或加入原图生成提示词。取消关闭不改变画布；再次打开不残留上一次其他角色的输入。
3. 提交时重新读取源图片。源节点已删除、类型已变化或没有有效图片内容时提示失败，不产生半张卡。批量图片节点使用当前画面展示的主图，副本不带整批候选结果。
4. 一次有效提交追加一个普通 Group、一个 Image、一个 Text；只选中新建组。图片保持原始比例，图片和文字分配到新组；原节点、原组和所有原连线不变，也不复制原图的参考连线。
5. 图片只沿用展示与素材需要的字段，例如 `content`、`storageKey`、原图尺寸、MIME、字节数，并设置普通成功素材状态。采用字段白名单，不浅拷贝整份 metadata；不能继承任务 ID、模型/供应商参数、生成类型、失败状态、批量候选、`storyboardParentId` / `storyboardCellIndex` / `storyboardGridSize` 或自动镜头身份。
6. 角色文本包含名称及用户描述，使用已有文本节点的数据格式。普通画布组包含角色图和描述后，通过已有参考选择/连线即可供生成上下文读取；Config 编辑器继续要求明确 `@` 引用。两张角色卡互不串入；同一节点重复引用沿用现有去重行为。
7. 创建、编辑和保存角色卡本身不发生成、图生图、上传或计费请求。后续用户主动点击生成时仍走既有准备、校验、参考解析与请求流程。
8. 源图删除后已创建角色卡仍保留自己的素材引用。角色卡解除分组、删除、复制、保存模板和插入模板均沿用普通节点/组的规则；不引入父子联动、专用角色 ID 或额外删除策略。

## 验收矩阵

交付记录逐项填“通过 / 失败 / 未验证”，附代码、命令结果或外置 Chrome URL。不得把 helper 单测、JSON 往返或编译通过写成实际页面验证。

| 场景 | 可观察结果 | 验证方式 |
| --- | --- | --- |
| 有效图片创建角色卡 | 恰好追加组、图、文三个节点；原节点/分组/连线不变；图片比例正确 | 节点构造与实际提交回调测试；外置 Chrome |
| 名称为空、取消、重新打开 | 空名称无法提交；取消不落节点；下一张卡无旧表单残留 | 入口测试与外置 Chrome |
| 弹窗打开后删除源图或清空图片 | 提交失败，无部分组或残留连线 | 实际提交回调测试 |
| 源图在分组、是自动分镜镜头、带生成历史或批量结果 | 只复制当前主图的素材字段；无旧任务/分镜归属/整批结果；原数据不变 | 带这些 metadata 的输入用例 |
| 编辑卡内图文、删除源节点 | 卡与原图独立；不覆盖其他角色或分镜镜头；剩余素材引用继续可用 | 入口与既有节点行为测试；外置 Chrome |
| 两个角色、未连接角色、重复引用 | 真实 `buildNodeGenerationContext` 只取得所选角色的图片/描述，去重遵循现有节点 ID | 调用 production 引用函数的行为测试 |
| Config 有 composer 内容且未写 `@`；写入 `@` 后生成上下文 | 前者不隐式注入，后者取到所引用组的图和文；已有输入优先级不变 | production 上下文函数测试；外置 Chrome 检查引用入口 |
| 创建期间服务调用 | 创建本身没有生成/上传/计费调用 | 提交入口中 spy/断言服务未调用 |
| 保存、重新打开、模板插入 | 复用普通快照/素材 hydrate；组 ID 重映射正确；不遗留原组或分镜归属 | 既有保存/模板函数接合测试；外置 Chrome 本地项目恢复 |
| 工具栏新配置与已有自定义配置 | 新用户能看到工具；旧选择不重置，能在更多配置启用；其他工具可用 | 工具定义与回调接线测试；外置 Chrome |
| 浅色/深色与既有生成、切图、模板功能 | 弹窗遵循当前画布主题；关联既有测试仍通过 | 外置 Chrome；全量测试、typecheck、build |

首次实施前记录本地服务的实际 worktree、端口与 `/canvas/:id` URL，再做页面验证。只操作外置 Chrome 的独立测试页面，不打断用户现有标签。1.3 的页面豁免不自动沿用到本切片。

## 非范围与验证边界

本切片不新增数据库表、API、节点类型、全局 store、模型通道、任务状态、计费规则、重试/超时/大小/并发限制。三视图、专用角色实体、云端角色库、身份锁定和自动联动更新不在本切片内；参考注入不等于保证供应商生成的角色完全一致。

自动检查使用 `cd web && bun test`、`bun run typecheck`、`bun run build`。不发真实生成或计费请求；真实供应商效果和跨账号/设备云恢复若未测，明确列为未验证，不能借代码验收宣称通过。提交、推送、合并和部署按当前授权分别记录。

## 用量记录与交付格式

从本切片的规划起记录运行 ID、模型、角色、开始/结束范围及可靠 usage 来源；实现、审查、返工和编排复核均纳入本切片。网络排查、1.3 和其他线程的用量不混入。

每次交付按模型列：输入 Mtoken、输出 Mtoken、缓存读取/写入 Mtoken、总 Mtoken、数据来源及完整性；`1 Mtoken = 1,000,000 tokens`。保留原始整数后换算，不从文本长度、运行时长或费用反推 token。输入是否已包含缓存、输出是否已包含 reasoning 以来源语义为准，禁止把子项重复加总；渠道口径不同时注明，不能直接假设可比。

报告另列实现与返工轮数、验收结果、实际改动文件和未验证项。拿不到的字段写“未提供”，采样覆盖不全写“部分统计”，不能记作 0 或冒充整点总量。用量记录只保留必要元数据，不收集密钥或提示词正文。

## 本轮验收记录

- DeepSeek 一次交付，交付内自检修正/补测 4 次；Astra 集中修正两项小问题（未经授权的输入长度限制、批量主图 ID 失效时与画布显示不一致），没有 Grok 退回后的实现返工。
- Astra 亲自执行最终代码全量测试：139 pass / 0 fail / 561 assertions / 16 files；typecheck、build 与 diff 检查通过。Grok 首次完整代码审查通过，并亲跑 13 个角色卡测试（78 assertions）。
- 代码/自动检查通过项：普通组图文创建、原内容保护、素材字段白名单、批量主图、空输入与源图缺失拒绝、引用隔离/去重、Config 显式引用、创建不调用服务、模板快照与 ID 重映射、工具配置保留。
- 未验证项：React/DOM 的取消/重开、外置 Chrome 工具栏与浅深色弹窗、真实图片解码与页面比例、引用操作、页面保存重开/素材 hydrate/模板插入、真实云恢复、真实供应商及计费。现有入口测试不等于这些页面路径已通过。
- 外置 Chrome 浏览器接口报 `Browser is not available: chrome`，原生窗口可读但导航和重连两次超时；用户随后明确要求本轮跳过页面验收。计划页面 `http://127.0.0.1:3104/canvas`，无已验证 URL，没有使用内置/headless 替代。
- 冻结审查指纹与逐项 Grok 证据位于 `/tmp/pilipala-14a-acceptance/review-baseline.json`、`grok-review-01.md`；编排者亲自核对报告与同一代码。收尾只更新工程记录，功能源码未再改变。
- 本地验收服务、临时 `web/node_modules` 软链与构建产物 `web/dist` 已清理；证据日志保留在 `/tmp/pilipala-14a-acceptance/` 与 `/tmp/pilipala-14a-implementation/`。
- `todo.mdx` 没有对应待办，无需迁移；`pending-test.mdx` 保留可供人工验证的功能项。尚未提交、推送、合并或部署，不将 1.4a 完成等同于整个 1.4 完成。
