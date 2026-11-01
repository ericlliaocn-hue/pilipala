# Feature brief：独立管理后台、RBAC 与租户边界（开发计划）

- 日期：2026-11-01
- 作者：pilipala engineering
- 对应 WP / 里程碑：M4 运维面重做；为后续团队版铺租户边界
- 状态：已确认
- 建议分支：从 `feat/wp21-video-gateway` 拉 `feat/admin-console`
- 交给实现模型的方式：每次只派一个步骤。消息里贴本文件全文，并写明「只做步骤 N，做完停。不要做步骤 N+1，不要 merge，不要部署生产。」

本文件是需求源。步骤里没写的表、字段、权限码、接口、超时，禁止实现者自己加。

## 0. 先读

按顺序读完再改代码：

1. 本文件
2. `AGENTS.md`
3. `docs/engineering/adr/0005-friendly-errors-vs-raw-upstream.md`
4. `docs/engineering/adr/0004-thin-worker-no-god-classes.md`
5. `docs/engineering/data-model/billing.md`
6. `docs/engineering/data-model/analytics.md`（报表口径；本计划不建 `analytics_event`）
7. `api/src/main/java/art/pilipala/api/admin/application/AdminGuard.java`
8. `api/src/main/resources/db/migration/` 里版本号最大的那个文件

基线分支必须已经包含充值、积分商品、会员、视频产品（线上 Flyway 已到 `V22`）。不要从落后的本地 `main`（只有概念 D logo、没有这些表）开工。若拉下来的分支最高迁移号不是 `V22` 或更高，停下来说明，不要猜。

## 1. 已拍板，禁止再猜

1. 管理后台是独立前端 `admin/`，部署名 `admin.pilipala.art`。本计划只做代码和 nginx 示例配置，不登录生产机。
2. 创作站 `web` 的 `/admin` 从 `UserLayout` 去掉。
3. 权限是平台 / 租户两个范围的 RBAC。`users.role` 删除，不再读取。
4. 每个用户注册时创建一个 `personal` 租户、一条成员、一只钱包，并授予 `tenant_owner`。
5. 钱包属于租户。`wallet.user_id` 删除，`wallet.tenant_id` 唯一且非空。
6. 组织租户的建团、邀请、席位、切换器不做。表允许 `type=organization`，本计划没有创建它的接口。
7. 余额只能通过流水变化：加额写 `topup`，退款走现有退款，取消任务走现有取消并释放冻结。禁止把 `available` 直接写成一个新数字。
8. 加额和退款不要第二次短信验证。要幂等键，同一键只入账一次。
9. 种子角色 `system=true`：不能删，不能改它的权限集合。`platform_owner` 可以新建 `system=false` 的角色并勾选已有权限码，不能发明新权限码。
10. 至少保留一个 `platform_owner`。引导名单只在系统里还没有任何 `platform_owner` 时，把本次登录用户授成 `platform_owner`。已经有所有者之后，名单不再自动升权。
11. 权限每次请求从数据库读，不把平台权限缓存在登录态里。
12. NewAPI 与 Anytoken 不接收 `tenant_id` 和用户 id。Worker 若当前请求体里没有这两项，则 `worker/` 零改动。
13. 后台菜单在代码里登记，绑定权限。只读展示，不能在页面上新建路由。
14. 字典只能改已有枚举的显示名和排序，不能新增任务状态或流水类型。
15. `i18n_message` 继续管动态错误和运营短句。前端语言包里的按钮文案不搬进数据库。
16. Banner 广告位只有 `home_promo` 和 `membership`。`campaign_id` 可空，不建广告表，不放广告管理菜单。
17. 统计直接查业务表。不建 `analytics_event`，不采集客户端埋点。
18. 登录日志和审计只追加，本计划不写删除任务，不定保留天数。
19. 不代用户登录。不做发票、内容审核、按租户单独售价。
20. 不改 `docs/content/**`。不把真实密钥写入仓库。

## 2. 权限与种子角色

权限码以本节为全集。

