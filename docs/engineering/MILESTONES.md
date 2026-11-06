# 里程碑 M0–M8

本页是已对齐的阶段目标与退出标准。工作包拆分见 [DEVELOPMENT.md](./DEVELOPMENT.md)。未写进退出标准的能力，默认不算该里程碑完成。

当前仓库已包含 `web/`、`api/`、`worker/`、`admin/` 与 WP0–WP21、admin-console 的实现，M0–M4 已基本完成。M5–M8 是面向 AI 短剧 / 漫剧的竞争路线（见 [VISION.md](./VISION.md)），逐步进度见 [ROADMAP.md](./ROADMAP.md)。

## 总览

| 里程碑 | 主题 | 主要工作包 | 用户能感知的结果 |
| --- | --- | --- | --- |
| M0 | 身份与工程纪律 | WP0 + 本文档树 | 产品叫 pilipala；大改动必须先有 brief |
| M1 | 平台骨架 | WP1–WP5 | 登录后能提交任务；worker 只处理 `job_id` |
| M2 | 付费出图闭环 | WP6–WP9 | 预付费生图成功扣费、失败退冻；错误友好 |
| M3 | C 端最小产品 | WP10 | 画布/素材/生成记录能跟账号走 |
| M4 | 运维面 | WP11、admin-console | 运营能配置与排障，而不是多一个创作产品 |
| M5 | 可合规运营 | P0（ROADMAP） | 生成内容带 AI 标识、违规内容被拦截、发布走 CI |
| M6 | 短剧创作核心 | P1（ROADMAP） | 剧本、角色、分镜、镜头、配音在画布上成链；模型覆盖短剧所需 |
| M7 | 托管 Agent 与模板 | P2（ROADMAP） | 一段剧本自动排出分镜与镜头；工作流可存模板、克隆同款 |
| M8 | 生态 | P3（ROADMAP） | 模板广场、小团队协作、开放 API 与 Agent Skill |

## M0 — 身份与工程纪律

**目标**：把 fork 收成 pilipala 产品仓库，并锁住后续协作方式。

**必须完成**

- 用户可见品牌改为 pilipala（标题、logo、主文案）。详见 [WP0 brief](./feature-briefs/2026-10-24-wp0-white-label-pilipala.md)。
- `docs/engineering/` 成为架构、里程碑、ADR、brief 的唯一入口。
- 大改动强制：feature brief → 实现 → PR 对照 brief review。

**退出标准**

- 打开 `web/` 开发页，浏览器标题与主界面不再把「无限画布 / Infinite Canvas」当产品名。
- 根 README 顶部有 pilipala 说明，并链到本目录。
- `AGENTS.md` 要求 agent 先读本目录，大改动先写 brief。
- 公开文档站点（`docs/content/`、Next.js、Dockerfile）没有被掏空或改成工程站。

**明确不做**

- 不在 M0 引入 Java API、队列、计费。
- 不重写上游功能说明与赞助墙全文；只加 pilipala 入口。

## M1 — 平台骨架

**目标**：业务 API + 瘦 worker + Anytoken hop 能跑通一条「假任务也算任务」的状态机。

**必须完成**

- Java 21 + Spring Boot 3 业务 API；Sa-Token 登录。
- Postgres / Redis 落地；job / job_step / outbox / idempotency 表存在，字段级见 [data-model](./data-model/)（WP2 已锁定）。
- RabbitMQ 作为第一期队列；消息体只有 `job_id`。
- Worker 按 [ADR 0004](./adr/0004-thin-worker-no-god-classes.md) 保持瘦：不拉画布、不编排创作、不持有钱包。
- 只通过受控网关访问模型（当时为 Anytoken；后经 ADR 0008/0009 增加 fork NewAPI 视频网关）；禁止双账本。

**退出标准**

- 已登录用户可以 `submit` 一条任务并 `query` 到 `queued → running → succeeded | failed`。
- 失败且 `attempt < max` 时回到 `queued`；超过后进入 `dead`（DLQ）。
- 取消路径能到 `cancelled`。
- 日志里看得到上游细节；查询接口对用户不回传原始供应商错误串。
- 代码审查能指出：没有 `TaskCenterService` 式 3k 行上帝类，没有 worker 依赖画布模块。

**明确不做**

- 不接真实扣费（那是 M2）。
- 不做 admin。
- 不要求画布云端持久化。

## M2 — 付费出图闭环

**目标**：预付费用户在画布上出一张图，账对、文件在、错误可读。

**必须完成**

- 钱包：提交冻结 → 成功确认 → 失败/取消释放。
- 前端托管模式改为打 pilipala API，不再把供应商 Key 放在浏览器里作为正式路径。
- Worker 调 Anytoken 真实生图；结果进火山 TOS。
- 用户可见错误：稳定 `error_code` + 友好文案；上游原文只在日志 / `provider_task`。
- 动态运营文案走 `i18n_message`；静态 UI 仍走前端 locale。先做 zh + en。

**退出标准**

- 余额不足：冻结失败，不创建可执行任务（或创建后立即失败且不入队执行），用户看到稳定错误码。
- 出图成功：TOS 可取回；钱包冻结转为确认；任务 `succeeded`。
- 上游失败或用户取消：冻结释放；任务 `failed` / `cancelled` / `dead` 符合状态机。
- 同一 `idempotency_key` 不重复扣两笔冻。
- 画布上至少一条官方生图路径走新链路。

