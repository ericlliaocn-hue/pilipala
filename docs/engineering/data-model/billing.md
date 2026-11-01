# 计费域：表与字段级

pilipala 只做 **预付费钱包**。Anytoken 侧额度是公司成本，不映射成用户账本。

> 余额**只**存在于 `wallet`（及变动流水 `wallet_ledger`）。`users` 表不得镜像余额。
>
> **钱包属于租户**（M4 `V24`）：`wallet.tenant_id` 唯一且非空，`wallet.user_id` 已删除。归属与余额口径见 [tenancy.md](./tenancy.md)。

字段级由 [WP2 brief](../feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定。本页是 **实现者的照抄源**：列名、类型、约束以本页为准。

- 权威存储：PostgreSQL。
- 金额一律 `BIGINT`（pilipala credit 整数）。**禁止** `DECIMAL` / `FLOAT`。
- 时间一律 `TIMESTAMPTZ`；PK 默认 UUID + `gen_random_uuid()`。

## 表清单

| 表 | 作用 | 首次落地 |
| --- | --- | --- |
| `wallet` | 余额 | WP6 |
| `wallet_hold` | 冻结单 | WP6 |
| `wallet_ledger` | 流水，stage-1 必建 | WP6 |
| `recharge_order` | C 端充值订单（z-pay 支付宝） | WP16 |

## 状态：冻结 → 确认 / 释放

```
submit 估价成功 → freeze
job succeeded     → confirm（冻结转为实扣）
job cancelled / dead / 不再重试的失败 → release（解冻）
```

不允许：

- 先出图后扣款（后付费）
- 成功路径只冻不确认（余额被永久占用）
- 失败路径不释放（用户被吞钱）
- 在 Anytoken 再记一笔用户额度并与钱包对账双写

## `wallet`

余额字段只在本表；与 [data-model/README.md](./README.md) 前向外键策略一致：文档中的 `FK →` 为逻辑关联 + 索引；存量迁移里已有的物理 FK 保留，不在 WP13 批量拆除。

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `tenant_id` | UUID | NO | UNIQUE；FK → `tenant.id` | **钱包属于租户**：一租户一钱包（M4 `V24` 取代原 `user_id`） |
| `available` | BIGINT | NO | DEFAULT 0；CHECK >= 0 | 可用 credit |
| `frozen` | BIGINT | NO | DEFAULT 0；CHECK >= 0 | 已冻未确认 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**不变量**：`available >= 0`，`frozen >= 0`。变更只经由 hold / ledger 路径，禁止「直接改两个数字」。

## `wallet_hold`（冻结单）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | = `job.hold_id` |
| `tenant_id` | UUID | NO | FK → `tenant.id`；IDX (`tenant_id`, `created_at`) | 归属租户（M4 `V24`） |
| `user_id` | UUID | NO | IDX (`user_id`, `created_at`) | 冗余便于对账；只表示操作者 |
| `job_id` | UUID | YES | UNIQUE 可空 | 默认与 job 同事务创建 |
| `amount` | BIGINT | NO | CHECK > 0 | 冻结额 |
| `status` | TEXT | NO | CHECK ∈ `frozen` \| `confirmed` \| `released` | 终态只进一次 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `finalized_at` | TIMESTAMPTZ | YES | | confirm / release 时间 |

终态只可进入一次。**重放 confirm / release 必须幂等**（实现 WP6 验收）。

## `wallet_ledger`（流水）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `wallet_id` | UUID | NO | FK → `wallet.id`；IDX (`wallet_id`, `created_at`) | |
| `tenant_id` | UUID | NO | FK → `tenant.id`；IDX (`tenant_id`, `created_at`) | 归属租户（M4 `V24`） |
| `user_id` | UUID | NO | | 冗余便于对账；只表示操作者 |
| `type` | TEXT | NO | CHECK ∈ `topup` \| `freeze` \| `confirm` \| `release` \| `refund` | |
| `amount` | BIGINT | NO | CHECK != 0 | 符号约定见下表 |
| `hold_id` | UUID | YES | FK → `wallet_hold.id` | |
| `job_id` | UUID | YES | | 追溯到任务 |
| `recharge_order_id` | UUID | YES | IDX（可空） | 逻辑关联 `recharge_order.id`；**无** `ON DELETE CASCADE` |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 金额符号约定

| type | `available` | `frozen` | 含义 |
| --- | --- | --- | --- |
| `topup` | `+amount` | — | 充值（含 WP16 渠道入账） |
| `refund` | `-amount` | — | 充值退款扣回（须 `available >= amount`） |
| `freeze` | `-amount` | `+amount` | 冻结：可用转已冻 |
| `confirm` | — | `-amount` | 确认：已冻转实扣 |
| `release` | `+amount` | `-amount` | 释放：已冻退回可用 |

`amount` 恒为正整数，方向由 `type` 决定；实现者不要靠正负号猜语义。

**单账本**：禁止 Anytoken 侧再记用户额度并双写。pilipala 用户余额只存在于 `wallet` + `wallet_ledger`。

## 与任务的绑定

- 冻不住：不入队。用户得到 `WALLET_INSUFFICIENT`（码表见 [errors.md](./errors.md)）。
- 重试 attempt **不** 重复冻结。一次 job 一次 hold。
- 估价过低导致上游实际更贵：stage-1 不自动补扣，默认「产品按标价冻，成本差算运营」。如需改规则，必须先改本页与对应 brief。
- 估价过高：成功仍按冻结额 confirm，或按实际确认差额 release——**必须二选一**并写进实现 brief，禁止实现者现场发挥。

## 充值

运维脚本 / Admin 手工加额仍须同时记 `wallet_ledger（type=topup）`。WP16 起 C 端可通过 **z-pay 支付宝（mapi qrcode）** 自助充值：订单真相在 `recharge_order`，验签异步通知与服务端查单补单入账，流水 `type=topup` 且填写 `recharge_order_id`。`notify_url` / `return_url` 由部署环境 `PILIPALA_PUBLIC_BASE_URL` 拼接（生产 env：`https://pilipala.art`），**禁止**在代码里写死域名或抄外站回调 URL。

### `recharge_order`（WP16）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | NO | IDX (`user_id`, `created_at` DESC)；REFERENCES `users(id)` **无 CASCADE** | 操作者 |
| `tenant_id` | UUID | NO | FK → `tenant.id`；IDX (`tenant_id`, `created_at`) | 归属租户（M4 `V24`） |
| `out_trade_no` | TEXT | NO | UNIQUE | 前缀 `flr_` + 日期 + 随机 |
| `provider` | TEXT | NO | DEFAULT `zpay` | |
| `provider_trade_no` | TEXT | YES | IDX（可空） | 上游 `trade_no` |
| `package_id` | TEXT | NO | | 配置套餐 id |
| `money_fen` | BIGINT | NO | CHECK > 0 | 人民币分 |
| `credits` | BIGINT | NO | CHECK > 0 | 入账 credit |
| `status` | TEXT | NO | CHECK ∈ `created` \| `pending_pay` \| `paid` \| `failed` \| `closed` \| `refunded` | |
| `client_ip` | TEXT | NO | | 下单 `clientip` |
| `device` | TEXT | YES | | `mobile` 或 null |
| `pay_url` / `qrcode_url` | TEXT | YES | | 规范化绝对 URL |
| `product_name` | TEXT | NO | | 上游商品名 |
| `ledger_id` | UUID | YES | | 入账后 `wallet_ledger.id` |
| `paid_at` / `refunded_at` | TIMESTAMPTZ | YES | | |
| `fail_reason` | TEXT | YES | | 勿存密钥 |
| `created_at` / `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

渠道适配、签名、对账调度均在 **billing** 域；`auth` 只提供已认证 `user_id`。webhook 路径 `/api/v1/billing/recharge/webhook/zpay` 在 `AuthPathPolicy` 匿名放行。

## 与其它域的接口

- `job.hold_id` → `wallet_hold.id`（见 [jobs.md](./jobs.md)）。
- 账本只有一套：禁止 NewAPI 风格的 `tokens` / `quotas` / `channels` 用户额度镜像表，见 [ADR 0001](../adr/0001-anytoken-not-newapi-fork.md)。