**平台：** `staff.read` `staff.assign_role` `role.read` `role.write` `tenant.read` `tenant.member.read` `user.read` `user.disable` `user.enable` `user.read_phone` `wallet.read` `wallet.topup` `ledger.read` `recharge.read` `recharge.refund` `job.read` `job.retry` `job.cancel` `job.provider_raw` `model.read` `model.write` `i18n.read` `i18n.write` `credit_product.read` `credit_product.write` `membership.read` `membership.write` `banner.read` `banner.write` `dictionary.read` `dictionary.write` `runtime.read` `runtime.write` `stats.read` `status.read` `audit.read`

**租户：** `member.invite` `member.remove` `member.assign_role` `billing.recharge` `billing.read` `project.read` `project.write` `asset.read` `asset.write` `job.submit` `job.read`

种子角色的权限必须逐项种子，不要用通配符存在数据库里：

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

`runtime.write` 只能改这两个键：`auth.email_login_enabled`、`auth.email_register_enabled`。其他运行时键拒绝。

## 3. 步骤总览

| 步骤 | 做什么 | 前置 |
| --- | --- | --- |
| 1 | Flyway：新表、租户回填、钱包改挂租户、删 `users.role` | 无 |
| 2 | 注册建个人租户；登录记日志；鉴权服务；引导所有者 | 1 |
| 3 | 创作站读写改为当前租户；现有 admin 接口改为权限码 | 2 |
| 4 | 新管理接口：角色、员工、租户、封禁、取消任务、Banner、字典、运行时开关、视频产品上下架、统计、异常、状态、导出、审计 | 3 |
| 5 | `admin/` 独立前端 | 4 |
| 6 | 创作站去掉内嵌后台，首页和会员页改读 Banner | 4 |
| 7 | 文档、CHANGELOG、测试清单勾完 | 5 和 6 |

不要并行改同一次迁移。步骤 5 和 6 可以在步骤 4 合并之后由两个模型分别做。

---

## 步骤 1：迁移

新增一个 Flyway 文件，序号是目录里最大号加 1。不要修改已有 `V*.sql`。

### 新表

`tenant`：`id UUID PK`，`type TEXT NOT NULL CHECK (type IN ('personal','organization'))`，`name TEXT NOT NULL`，`status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled'))`，`created_at` / `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`。

`tenant_membership`：`id UUID PK`，`tenant_id UUID NOT NULL REFERENCES tenant(id)`，`user_id UUID NOT NULL REFERENCES users(id)`，`status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled'))`，`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。唯一 `(tenant_id, user_id)`。

`permission`：`code TEXT PK`，`scope TEXT NOT NULL CHECK (scope IN ('platform','tenant'))`，`description TEXT NOT NULL`。

`role`：`id UUID PK`，`code TEXT NOT NULL UNIQUE`，`name TEXT NOT NULL`，`scope TEXT NOT NULL CHECK (scope IN ('platform','tenant'))`，`system BOOLEAN NOT NULL`，`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。

`role_permission`：主键 `(role_id, permission_code)`，外键分别指向 `role` 与 `permission`。插入时用 SQL 保证权限的 `scope` 与角色的 `scope` 相同。

`role_assignment`：`id UUID PK`，`user_id UUID NOT NULL REFERENCES users(id)`，`role_id UUID NOT NULL REFERENCES role(id)`，`tenant_id UUID NULL REFERENCES tenant(id)`，`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。检查：角色范围是 `tenant` 时 `tenant_id` 非空；范围是 `platform` 时 `tenant_id` 为空。平台分配的唯一索引：`(user_id, role_id) WHERE tenant_id IS NULL`。租户分配的唯一索引：`(user_id, role_id, tenant_id) WHERE tenant_id IS NOT NULL`。

`login_log`：`id UUID PK`，`user_id UUID NULL`，`tenant_id UUID NULL`，`result TEXT NOT NULL CHECK (result IN ('success','failure'))`，`ip TEXT NULL`，`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。不存验证码、令牌、密码。

