# 数据模型

本目录锁 **表清单与字段级**。阶段 1（job / billing / errors / i18n / users）的列名、类型、约束、索引、枚举已由 [WP2 brief](../feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定，对应页是实现者的照抄源。

**迁移文件不在这里。** `db/migration/**` 由实现 WP 的 Flyway 落地，本目录不预写 SQL。

产品未上线：表结构按新设计改即可，不必写旧字段兼容，除非 brief 明确要求。

## 表清单（stage-1）

| 表 | 所属 | 首次落地 | 字段级 | 说明 |
| --- | --- | --- | --- | --- |
| `users` | 账号 | WP3 | [下方字段](#userswp3-已落地) | 登录主体；Sa-Token 会话指向 `user_id` |
| `wallet` / `wallet_hold` / `wallet_ledger` | 计费 | WP6 已落地 | [billing.md](./billing.md) | 余额 + 冻结单 + 流水；单账本 |
| `recharge_order` | 计费 | WP16 已落地（`V9`） | [billing.md](./billing.md) | z-pay 支付宝充值订单 |
| `job` | 任务 | WP4 已落地；WP21 增量列 | [jobs.md](./jobs.md) | 状态机权威行 |
| `job_step` | 任务 | WP4 已落地；WP21 增量列 | [jobs.md](./jobs.md) | 每次 attempt 一行 |
| `outbox_message` | 投递 | WP4 已落地 | [jobs.md](./jobs.md) | 事务内待投递，消息体指向 `job_id` |
| `idempotency_record` | 任务 | WP4 已落地 | [jobs.md](./jobs.md) | `user_id` + key → 已返回的 `job_id` |
| `provider_task` | 网关痕迹 | WP5 已落地；WP21 增量列 | [jobs.md](./jobs.md) | Anytoken / NewAPI / 上游原文，不对 C 端默认暴露 |
| `i18n_message` | 文案 | WP9 已落地 | [errors.md](./errors.md) | 动态运营 / 错误模板，zh + en |
| `app_runtime_setting` | 运营 | WP18 已落地（`V11`）；Studio chrome seed `V13` | [下方字段](#app_runtime_settingwp18-已落地) | 运行时开关与运营文案（邮箱通道、Studio 顶栏/活动条） |
| `error_code` | 错误 | — | [errors.md](./errors.md) | **不是表**，是错误码注册表（见该页） |
| `canvas_project` / `user_asset` | C 端数据 | WP10 已落地（`V6`） | [projects.md](./projects.md) | 画布快照与用户素材，绑定 `user_id` |
| `tenant` / `tenant_membership` / `role` / `permission` / `role_permission` / `role_assignment` | 租户与 RBAC | M4 已落地（`V24`） | [tenancy.md](./tenancy.md) | 租户边界与平台/租户权限；**钱包属于租户** |
| 模型展示配置 `model_display_config` | 运营 | WP11 已落地（`V7`） | [下方字段](#model_display_configwp11-已落地) | 只管前端展示与上下架，**不是**渠道密钥表、**不是**额度账本 |
| `model_product` / `model_product_group` | 视频路由 | WP21 Phase 2（`V19`/`V20`/`V21`） | [WP21 Phase 2 brief](../feature-briefs/2026-10-29-wp21-phase2-newapi-fork.md) | 产品 SKU 与 NewAPI group/model；**不是**渠道密钥表、**不是**额度账本 |

> **WP21**：`job` / `job_step` / `provider_task` 的增量列先以 [WP21 brief](../feature-briefs/2026-10-29-wp21-video-multi-channel-gateway.md) 为准；实现 PR 先更新 `jobs.md`，再写 Flyway。

**分析事件与报表口径**见 [analytics.md](./analytics.md)（WP15；Phase 1 无强制新表）。**余额与流水权威仍在** [billing.md](./billing.md)。

不建的表（非目标）：

- NewAPI 风格的 `tokens` / `quotas` / `channels` 用户额度镜像；NewAPI 的渠道表也不镜像进业务库，渠道配置留在网关
- 按 RabbitMQ queue 名拆的业务表（`queue_name` / `routing_key` 不进业务表，见 ADR 0003）
- Postgres `job_lease` 表（lease 等价物 = `status=running` + Redis 短锁，见 [jobs.md](./jobs.md)）
- admin 专用「第二套用户」
- WP15 Phase 1 的 **ads / campaign / growth** 营销表（仅事件 props 预留 `campaign_id`）

## 权威与缓存

- 任务状态、钱包、幂等：Postgres 权威。
- Redis：会话、短锁（worker 领取 job 的租约）、热点读。不把钱包余额只放在 Redis。
- TOS：字节，不把对象当行存储。
- MQ：不是存储。丢了靠 outbox + DB 状态恢复。

## `users`（WP17 已落地）

WP17 已实现：见 [WP17 brief](../feature-briefs/2026-10-26-auth-phone-email-identity.md)。基表 `V1`，`phone` / `email` 在 `V10`。

> `users` 只表示登录身份与资料（含 `password_hash`）。**禁止**在用户表存 `available` / `frozen` / 余额类字段；资金只在 [billing.md](./billing.md) 的 `wallet*`。
>
> **`role` 列已删除**（M4 `V24`）：运维身份改由 `role_assignment` 表达，见 [tenancy.md](./tenancy.md)。新列 `status` 用来停用账号。

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | 默认 `gen_random_uuid()`；也是 Sa-Token 的 `loginId` |
| `username` | TEXT | YES | UNIQUE | **不再用于登录**；新注册为 null；存量可保留 |
| `phone` | TEXT | YES | UNIQUE（非空值） | E.164，如 `+8613800138000` |
| `email` | TEXT | YES | UNIQUE（非空值） | 小写规范化 |
| `password_hash` | TEXT | NO | | BCrypt 哈希（strength 10） |
| `locale` | TEXT | YES | | `zh` / `en` 偏好；空则回退 Accept-Language / zh |
| `status` | TEXT | NO | DEFAULT `'active'`，CHECK ∈ `active` \| `disabled` | 停用后不能登录 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**表级 CHECK**（无仅 username 存量行时由 `V10` 落地）：`phone IS NOT NULL OR email IS NOT NULL`。

## Job 四表（WP4 已落地）

WP4 已实现：见 [WP4 brief](../feature-briefs/2026-10-24-wp4-job-outbox-rabbit.md)。四张表由
`api/src/main/resources/db/migration/V2__create_job_domain.sql` 建。

列定义 **不** 在本页改，仍以 [jobs.md](./jobs.md) 为准（WP4 照抄实现，没有加列，也没有加
`queue_name` / `routing_key` / `amqp_*` 这类队列实现字段）。

`wallet*` 已由 WP6 落地（见下节），`job_lease` 永远不建（用 Redis 短锁）。

## `provider_task`（WP5 已落地）

WP5 已实现：见 [WP5 brief](../feature-briefs/2026-10-24-wp5-thin-worker-anytoken.md)。表由
`api/src/main/resources/db/migration/V3__create_provider_task.sql` 建，同时给
`job_step.provider_task_id` 补上了指向 `provider_task(id)` 的物理外键。

列定义 **不** 在本页改，仍以 [jobs.md](./jobs.md) 为准（WP5 照抄实现，7 列，没加
`provider` / `http_status` / `updated_at` 这类未登记列；结构化摘要统一放进 `raw_body` JSONB）。

只有 worker 写这张表；C 端 `query` / `result` **绝不**返回它的任何字段。

## 钱包三表（WP6 已落地）

WP6 已实现：见 [WP6 brief](../feature-briefs/2026-10-24-wp6-prepaid-wallet.md)。三张表由
`api/src/main/resources/db/migration/V4__create_wallet_domain.sql` 建，同时给
`job.hold_id` 补上了指向 `wallet_hold(id)` 的物理外键。

列定义 **不** 在本页改，仍以 [billing.md](./billing.md) 为准（WP6 照抄实现：
`wallet` 5 列、`wallet_hold` 7 列、`wallet_ledger` 8 列，金额一律 BIGINT 正整数，
方向由流水 `type` 决定）。

账本只写在 api 进程；worker 不碰这三张表（ADR 0004）。

## `model_display_config`（WP11 已落地）

WP11 已实现：见 [WP11 brief](../feature-briefs/2026-10-24-wp11-admin-ops.md)。表由
`api/src/main/resources/db/migration/V7__create_admin_ops.sql` 建。

这张表只管 **前端展示与上下架**：它既不存供应商 API Key，也不是额度账本
（ADR 0001：Anytoken 是唯一模型网关，额度只有 `wallet` 一套账本）。
`V7` 预置 4 条真实生图模型种子（`ON CONFLICT (model) DO NOTHING`）：
`gpt-image-2` / `gpt-image-2.5-flare` / `gpt-image-2.5-sunburst` / `image2`。

**WP12 `V8`（`V8__seed_seedance_video_models.sql`）再幂等补 4 条视频模型种子**
（`capability = 'video.generate'`，`ON CONFLICT (model) DO NOTHING`）：

| model | capability | display_name | enabled | sort_order |
| --- | --- | --- | --- | --- |
| `seedance2` | `video.generate` | Seedance 2 | true | 50 |
| `seedance2.5` | `video.generate` | Seedance 2.5 | true | 60 |
| `seedance-fast` | `video.generate` | Seedance Fast | true | 70 |
| `seedance-mini` | `video.generate` | Seedance Mini | true | 80 |

`V8` 只做种子插入，**不改表结构**（列定义仍以本页为准）。`capability` 列放的是
`image.generate` / `video.generate` 这类能力名，不是渠道名，也不是额度键。

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | 默认 `gen_random_uuid()` |
| `model` | TEXT | NO | UNIQUE (`model`) | pilipala 内部模型标识；唯一键 |
| `capability` | TEXT | NO | DEFAULT `'image.generate'` | 能力名 |
| `display_name` | TEXT | NO | | 前端展示名 |
| `enabled` | BOOLEAN | NO | DEFAULT true | 是否上架；`GET /api/v1/models` 只返回上架的 |
| `sort_order` | INT | NO | DEFAULT 0 | 排序位；小的排前面 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | 最后一次改动时间 |

## 通用约定

- PostgreSQL；PK 默认 UUID + `gen_random_uuid()`。
- 时间一律 `TIMESTAMPTZ`。
- 金额一律 `BIGINT`（pilipala credit 整数）。禁止 `DECIMAL` / `FLOAT`。
- 字符串枚举存 `TEXT` + `CHECK`（或实现 WP 再用 PG enum，文档先写允许值）。
- JSON 用 `JSONB`。
- **前向策略（WP13 起）**：新表之间的关联默认用**逻辑外键**（列 + 索引 + 应用写路径校验 / 事务），**不以**数据库 `FOREIGN KEY` 约束作为主一致性手段。
- 金额、状态机相关写入必须在用例事务内保证合法 `user_id` / `job_id` / `hold_id`；禁止依赖 `ON DELETE CASCADE` 级联删流水。
- **存量**：已落地迁移中的物理 FK（如 `job.hold_id → wallet_hold`、`job_step.provider_task_id → provider_task`）**保留**；本 WP **不**批量 DROP。是否在未来迁移中弱化，另开 brief。
- 文档列上的 `FK → …` 表示逻辑关联与索引要求，不强制「下一步迁移必须加物理 CONSTRAINT」。

## `app_runtime_setting`（WP18 已落地）

WP18 已实现：见 [WP18 brief](../feature-briefs/2026-10-26-auth-ui-ribbon-email-flags.md)。表由 `V11__app_runtime_setting.sql` 建。

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `key` | TEXT | NO | PK | 点分命名，如 `auth.email_login_enabled` |
| `value` | TEXT | NO | | 布尔存 `true` / `false` 小写字符串；Studio 文案/截止时刻存原文字符串 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

Seed：`auth.email_login_enabled=false`、`auth.email_register_enabled=false`。

Studio chrome（`V13`）：`studio.promo.enabled=true`、`studio.promo.end_at=2026-11-15T15:59:59.000Z`、`studio.promo.title=预付费加赠限时开启`、`studio.promo.copy=充值加赠最高 20%，任务成功才扣费`、`studio.promo.cta=限时抢购`、`studio.membership.badge=限时 4.6 折`、`studio.membership.enabled=true`。缺 key 时公开接口仍回退到上述默认。

## 字段级怎么改

1. 实现 WP 发现本页与实现冲突 → **先改本页**（连带改 brief），再改代码。
2. 不允许在实现 PR 里顺手加「以后可能有用」的列。
3. 字段语义变更要同步 `api/gateway-contract.md` 与 [errors.md](./errors.md) 的码表。
