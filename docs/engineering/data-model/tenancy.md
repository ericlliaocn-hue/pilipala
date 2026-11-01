# 租户与 RBAC：表与字段级

pilipala 的权限是 **平台 / 租户两个范围的 RBAC**。`users.role` 已删除，运维身份改由 `role_assignment` 表达；钱包改挂租户。

> 本页是 **实现者的照抄源**：列名、类型、约束以本页为准，对应迁移是 `api/src/main/resources/db/migration/V24__admin_console_tenancy.sql`。
> 需求源：[M4 独立管理后台 brief](../feature-briefs/2026-11-01-admin-console.md)（步骤 1）。

- 权威存储：PostgreSQL。
- 时间一律 `TIMESTAMPTZ`；PK 默认 UUID + `gen_random_uuid()`。
- 金额一律 `BIGINT`（pilipala credit 整数），见 [billing.md](./billing.md)。

## 硬约束

- **钱包属于租户**：`wallet.tenant_id` 唯一且非空，`wallet.user_id` 已删除。余额只能通过流水变化，禁止直接写 `available`。
- **归属以 `tenant_id` 为准**：`wallet_hold` / `wallet_ledger` / `recharge_order` / `job` / `canvas_project` / `user_asset` / `user_subscription` / `credit_grant` 保留 `user_id`，它只表示操作者。
- **种子角色 `system=true`**：不能删、不能改权限集合。
- **至少保留一个 `platform_owner`**。
- 权限每次请求从数据库读，不把平台权限缓存在登录态里。

## 表清单

| 表 | 作用 | 首次落地 |
| --- | --- | --- |
| `tenant` | 租户（个人 / 组织） | M4（`V24`） |
| `tenant_membership` | 用户在租户里的成员关系 | M4（`V24`） |
| `permission` | 权限码全集 | M4（`V24`） |
| `role` | 角色（平台 / 租户） | M4（`V24`） |
| `role_permission` | 角色 → 权限码 | M4（`V24`） |
| `role_assignment` | 用户 → 角色（平台分配不带 `tenant_id`） | M4（`V24`） |
| `login_log` | 登录日志（只追加） | M4（`V24`） |
| `audit_log` | 审计日志（只追加） | M4（`V24`） |
| `banner` | 广告位（只有两个 placement） | M4（`V24`） |
| `dictionary_entry` | 字典显示名与排序 | M4（`V24`） |
| `admin_idempotency` | 管理写操作的幂等结果 | M4（`V24`） |

## `tenant`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | 默认 `gen_random_uuid()` |
| `type` | TEXT | NO | CHECK ∈ `personal` \| `organization` | 本阶段只创建 `personal` |
| `name` | TEXT | NO | | 展示名；存量用户用 username，空则手机号后四位 |
| `status` | TEXT | NO | DEFAULT `'active'`，CHECK ∈ `active` \| `disabled` | 停用后租户内成员不可操作 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

索引：`(type, status)`。

## `tenant_membership`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `tenant_id` | UUID | NO | REFERENCES `tenant(id)` | 所属租户 |
| `user_id` | UUID | NO | REFERENCES `users(id)` | 成员 |
| `status` | TEXT | NO | DEFAULT `'active'`，CHECK ∈ `active` \| `disabled` | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

唯一：`(tenant_id, user_id)`。索引：`(user_id)`。

## `permission`

权限码的全集。**身份是 `(code, scope)`**：权限码全集里 `job.read` 同时出现在平台清单和租户清单，
而角色分配要求「权限的 scope 与角色的 scope 相同」，所以同一码在两个范围各有一行。

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `code` | TEXT | NO | PK（与 `scope` 组成复合主键） | 权限码 |
| `scope` | TEXT | NO | CHECK ∈ `platform` \| `tenant` | 所属范围 |
| `description` | TEXT | NO | | 中文说明 |

平台权限码：