`audit_log`：`id UUID PK`，`actor_user_id UUID NOT NULL`，`permission_code TEXT NOT NULL`，`tenant_id UUID NULL`，`target_type TEXT NOT NULL`，`target_id TEXT NOT NULL`，`idempotency_key TEXT NULL`，`summary TEXT NOT NULL`，`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。只插入，不提供更新和删除语句。

`banner`：`id UUID PK`，`placement TEXT NOT NULL CHECK (placement IN ('home_promo','membership'))`，`title TEXT NOT NULL`，`subtitle TEXT NULL`，`image_object_key TEXT NULL`，`link_url TEXT NULL`，`starts_at TIMESTAMPTZ NULL`，`ends_at TIMESTAMPTZ NULL`，`sort_order INT NOT NULL DEFAULT 0`，`enabled BOOLEAN NOT NULL DEFAULT false`，`campaign_id UUID NULL`，`created_at` / `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`。

`dictionary_entry`：主键 `(dict_code, item_code, locale)`。`label TEXT NOT NULL`，`sort_order INT NOT NULL`。`locale CHECK (locale IN ('zh','en'))`。

`admin_idempotency`：`id UUID PK`，`actor_user_id UUID NOT NULL`，`action TEXT NOT NULL`，`idempotency_key TEXT NOT NULL`，`response_json JSONB NOT NULL`，`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`。唯一 `(actor_user_id, action, idempotency_key)`。

### 改已有表

`users` 增加 `status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled'))`。回填完成后再 `DROP COLUMN role`。

为每个已有用户插入一个 `personal` 租户（`name` 用 `username`，空则用手机号后四位）、一条 `active` 成员。

`wallet` 增加 `tenant_id UUID NULL`，按该用户的个人租户回填，然后 `SET NOT NULL`，唯一约束改到 `tenant_id`，`DROP COLUMN user_id`。回填后钱包行数必须与回填前相同。

以下表增加 `tenant_id UUID NOT NULL`（先可空、按 `user_id` 找到个人租户回填、再设非空）：`wallet_hold`、`wallet_ledger`、`recharge_order`、`job`、`canvas_project`、`user_asset`、`user_subscription`、`credit_grant`。这些表保留 `user_id`，表示操作者；归属以 `tenant_id` 为准。索引 `(tenant_id, created_at)`。没有 `created_at` 的表就只建 `tenant_id` 索引。

`user_subscription` 的有效会员改为看租户，不看「这个用户个人还有没有另一条订阅」。回填后同一用户若有多条，全部挂到他的个人租户，不合并、不删除。

种子：第 2 节的全部权限和 8 个角色及其 `role_permission`。`system=true`。

字典种子的 `dict_code` 只允许：`job_status`、`ledger_type`、`recharge_order_status`、`tenant_type`、`capability`。`item_code` 必须与当前代码里的枚举值一致，zh / en 各一行。错误码的长短文案仍只在 `i18n_message`，不要再种子进字典。

### 本步完成

迁移能在一份已有数据的库上跑完。写 `docs/engineering/data-model/tenancy.md`，把上述表抄进去，并在 `data-model/README.md` 加上链接。写明钱包属于租户、`users.role` 已删除。不要改 `docs/content/**`。

---

## 步骤 2：租户创建、登录日志、鉴权

注册成功后，在同一事务里：创建 `personal` 租户、成员、`tenant_owner` 分配、该租户的钱包。初始积分若已有逻辑，记入这只钱包的 `topup` 流水，不要写用户列。

登录成功：写 `login_log.result=success`，会话中的当前租户设为该用户唯一的个人租户。登录失败：能解析出用户则写 `user_id`，否则 `user_id` 为空，`result=failure`。不写验证码。

`users.status=disabled` 的用户登录失败，错误码用已有的认证失败码，不要新造码，除非 `errors.md` 里没有能表达「账号已停用」的码。没有则新增 `ACCOUNT_DISABLED`，并在 `i18n_message` 种子中英短句。这是本计划允许新增的唯一错误码。

鉴权服务：

- `hasPlatform(userId, code)`：存在平台 `role_assignment`，且该角色含此权限。
- `hasTenant(userId, tenantId, code)`：成员 `status=active`，租户 `status=active`，租户角色含此权限。
- 每次从库读。

