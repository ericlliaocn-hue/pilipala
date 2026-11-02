# 错误模型：`error_code` 注册表

字段与码名由 [WP2 brief](../feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定。决策见 [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)。

本页是 **实现者的照抄源**：`error_code` 名称一旦发布就是 C 端合同，不得随意改名。确需更名要先改本页与对应 brief。

## 两类受众，两套字段

| 字段 | 受众 | 内容 | 出现位置 |
| --- | --- | --- | --- |
| `error_code` | 用户 + 客服 | 稳定大写蛇形，由 pilipala 定义 | C 端响应体、DB `job.error_code` / `job_step.error_code` |
| `message` | 用户 | 当前 locale 的友好短句 | C 端响应体 |
| 原始 HTTP 状态 / 原始 JSON / 上游堆栈 | 运营 | 原文 | 服务端日志、`provider_task.raw_status` / `provider_task.raw_body` |

C 端默认响应 **只** 含 `error_code` + `message`。原文只在 M4 / WP11 admin 与内部角色可见，禁止进公开文档样例。

## 注册表

| error_code | 何时 | 备注 |
| --- | --- | --- |
| `WALLET_INSUFFICIENT` | 冻结失败 | 用户余额不足；不入队 |
| `JOB_NOT_FOUND` | 不存在或非本人 | 查别人的 job 也返回此码，不泄露存在性 |
| `JOB_CANCELLED` | 取消 | 用户主动取消或任务被取消 |
| `JOB_FAILED` | 已映射的泛化业务失败 | 上游失败但可归类到通用业务失败时使用 |
| `PROVIDER_UNAVAILABLE` | 任一网关 / 上游不可用或未识别 | 未识别上游错误的默认落点 |
| `IDEMPOTENCY_CONFLICT` | 同一 key 不同 body | 不静默复用旧 `job_id` |
| `UNAUTHORIZED` | 无会话 | 未登录调用 C 端接口 |
| `FORBIDDEN` | 已登录但不是管理员 | 访问 `/api/v1/admin/**` 被拒（WP11）；普通用户看到的稳定码 |
| `VALIDATION_ERROR` | 请求字段不合法 | 参数缺失 / 类型错 / 取值越界。认证业务态仍用此码，用 `i18n_message` key 区分：`AUTH_OTP_INVALID`、`AUTH_ACCOUNT_NOT_FOUND`、`AUTH_IDENTITY_ALREADY_REGISTERED`、`AUTH_IDENTITY_OCCUPIED` |
| `PROJECT_NOT_FOUND` | 云端项目不存在或非本人 | 查别人的项目也返回此码，不泄露存在性（WP10） |
| `ASSET_NOT_FOUND` | 云端素材不存在或非本人 | 同上（WP10） |
| `GATEWAY_TIMEOUT` | 网关轮询超时 | 上游异步任务在轮询上限内没进终态（WP12/WP21 视频）；**可重试** |
| `GATEWAY_INVALID_RESPONSE` | 网关响应不合法 | 上游没给出任务 id，无法继续轮询（WP12/WP21 视频）；**不可重试** |
| `UPSTREAM_VIDEO_FAILED` | 上游视频任务失败终态 | 上游明确返回 `failed` / `cancelled` / `error`（WP12/WP21 视频）；**不可重试**，原文进 `provider_task` |
| `NO_AVAILABLE_CHANNEL` | 视频产品无可用 target | route 链全部禁用 / 参数不兼容 / 成本超限（WP21）；**不可重试**，释放冻结 |
| `RECHARGE_ORDER_NOT_FOUND` | 充值订单不存在或非本人 | WP16 |
| `RECHARGE_PROVIDER_ERROR` | z-pay 下单/查单/退款失败 | WP16；原文仅日志 |
| `RECHARGE_NOT_REFUNDABLE` | 订单不可退或余额不足扣回 | WP16 Admin 退款 |
| `OTP_RATE_LIMITED` | 验证码发送冷却或小时/日上限 | WP17；占用冲突仍用 `VALIDATION_ERROR`（文案 key `AUTH_IDENTITY_OCCUPIED`） |
| `ACCOUNT_DISABLED` | 账号已停用 | M4（admin-console 步骤 2）；登录与重置密码都拒绝，不发会话、不写登录日志中的成功行 |

**未识别上游** → 落到 `PROVIDER_UNAVAILABLE` 或 `JOB_FAILED` 一类泛化码，原文入库。禁止把 `e.getMessage()` 填进 `message`。

新增码的规矩：先在本表登记，再写映射，再写两套文案模板。码名用大写蛇形，语义稳定优先于措辞优美。

## 与 `i18n_message` 的关系

`message` **只** 来自 `i18n_message` 表里当前 locale 的模板，不在代码里拼接、不把上游字符串塞进去。

| 关系 | 说明 |
| --- | --- |
| `i18n_message.key` | 常与 `error_code` 相同，也可用其前缀（如 `JOB_FAILED` / `JOB_FAILED.RETRY`） |
| `i18n_message.locale` | `zh` / `en`，stage-1 仅这两种 |
| 缺词条回退 | 请求语言 → `zh` → `error_code` 本身（最后手段，仍不回退到上游英文堆栈） |

`i18n_message` 同时承载非错误的动态运营文案（公告、模型下架说明、充值提示）。**不要**为每种语言建一张表，也不要在 Java 里 `if ("zh")` 拼错误。

WP11 起，`FORBIDDEN` 同时以 `FORBIDDEN` 与 `error.FORBIDDEN` 两个 key 落种子（中英各一条），
兼容「码名直接当 key」与「`error.` 前缀」两种取法。

### `i18n_message` 列级 schema

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `key` | TEXT | NO | UNIQUE (`key`, `locale`) | 常与 `error_code` 相同 |
| `locale` | TEXT | NO | CHECK ∈ `zh` \| `en` | stage-1 仅这两种 |
| `template` | TEXT | NO | | 短句；有限占位符 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

首次落地：WP9。

## 与 `provider_task` 的关系

```
上游响应 ──► provider_task.raw_status / raw_body   （运营可见）
                    │
                    ▼ 映射层
              error_code ──► i18n_message.template ──► message （C 端可见）
```

- 映射只发生在服务端。映射失败时用泛化码，不抛原始异常文本。
- `provider_task.raw_body` **禁止**出现在 `query` / `result` 响应里，见 [api/gateway-contract.md](../api/gateway-contract.md)。
- C 端 `query` 返回 `error_code` + `message`；重试中展示「失败重试中」由前端 locale 承担，状态权威在 `job.status`。

## 相关

- [i18n-and-errors.md](../i18n-and-errors.md)：两套文案来源的分工与前端职责
- [data-model/jobs.md](./jobs.md)：`job.error_code` / `job_step.error_code` 列
- [data-model/billing.md](./billing.md)：`WALLET_INSUFFICIENT` 的触发点