`staff.read` `staff.assign_role` `role.read` `role.write` `tenant.read` `tenant.member.read` `user.read` `user.disable` `user.enable` `user.read_phone` `wallet.read` `wallet.topup` `ledger.read` `recharge.read` `recharge.refund` `job.read` `job.retry` `job.cancel` `job.provider_raw` `model.read` `model.write` `i18n.read` `i18n.write` `credit_product.read` `credit_product.write` `membership.read` `membership.write` `banner.read` `banner.write` `dictionary.read` `dictionary.write` `runtime.read` `runtime.write` `stats.read` `status.read` `audit.read`

租户权限码：

`member.invite` `member.remove` `member.assign_role` `billing.recharge` `billing.read` `project.read` `project.write` `asset.read` `asset.write` `job.submit` `job.read`

## `role`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `code` | TEXT | NO | UNIQUE | 角色码 |
| `name` | TEXT | NO | | 展示名 |
| `scope` | TEXT | NO | CHECK ∈ `platform` \| `tenant` | 角色范围 |
| `system` | BOOLEAN | NO | | 种子角色为 `true`：不能删、不能改权限集合 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

## `role_permission`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `role_id` | UUID | NO | REFERENCES `role(id)` ON DELETE CASCADE | |
| `permission_code` | TEXT | NO | 复合外键 → `permission(code, scope)` | |
| `permission_scope` | TEXT | NO | CHECK ∈ `platform` \| `tenant` | 与角色 scope 必须相同 |

主键：`(role_id, permission_code)`。

插入时由触发器 `role_permission_scope_guard` 保证「权限的 scope 与角色的 scope 相同」：
跨范围授权直接报错，不靠应用层自觉。

## `role_assignment`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | NO | REFERENCES `users(id)` | 被授权人 |
| `role_id` | UUID | NO | REFERENCES `role(id)` | |
| `tenant_id` | UUID | YES | REFERENCES `tenant(id)` | 租户角色必填；平台角色必须为空 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

唯一索引：

- 平台分配：`UNIQUE (user_id, role_id) WHERE tenant_id IS NULL`
- 租户分配：`UNIQUE (user_id, role_id, tenant_id) WHERE tenant_id IS NOT NULL`

触发器 `role_assignment_scope_guard` 保证：租户范围的角色分配必须带 `tenant_id`，平台范围的不能带。

## `login_log`

只追加，不更新不删除。**不存验证码、令牌、密码。**

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | YES | | 登录失败且认不出用户时为空 |
| `tenant_id` | UUID | YES | | |
| `result` | TEXT | NO | CHECK ∈ `success` \| `failure` | |
| `ip` | TEXT | YES | | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

索引：`(user_id, created_at DESC)`、`(created_at DESC)`。

## `audit_log`

只追加：迁移只给 INSERT，不提供 UPDATE / DELETE 语句。

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `actor_user_id` | UUID | NO | | 操作人 |
| `permission_code` | TEXT | NO | | 触发本次操作的权限码 |
| `tenant_id` | UUID | YES | | |
| `target_type` | TEXT | NO | | 目标类型，如 `wallet` / `job` |
| `target_id` | TEXT | NO | | 目标 id |
| `idempotency_key` | TEXT | YES | | 加额 / 退款等写操作的幂等键 |
| `summary` | TEXT | NO | | 人类可读摘要；备注写这里 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

索引：`(created_at DESC)`、`(actor_user_id, created_at DESC)`。