引导：统计 `platform_owner` 的分配数。为 0 且本次登录身份命中 `PILIPALA_ADMIN_BOOTSTRAP_PHONES` 或用户名或邮箱名单时，插入一条平台 `platform_owner`。大于 0 时什么都不做。

`GET /api/v1/auth/me` 增加：`tenant_id`、`tenant_type`、`tenant_roles`（code 数组）、`tenant_permissions`、`platform_permissions`。不要返回其他租户。

---

## 步骤 3：创作站数据与旧后台接口

所有按 `user_id` 查钱包、流水、充值、任务、画布、素材、订阅的 C 端接口，改为当前会话的 `tenant_id`。请求里如果另带租户 id，必须等于会话里的当前租户，否则 `FORBIDDEN`。

提交任务前检查 `job.submit` 和租户 `active`、用户 `active`。冻结、确认、释放的 SQL 条件改为 `tenant_id`，语义仍是冻结、确认、释放，不要改金额公式。

`AdminGuard.requireAdmin()` 改为 `require(code)`。现有接口对应：

| 现有接口 | 权限 |
| --- | --- |
| `GET /api/v1/admin/users` | `user.read` |
| `POST /api/v1/admin/wallets/{userId}/topup` | `wallet.topup` |
| `GET /api/v1/admin/wallets/{userId}/ledger` | `ledger.read` |
| `GET /api/v1/admin/billing/recharge-orders` | `recharge.read` |
| `POST /api/v1/admin/billing/recharge-orders/{id}/refund` | `recharge.refund` |
| `GET /api/v1/admin/jobs` | `job.read`；响应里的 `raw_status` / `raw_body` 仅当还有 `job.provider_raw` 时出现，否则这两个字段为 null |
| `POST /api/v1/admin/jobs/{jobId}/retry` | `job.retry` |
| `GET/PUT /api/v1/admin/i18n-messages` | `i18n.read` / `i18n.write` |
| `GET/PUT /api/v1/admin/models` | `model.read` / `model.write` |
| 积分商品、会员方案已有接口 | 对应的 read / write |

加额和退款的请求体增加必填 `idempotency_key`（1 到 64 字符）和必填 `reason`（1 到 200 字符）。同一操作者、同一动作、同一键返回第一次的结果，不第二次入账。`reason` 写入流水能带的备注字段；该字段若不存在，就写在 `audit_log.summary`，不要为了备注去改金额列。写 `audit_log`。

用户列表里的手机号默认只留后四位。

本步不要新增页面。

---

## 步骤 4：新的管理接口

全部在 `/api/v1/admin/**`。未登录是现有未认证错误。无权限是 `FORBIDDEN`。写操作记 `audit_log`。

