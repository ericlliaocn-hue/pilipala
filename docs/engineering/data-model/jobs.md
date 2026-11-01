# Job 域：表与字段级

字段级由 [WP2 brief](../feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定。本页是 **实现者的照抄源**：列名、类型、约束、枚举以本页为准，不要在实现 WP 里自行改名或加「以后可能有用」的列。

- 权威存储：PostgreSQL。
- 队列：RabbitMQ（第一期），但 **job 模型与队列无关**，见 [ADR 0003](../adr/0003-rabbitmq-first-queue-agnostic-jobs.md)。
- 通用约定：PK 默认 UUID + `gen_random_uuid()`；时间一律 `TIMESTAMPTZ`；字符串枚举存 `TEXT` + `CHECK`；JSON 用 `JSONB`。

## 表清单

| 表 | 作用 | 首次落地 |
| --- | --- | --- |
| `job` | 状态机权威行 | WP4 |
| `job_step` | 每次 attempt 一行 | WP4 |
| `outbox_message` | 事务内待投递 | WP4 |
| `idempotency_record` | `user_id` + key → 已返回的 `job_id` | WP4 |
| `provider_task` | Anytoken / 上游原文痕迹 | WP5 |

不建：`job_lease`（见下文「Lease 等价物」）。

## 状态机

```
created → queued → running → succeeded
                ↘ failed ──(attempt < max_attempts)→ queued
                ↘ cancelled          （终态，release）
                ↘ dead               （终态，attempt 用尽或不可重试，release）
```

| 状态 | 终态？ | 谁写 | 钱包 |
| --- | --- | --- | --- |
| `created` | 否 | api | 可尚未 freeze |
| `queued` | 否 | api / 重试迁移 | 已 freeze（可执行任务） |
| `running` | 否 | worker | 保持 freeze |
| `succeeded` | 是 | 状态迁移 | confirm |
| `failed` | 否（可见中间态） | worker 汇报后 | 保持 freeze；若 `attempt < max` → 回 `queued` |
| `cancelled` | 是 | api / worker | release |
| `dead` | 是 | 状态迁移 | release |

**锁死的选择**：attempt 用尽或不重试 → 进入 `dead`，**不以 `failed` 作终态停留**。

对用户查询：重试中可展示「失败重试中」；`status` 仍可能短暂为 `failed` 或已回 `queued`——实现 WP 选一种并写进验收。不可恢复则 `dead`。

`failed` 是 **中间可见状态**，不是必须停留的终态。

## `job`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | 亦即 MQ 消息体里的 `job_id` |
| `user_id` | UUID | NO | IDX (`user_id`, `created_at` DESC) | 提交者；FK → `users.id`（`users` 表 WP3 建）；只表示操作者 |
| `tenant_id` | UUID | NO | FK → `tenant.id`；IDX (`tenant_id`, `created_at`) | 归属租户（M4 `V24`）；归属以它为准 |
| `status` | TEXT | NO | CHECK ∈ 上表枚举；IDX (`status`, `updated_at`) | 状态机权威 |
| `capability` | TEXT | NO | | 如 `image.generate` |
| `model` | TEXT | NO | | pilipala 内部模型名；WP21 起可为产品 code |
| `product_id` | UUID | YES | IDX | WP21：产品 SKU；老 job 为空 |
| `target_id` | UUID | YES | | WP21：当前 / 最后 target |
| `quoted_price` | BIGINT | YES | | WP21：冻结时的产品价快照（credits） |
| `pricing_version` | TEXT | YES | | WP21：定价版本 |
| `params` | JSONB | NO | | 入队前已物化；worker 不回源画布 |
| `attempt` | INT | NO | DEFAULT 0；CHECK >= 0 | 已开始的 attempt 数 |
| `max_attempts` | INT | NO | DEFAULT 3；CHECK >= 1 | 默认 3；实现 WP 不得静默改默认，除非改本文档 |
| `hold_id` | UUID | YES | FK → `wallet_hold.id` | 一 job 一 hold；未冻前可空 |
| `idempotency_key` | TEXT | NO | UNIQUE (`user_id`, `idempotency_key`) | 与用户一起唯一 |
| `cancel_requested_at` | TIMESTAMPTZ | YES | | 取消意图 |
| `error_code` | TEXT | YES | | 映射后的稳定码，见 [errors.md](./errors.md) |
| `result_object_keys` | JSONB | YES | | 成功时 TOS object_key 列表，如 `["img/..."]` |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 禁止出现在 `job` 上的列

- `queue_name` / `routing_key` / `amqp_*`：队列实现可换，job 模型不绑死队列（ADR 0003）。
- Anytoken user id：网关侧不映射成 pilipala 用户。
- 上游 raw message：原文只进 `provider_task` 与日志。

## Lease 等价物

**不建 `job_lease` 表。** 权威在 Postgres，短锁在 Redis。

Worker 领取一个 job 时：

1. 事务内 `status: queued → running`，同时 `attempt = attempt + 1`。
2. 同时 Redis `SET job:lease:{job_id} {worker_id} NX EX {seconds}`。
   - 键约定：`job:lease:{job_id}`
   - 值：worker 实例标识
   - 建议默认 TTL **60 秒**——这是建议默认值，实现 WP 要在 brief 里确认可调，**禁止在代码里拍脑袋**。

锁丢失但 DB 仍为 `running`：靠超时扫 + `attempt` / `max_attempts` 规则回收。超时阈值与回收策略由实现 WP 写验收，本页只锁「权威在 Postgres，短锁在 Redis」这一条分工。

## `job_step`

一次 attempt 一行。

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `job_id` | UUID | NO | FK → `job.id` ON DELETE CASCADE；UNIQUE (`job_id`, `attempt`) | |
| `attempt` | INT | NO | CHECK >= 1 | |
| `status` | TEXT | NO | CHECK ∈ `running` \| `succeeded` \| `failed` \| `cancelled` | |
| `error_code` | TEXT | YES | | 映射码 |
| `provider_task_id` | UUID | YES | FK → `provider_task.id` | 指向原文痕迹 |
| `target_id` | UUID | YES | | WP21：本 attempt 的 target；fallback 新开 attempt |
| `error_class` | TEXT | YES | | WP21：`transient/content/param/auth/task_failed/timeout/download` |
| `started_at` | TIMESTAMPTZ | NO | | |
| `ended_at` | TIMESTAMPTZ | YES | | |

## `outbox_message`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `aggregate_type` | TEXT | NO | | 如 `job` |
| `aggregate_id` | UUID | NO | IDX (`status`, `created_at`) 部分索引 `pending` | 通常为 `job_id` |
| `payload` | JSONB | NO | | 业务上必须含 `{"job_id":"<uuid>"}`；可加 `event_id`（= outbox id）供投递幂等 |
| `status` | TEXT | NO | CHECK ∈ `pending` \| `sent` \| `failed` | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `sent_at` | TIMESTAMPTZ | YES | | |

dispatcher 只负责投递。重试间隔与失败处理由实现 WP 再定。换成 RocketMQ 不改本表语义。

## `idempotency_record`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | NO | | 作用域 |
| `key` | TEXT | NO | UNIQUE (`user_id`, `key`) | 客户端幂等键 |
| `job_id` | UUID | NO | FK → `job.id` | 首次 submit 的结果 |
| `request_hash` | TEXT | NO | | 同一 key 不同 body → `IDEMPOTENCY_CONFLICT` |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

同一 key 重复 submit 返回同一 `job_id`，不二次冻结。

## `provider_task`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `job_id` | UUID | NO | IDX (`job_id`)；FK → `job.id` | |
| `job_step_id` | UUID | YES | FK → `job_step.id` | |
| `upstream_request_id` | TEXT | YES | | Anytoken / 上游请求 id |
| `raw_status` | TEXT | YES | | 上游状态 |
| `raw_body` | JSONB | YES | | 原文 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `target_id` | UUID | YES | | WP21：本次 attempt 的 target |
| `upstream_task_id` | TEXT | YES | | WP21：NewAPI 异步任务 id |
| `status` | TEXT | NO | DEFAULT `legacy`；CHECK ∈ `submitted` \| `polling` \| `succeeded` \| `failed` \| `cancelled` \| `legacy` | WP21 轮询子状态；存量 / Anytoken 旧路径为 `legacy`，NewAPI 新行显式写 `polling` |
| `next_poll_at` | TIMESTAMPTZ | YES | 部分索引 (`status`, `next_poll_at`) WHERE `status IN ('submitted','polling')` | 调度器按此重投 |
| `poll_count` | INT | NO | DEFAULT 0；CHECK >= 0 | |
| `last_poll_at` | TIMESTAMPTZ | YES | | |
| `error_class` | TEXT | YES | | 同 `job_step` |
| `latency_ms` | INT | YES | | 提交 / 查询耗时 |
| `cost_amount` | NUMERIC(12,4) | YES | | 上游成本快照，只做毛利分析 |
| `cost_currency` | TEXT | YES | | |

C 端 `query` / `result` **不** 包含 `raw_body`。原文只进日志与本表，仅 M4 / WP11 admin 可见。

## 视频路由两表（WP21 Phase 2）

`model_product` / `model_product_group` 的字段级定义见 [WP21 Phase 2 brief](../feature-briefs/2026-10-29-wp21-phase2-newapi-fork.md)。`model_target` / `model_route` 已由 `V21` 删除；`job` / `provider_task` 上遗留的 `target_id` 只作审计列，运行时不读写。它们不存渠道 Key、不是额度账本；产品价只在 `model_product`，上游成本只做毛利分析。

## MQ 消息体

```json
{ "job_id": "<uuid>" }
```

可加传输层 `event_id`；**禁止**塞 `params` / 图片 / 钱包额。