**明确不做**

- 不铺视频/音频全模态（除非同一套 job 合同零成本捎带；默认只锁生图）。
- 不做社区、不做作品广场。
- 不做完整 admin。

## M3 — C 端最小产品

**目标**：C 端可以当「托管 pilipala」用，而不只是「能出一张图的演示」。

**必须完成**

- 画布项目、素材、生成记录与账号绑定并可在多设备取回（具体表字段在后续 brief 里定，不在本页锁死）。
- 前端不再把「仅本地 IndexedDB」写成已支持云同步的完成态；以实际能力更新工程说明。
- zh / en 覆盖静态 UI 与动态错误。
- C 端最小路径不依赖 admin：默认模型与策略可用配置或环境注入。

**退出标准**

- 用户换浏览器登录后能看到自己的项目/素材/生成记录（范围以当时 brief 为准，但必须有账号级持久化，而不是纯本地）。
- 没有 admin 也能完成：注册/登录 → 充值或加额度（可先用运维脚本）→ 画布出图 → 再次打开仍在。
- 公开用户文档若提到存储，不得声称「已云同步」除非该能力已验收。

**明确不做**

- 不把 admin 做成 M3 的阻塞项。
- 不做第二套创作 UI。

## M4 — 运维面（Admin）

**目标**：让运营能配置和排障。Admin 是 ops/config，不是第二个 pilipala。

**必须完成**

- 用户/钱包只读与有限调整（加额、冻结排查）。
- 任务查询、重试/死信查看、`provider_task` 原文仅对内部角色可见。
- 模型/渠道展示配置、功能开关、`i18n_message` 维护。
- 不引入独立创作画布、不引入 C 端信息流。

**退出标准**

- 运营不连数据库也能：查用户、查任务、看失败原因、改一条动态文案、上下架一个模型展示项。
- 代码与 IA 上 admin 是「后台」，路由/权限与 C 端隔离。
- 没有把 C 端画布功能复制进 admin。

**明确不做**

- 不做运营自己的无限画布。
- 不做面向 C 的 CMS/社区。

## M5 — 可合规运营

**目标**：现有出图/出视频链路可以对公众收费运营。

**必须完成**

- 生成图片 / 视频带显式 AI 标识与隐式元数据标识（《人工智能生成合成内容标识办法》）。
- 提示词提交前审核、生成结果入库前审核；拦截有稳定 `error_code`，且不扣费。
- 发布走 CI：测试 + 构建 + 产物，替代手工 tgz；web 类型检查归零。

**退出标准**

- 任意一张托管生成的图片 / 视频能看到标识并读到元数据标识。
- 违规提示词被拦截，钱包无冻结残留；违规结果不进入用户可见列表。
- `main` 推 tag 能产出可部署产物。

**明确不做**：备案、商户号等线下资质（由运营方办理，工程只提供材料所需的技术说明）。

## M6 — 短剧创作核心

**目标**：一个创作者在画布上手工完成一集短剧的主要环节。

**必须完成**

- 模型覆盖：图片至少 2 家、视频至少 2 家（含 Seedance）、TTS 与配乐各 1 家，全部经网关。
- 剧本 / 分镜节点；9 / 25 宫格分镜批量出图。
- 角色 / 主体一致性：角色卡、三视图、参考注入。
- 首尾帧、画布内视频裁剪与拼接。
- 节点打组、保存、一键重跑。

**退出标准**

- 从一段剧本出发，手工操作即可在画布上产出带配音的多镜头成片并导出。

## M7 — 托管 Agent 与模板

**目标**：把 M6 的手工流程自动化、可复用。

**必须完成**

- 托管 Agent：剧本 → 角色 / 分镜 → 镜头节点自动排上画布；每步可人工接管，费用先报价后冻结。
- 工作流模板：保存、套用、「克隆同款」替换素材。

**退出标准**

- 一段剧本在一次确认后自动生成完整分镜与镜头，失败步骤可单独重跑且不重复扣费。

## M8 — 生态

**目标**：增长与留存。

- 模板 / 作品广场；小团队协作（组织租户、成员、评论、版本）；开放 API 与给外部 Agent 的 Skill 包。
- 进入 M8 前需重新评估范围并另写 brief。

## 跨里程碑不变式

这些条件从 M1 起就生效，后续里程碑不能破坏：

- 模型请求只经受控网关（Anytoken 存量 + fork NewAPI 视频网关），见 [ADR 0001](./adr/0001-anytoken-not-newapi-fork.md)、[ADR 0008](./adr/0008-gateway-layer-video-multi-channel.md)、[ADR 0009](./adr/0009-newapi-fork-video-gateway.md)；网关不拥有用户、额度与定价。
- 业务 API 是 Java 21 + Spring Boot 3，见 [ADR 0002](./adr/0002-java-spring-business-api.md)。
- 队列实现可换，job 模型不绑死 RabbitMQ，见 [ADR 0003](./adr/0003-rabbitmq-first-queue-agnostic-jobs.md)。
- Worker 禁止变胖，见 [ADR 0004](./adr/0004-thin-worker-no-god-classes.md)。
- 用户错误与上游原文分离，见 [ADR 0005](./adr/0005-friendly-errors-vs-raw-upstream.md)。
- 计费只有冻结 / 确认 / 释放，见 [data-model/billing.md](./data-model/billing.md)。
