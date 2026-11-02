# Feature briefs

Brief 是 pilipala 的需求源。Minimax 与人实现都从这里粘贴，不从聊天里重新编造范围。

## 怎么用

1. 复制 [_TEMPLATE.md](./_TEMPLATE.md) 为 `YYYY-MM-DD-<wp-or-slug>.md`。
2. 填完「范围 / 非范围 / 验收 / 非目标」再开工。标了 TBD 的字段级细节不要让实现者猜。
3. 把全文粘贴给 Minimax，并声明必须遵守 `docs/engineering/` 的 ADR 与冻结栈。
4. PR 引用本文件路径；审查只对照 brief。

> **持久化**：工程目标为 MyBatis-Plus（[ADR 0006](../adr/0006-mybatis-plus-persistence.md)）。WP0–WP12 等历史 brief 若仍写 JdbcTemplate /「不引入 ORM」，以 ADR 0006 与 `TECH_STACK.md` 为准，**不必**回改那些历史 brief 正文。

## 现有 brief

| 文件 | WP | 说明 | 状态 |
| --- | --- | --- | --- |
| [2026-10-24-wp0-white-label-pilipala.md](./2026-10-24-wp0-white-label-pilipala.md) | WP0 | — | 已实现，待验收 |
| [2026-10-24-wp1-api-worker-skeleton.md](./2026-10-24-wp1-api-worker-skeleton.md) | WP1 | — | 已实现，待验收 |
| [2026-10-24-wp2-phase1-schema.md](./2026-10-24-wp2-phase1-schema.md) | WP2 | — | 已实现，待验收 |
| [2026-10-24-wp3-auth-users.md](./2026-10-24-wp3-auth-users.md) | WP3 | — | 已实现，待验收 |
| [2026-10-24-wp4-job-outbox-rabbit.md](./2026-10-24-wp4-job-outbox-rabbit.md) | WP4 | — | 已实现，待验收 |
| [2026-10-24-wp5-thin-worker-anytoken.md](./2026-10-24-wp5-thin-worker-anytoken.md) | WP5 | — | 已实现，待验收 |
| [2026-10-24-wp6-prepaid-wallet.md](./2026-10-24-wp6-prepaid-wallet.md) | WP6 | — | 已实现 |
| [2026-10-24-wp7-web-pilipala-api-generation.md](./2026-10-24-wp7-web-pilipala-api-generation.md) | WP7 / M2 | — | 已实现 |
| [2026-10-24-wp8-tos-storage-signed-url.md](./2026-10-24-wp8-tos-storage-signed-url.md) | WP8 / M2 | — | 已实现 |
| [2026-10-24-wp9-friendly-errors-i18n-message.md](./2026-10-24-wp9-friendly-errors-i18n-message.md) | WP9 / M2 | — | 已实现 |
| [2026-10-24-wp10-cloud-persistence.md](./2026-10-24-wp10-cloud-persistence.md) | WP10 / M3 | C 端账号级云端持久化（项目 + 资产 + 历史 + TOS 虚拟主机域名兼容 + Anytoken 真实模型对齐） | 已实现 |
| [2026-10-24-wp11-admin-ops.md](./2026-10-24-wp11-admin-ops.md) | WP11 / M4 | Admin 运维与配置后台（用户/钱包加额 + 任务与 `provider_task` 原文排障 + 动态文案 + 模型上下架） | 已实现 |
| [2026-10-24-wp12-seedance-video-generation.md](./2026-10-24-wp12-seedance-video-generation.md) | WP12 / M2·M3 | Seedance 视频生成全链路（`video.generate` + Anytoken `/v1/videos/*` 三步协议 + TOS `.mp4` 归档 + `/video` 云历史） | 已实现 |
| [2026-10-25-wp13-docs-mybatis-plus-domain.md](./2026-10-25-wp13-docs-mybatis-plus-domain.md) | WP13 | 工程文档对齐：MyBatis-Plus + 领域分包（docs-first；ORM 代码迁移另 WP） | 已确认 / 实现中 |
| [2026-10-25-wp14-mybatis-plus-code-migration.md](./2026-10-25-wp14-mybatis-plus-code-migration.md) | WP14 | MyBatis-Plus 3.5.17 代码迁移：`Jdbc*Repository` → 域内 Mapper（api + worker） | 已确认 / 实现中 |
| [2026-10-25-wp15-analytics-events-reports.md](./2026-10-25-wp15-analytics-events-reports.md) | WP15 | 数据分析：事件字典 v0 + 必出报表 + analytics 域（docs-first；采集可选 Phase 2） | 已确认 / 实现中 |
| [2026-10-25-wp16-recharge-zpay-alipay.md](./2026-10-25-wp16-recharge-zpay-alipay.md) | WP16 | C 端 z-pay 支付宝充值（mapi qrcode）+ 入账/补单/Admin 退款 | 已确认 / 实现中 |
| [2026-10-26-auth-phone-email-identity.md](./2026-10-26-auth-phone-email-identity.md) | WP17 | 手机 / 邮箱身份（OTP + 阿里云短信 + 自建 SMTP + 换绑占用校验） | 已确认 / 实现中 |
| [2026-10-26-auth-ui-ribbon-email-flags.md](./2026-10-26-auth-ui-ribbon-email-flags.md) | WP18 | Concept B 认证弹层 + 邮箱通道库表开关 + SMTP 假成功修复 | 已确认 / 实现中 |
| [2026-10-27-studio-topbar-promo-libtv.md](./2026-10-27-studio-topbar-promo-libtv.md) | Studio chrome | 顶栏企业采购/积分超市/开通会员 + 活动条数字格倒计时，配置表控制 | 已确认 / 实现中 |
| [2026-10-29-wp21-video-multi-channel-gateway.md](./2026-10-29-wp21-video-multi-channel-gateway.md) | WP21 | 视频多渠道路由：NewAPI 网关 + canonical target/route + submit/query 拆步 + 产品级 fallback | 已确认（Phase 0 完成） |
| [2026-11-01-admin-console.md](./2026-11-01-admin-console.md) | M4 | 独立管理后台 + 平台/租户 RBAC + 个人租户边界（团队版界面另开） | 实现中 |

> **编号**：**WP14** = MyBatis-Plus **代码**迁移；**WP15** = 数据分析文档基线；**WP16** = 预付费充值渠道；**WP21** = 视频多渠道路由 + NewAPI 网关。勿混用编号。

上表里的行是 **真实 brief**（已落盘、可整份粘贴给 Minimax），不是占位行。