- `GET /permissions`：`role.read`。返回第 2 节的码、范围、说明。
- `GET /roles`：`role.read`。含权限码和 `system`。
- `POST /roles`：`role.write`。只创建 `system=false`。`scope` 只能是 `platform` 或 `tenant`。权限码必须已存在且范围一致。
- `PUT /roles/{id}/permissions`：`role.write`。`system=true` 返回 `VALIDATION_ERROR`。
- `DELETE /roles/{id}`：`role.write`。`system=true` 或仍有分配时返回 `VALIDATION_ERROR`。
- `GET /staff`：`staff.read`。有平台角色分配的用户。
- `PUT /staff/{userId}/roles`：`staff.assign_role`。替换该用户的平台角色集合。若结果会导致系统里没有任何 `platform_owner`，返回 `VALIDATION_ERROR` 并不写入。
- `GET /tenants`：`tenant.read`。分页，可按 `type`、`status` 过滤。
- `GET /tenants/{id}/members`：`tenant.member.read`。
- `POST /users/{id}/disable` 与 `/enable`：`user.disable` / `user.enable`。不能封禁自己。封禁不删除钱包和画布。
- `POST /users/{id}/phone-reveal`：`user.read_phone`。返回完整手机号并写审计。
- `POST /jobs/{jobId}/cancel`：`job.cancel`。仅非终态。调用现有取消，让冻结按原状态机释放。终态返回 `VALIDATION_ERROR`。
- Banner 的列表、创建、更新、启用：`banner.read` / `banner.write`。`placement` 只允许两个值。图片上传复用现有 TOS 上传能力，只存对象键。
- `GET /api/v1/banners/current?placement=`：公开，无需登录。返回该广告位里 `enabled` 且当前时间落在开始/结束之内的一条，按 `sort_order`、`created_at` 取第一条。没有则空。
- 字典列表与改 `label`、`sort_order`：`dictionary.read` / `dictionary.write`。拒绝未知 `dict_code` 或 `item_code`。
- `GET /runtime-settings` 与 `PUT`：`runtime.read` / `runtime.write`。只允许第 2 节那两个键。值只允许 `true` 或 `false`。
- `GET /video-products`：`model.read`。返回 `model_product` 的 code、价格、enabled、分组 code。
- `PUT /video-products/{code}/enabled`：`model.write`。只改 `enabled`，不改价格。
- `GET /stats/overview?from&to`：`stats.read`。`from` 与 `to` 必填，跨度最长 31 天。返回：新注册数、激活数（租户第一笔进入 `queued` 的任务）、登录成功去重用户数、按模型的成功与失败加死信（不含取消）、流水按日的 topup / confirm / release 合计。
- `GET /stats/exceptions`：`stats.read`。四类：`succeeded` 但冻结仍未确认；`failed` 或 `dead` 或 `cancelled` 但冻结仍未释放；充值 `paid` 但没有对应入账流水；`running` 且 `updated_at` 早于该 job 的 `gateway_timeout_seconds`（该列为空的不放进这一类）。
- `GET /status`：`status.read`。API 自身为 up；Worker 健康检查 URL 用现有配置，没有配置则返回 `unknown`，不要新加端口约定；NewAPI 对已配置的 base URL 做一次短连接，失败为 down；outbox 中待投递条数；`running` 任务条数。不要重启任何进程。
- `GET /audit-logs`：`audit.read`。
- `GET /exports/ledger` 与 `/exports/recharge-orders`：`ledger.read` / `recharge.read`。`from` 与 `to` 必填，跨度最长 31 天，超过返回 `VALIDATION_ERROR`。响应 `text/csv`。

本步仍然不要做 `admin/` 页面。每个新接口至少一个 MockMvc 或服务测试：有权限成功，无权限 `FORBIDDEN`。加额相同幂等键调用两次，流水只增加一条。

---

## 步骤 5：独立前端 `admin/`

新建 Vite + React + TypeScript + Ant Design 应用，包管理与 `web/` 一致。不要复制画布、Agent、Studio 壳。

开发时把 `/api` 代理到本地 API。令牌存在本站自己的 `localStorage`，请求头 `pilipala-token`。不读取创作站域名的存储。

页面：

1. 登录：手机号验证码，复用现有 `/api/v1/auth/*`。没有平台权限的用户停在「没有运维后台权限」，不跳到创作站。
2. 页眉：环境名，读 `VITE_PILIPALA_ENV_NAME`，只显示，不参与鉴权。
3. 侧栏：按下面的菜单和权限显示。数据来自代码常量，不来自可编辑菜单接口。另有一页「菜单」用同一份常量画成树，标出当前角色能看见的项，只读。

| 菜单 | 权限 |
| --- | --- |
| 概览 | `stats.read` |
| 异常 | `stats.read` |
| 系统状态 | `status.read` |
| 用户与钱包 | `user.read` |
| 充值订单 | `recharge.read` |
| 任务 | `job.read` |
| 租户 | `tenant.read` |
| 员工与角色 | `staff.read` |
| 菜单 | `role.read` |
| 字典 | `dictionary.read` |
| 文案 | `i18n.read` |
| Banner | `banner.read` |
| 模型展示 | `model.read` |
| 视频产品 | `model.read` |
| 积分商品 | `credit_product.read` |
| 会员 | `membership.read` |
| 运行开关 | `runtime.read` |
| 审计 | `audit.read` |