## `banner`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `placement` | TEXT | NO | CHECK ∈ `home_promo` \| `membership` | **只有两个广告位** |
| `title` | TEXT | NO | | |
| `subtitle` | TEXT | YES | | |
| `image_object_key` | TEXT | YES | | 只存 TOS 对象键，不存 URL |
| `link_url` | TEXT | YES | | |
| `starts_at` | TIMESTAMPTZ | YES | | 空表示不限起点 |
| `ends_at` | TIMESTAMPTZ | YES | | 空表示不限终点 |
| `sort_order` | INT | NO | DEFAULT 0 | |
| `enabled` | BOOLEAN | NO | DEFAULT false | |
| `campaign_id` | UUID | YES | | 预留键；**不建广告表** |
| `created_at` / `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

索引：`(placement, enabled, sort_order, created_at)`。

## `dictionary_entry`

只改已有枚举的显示名与排序，**不能新增任务状态或流水类型**。

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `dict_code` | TEXT | NO | PK | 只允许 `job_status` / `ledger_type` / `recharge_order_status` / `tenant_type` / `capability` |
| `item_code` | TEXT | NO | PK | 必须与代码里的枚举值一致 |
| `locale` | TEXT | NO | PK，CHECK ∈ `zh` \| `en` | |
| `label` | TEXT | NO | | 显示名 |
| `sort_order` | INT | NO | DEFAULT 0 | |

错误码的长短文案仍在 `i18n_message`，不进字典。

## `admin_idempotency`

同一操作者、同一动作、同一幂等键只入账一次。

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `actor_user_id` | UUID | NO | | 操作者 |
| `action` | TEXT | NO | | 动作，如 `wallet.topup` / `recharge.refund` |
| `idempotency_key` | TEXT | NO | | 1–64 字符 |
| `response_json` | JSONB | NO | | 第一次的结果，重复调用原样返回 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

唯一：`(actor_user_id, action, idempotency_key)`。

## 改已有表

| 表 | 变更 |
| --- | --- |
| `users` | 新增 `status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled'))`；**删除 `role` 列** |
| `wallet` | 新增 `tenant_id UUID NOT NULL`（唯一）；**删除 `user_id` 列** |
| `wallet_hold` / `wallet_ledger` / `recharge_order` / `job` / `canvas_project` / `user_asset` / `user_subscription` / `credit_grant` | 新增 `tenant_id UUID NOT NULL`，保留 `user_id`（操作者），索引 `(tenant_id, created_at)` |

`user_subscription` 的有效会员改看租户；同一用户的多条订阅全部挂到他的个人租户，不合并不删除。

## 种子角色

| 角色 | 范围 | 权限 |
| --- | --- | --- |
| `platform_owner` | platform | 全部平台权限 |
| `platform_support` | platform | `user.read` `user.read_phone` `tenant.read` `tenant.member.read` `job.read` `job.provider_raw` `ledger.read` `recharge.read` `stats.read` `status.read` |
| `platform_finance` | platform | `user.read` `user.read_phone` `wallet.read` `wallet.topup` `ledger.read` `recharge.read` `recharge.refund` `stats.read` |
| `platform_operator` | platform | `model.read` `model.write` `i18n.read` `i18n.write` `credit_product.read` `credit_product.write` `membership.read` `membership.write` `banner.read` `banner.write` `dictionary.read` `dictionary.write` `runtime.read` `runtime.write` `job.read` `job.retry` `stats.read` `status.read` |
| `tenant_owner` | tenant | 全部租户权限 |
| `tenant_admin` | tenant | 全部租户权限，但接口上禁止移除本租户的 `tenant_owner` |
| `tenant_billing` | tenant | `billing.recharge` `billing.read` `job.read` `project.read` `asset.read` |
| `tenant_member` | tenant | `project.read` `project.write` `asset.read` `asset.write` `job.submit` `job.read` |

种子权限必须逐项落地，**不允许用通配符存在数据库里**。

## 迁移回填的顺序

`V24` 在一份已有数据的库上按这个顺序跑：

1. 建新表与范围守卫触发器。
2. `users` 加 `status`。
3. 每个存量用户建一条 `personal` 租户 + 一条 `active` 成员。
4. `wallet` 加 `tenant_id` → 按个人租户回填 → 校验没有空值与行数变化 → 改非空 → 唯一索引改到 `tenant_id` → 删 `user_id`。
5. 其余八张表加 `tenant_id` → 按 `user_id` 回填 → 校验没有空值 → 改非空 → 建 `(tenant_id, created_at)` 索引。
6. 删除 `users.role`。
7. 种子：权限码、8 个角色、角色权限、字典。