按钮同样按权限隐藏。加额、退款、封禁、取消、改角色在界面上要填写原因；加额和退款还要带新生成的幂等键。手机号默认打码，有 `user.read_phone` 才显示「查看」按钮。

没有 `job.provider_raw` 时，任务详情不展示上游原文区域。

仓库里加一份 `deploy/nginx/admin.conf.example`：`server_name admin.pilipala.art`；静态根 `/opt/pilipala/admin-dist`；`location /api/` 反代到与创作站相同的 API 上游。示例里不要写真实 IP 和密钥。

---

## 步骤 6：创作站

从 `web/src/router.tsx` 去掉 `/admin`。删除只被该路由使用的页面组装；仍被 API 客户端使用的函数留下，改由 `admin/` 使用，避免两套客户端各写一份之后分叉。若移动文件，`web` 里不再出现嵌在 Studio 里的运维页。

创作站里原「运维后台」入口改为外链 `https://admin.pilipala.art`，仅当 `me.platform_permissions` 非空时显示。

首页活动条和会员页顶部改为读 `GET /api/v1/banners/current`。没有数据时不显示条。不要再读 `studio.promo.*` 作为展示源。`app_runtime_setting` 里的旧键可以留在库里，代码不再读它们来画 Banner。

创作站继续不做租户切换器。

---

## 步骤 7：收尾

- `CHANGELOG.md` 的 `Unreleased` 加一条 `[新增]`，一句话说明独立管理后台、租户钱包和平台权限。
- `docs/engineering/feature-briefs/README.md` 本行状态改为「实现中」，不要改成已验收。
- 更新 `data-model/billing.md` 里「钱包属于用户」的句子，改为属于租户。
- 跑 `./mvnw -pl api -am test` 和 admin、web 已有的测试命令。不要为了让测试通过去放宽权限。

## 非范围

写了也算做错：

- 团队邀请、席位、组织创建接口、创作站租户切换器。
- NewAPI 页面或 NewAPI 表结构。给网关请求加 `tenant_id`。
- 直接 `UPDATE wallet.available`。
- 第二次短信验证、代登录、发票、内容审核、广告主表、客户端埋点入库。
- 在界面上新增权限码、新增任务状态、新增广告位、编辑种子角色的权限、删除最后一个 `platform_owner`。
- 修改已有 Flyway 文件。部署生产。自行 merge。
- `docs/content/**`。

## 验收

- [ ] 无 `platform_owner` 时，引导名单内的用户登录后成为 `platform_owner`；已有所有者时，名单内的另一个用户登录不会自动成为所有者。
- [ ] `platform_support` 能看任务原文和打码手机号的揭示，调用退款接口得到 `FORBIDDEN`。
- [ ] `platform_finance` 加额两次相同幂等键，只有一条 `topup`。退款可用。改模型得到 `FORBIDDEN`。
- [ ] `platform_operator` 能下架展示模型和视频产品，不能看到上游原文，不能加额。
- [ ] 种子角色的权限接口拒绝修改。删到最后一个 `platform_owner` 被拒绝。
- [ ] 新注册用户有一个个人租户、一只钱包、`tenant_owner`。用别人的 `tenant_id` 提交任务被拒绝。
- [ ] 封禁后不能登录。取消一条 `running` 任务后冻结被释放，`available` 不是被手写覆盖的。
- [ ] 公开 Banner 接口只返回一条当前有效记录。创作站不再渲染 Studio 内的 `/admin`。
- [ ] 统计在缺少客户端埋点时仍返回注册、激活、登录、模型成败和流水合计。异常列表能列出「成功未确认」。
- [ ] Worker 发给 NewAPI 的 JSON 没有 `tenant_id` 和用户 id。
- [ ] 仓库没有真实密钥。没有改 `docs/content/**`。

## 给实现模型的约束

- 只做被派到的那一个步骤。
- 权限码、字段、31 天导出上限、两个广告位、两个运行时键，都以上文为准。
- 发现分支里没有本步骤要改的类，停下来列出缺失的文件，不要换一套自己的包名。
- 不要把经验超时写进配置。
