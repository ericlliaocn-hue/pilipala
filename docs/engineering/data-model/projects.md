# C 端云端持久化：`canvas_project` 与 `user_asset`

WP10 落地的两张表。字段级由 [WP10 brief](../feature-briefs/2026-10-24-wp10-cloud-persistence.md) 锁定，
本页是实现者的照抄源。

迁移文件：`api/src/main/resources/db/migration/V6__create_cloud_persistence.sql`（`V1`–`V5` 零改动）。

**这两张表只属于 api 进程**：worker 不碰（ADR 0004）。

## 表 1：`canvas_project`（7 列）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK `DEFAULT gen_random_uuid()` | 项目唯一标识 |
| `user_id` | UUID | NO | FK → `users(id)` | 所属用户；只允许本人读写删；只表示操作者 |
| `tenant_id` | UUID | NO | FK → `tenant.id`；IDX (`tenant_id`, `created_at`) | 归属租户（M4 `V24`） |
| `name` | TEXT | NO | | 项目名称（1..128 字符） |
| `canvas_data` | JSONB | NO | `DEFAULT '{}'::jsonb` | 画布完整快照（`nodes`、`connections`、`viewport`、`backgroundMode`、`showImageInfo`、`chatSessions`、`activeChatId`） |
| `version` | BIGINT | NO | `DEFAULT 1 CHECK (version >= 1)` | 乐观递增版本号，每次 `PUT` 更新自动 `+ 1` |
| `created_at` | TIMESTAMPTZ | NO | `DEFAULT now()` | 创建时间 |
| `updated_at` | TIMESTAMPTZ | NO | `DEFAULT now()` | 最后保存时间 |

索引：

- `idx_canvas_project_user_updated ON canvas_project (user_id, updated_at DESC)`

`canvas_data` 里 **不** 放图片字节：节点图片只放 `storageKey`，字节留在本地存储或 TOS（ADR 0003）。

## 表 2：`user_asset`（8 列）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK `DEFAULT gen_random_uuid()` | 资产唯一标识 |
| `user_id` | UUID | NO | FK → `users(id)` | 所属用户；只表示操作者 |
| `tenant_id` | UUID | NO | FK → `tenant.id`；IDX (`tenant_id`, `created_at`) | 归属租户（M4 `V24`） |
| `project_id` | UUID | YES | FK → `canvas_project(id) ON DELETE SET NULL` | 关联画布项目（可选，删除项目不删资产） |
| `job_id` | UUID | YES | FK → `job(id) ON DELETE SET NULL` | 关联生成任务（`source='generated'` 时指向产生该图的 `job.id`） |
| `source` | TEXT | NO | `CHECK (source IN ('generated', 'uploaded'))` | 资产来源：`generated`（生图落盘）或 `uploaded`（用户上传/登记） |
| `object_key` | TEXT | NO | | 火山 TOS 对象键（如 `anytoken/img/<job_id>/1.png`）；**严禁存预签名 URL 或 Base64** |
| `mime_type` | TEXT | NO | `DEFAULT 'image/png'` | MIME 类型。自动归档时按产物推导：`video.generate`（或 `.mp4` 对象键）写 `video/mp4`，其余写 `image/png`（WP12） |
| `created_at` | TIMESTAMPTZ | NO | `DEFAULT now()` | 创建时间 |

约束与索引：

- `CONSTRAINT uk_user_asset_job_object UNIQUE (job_id, object_key)`（保证同一 `job_id` + `object_key` 多次同步幂等不重）
- `idx_user_asset_user_created ON user_asset (user_id, created_at DESC)`

## 权威与读取地址

- 表里 **只** 存 `object_key`。读地址一律由 api 现签：`TosUrlSigner.signGetUrl(objectKey)`（ADR 0003）。
  **严禁**把带有效期的预签名 URL 当权威源落库——过期后它就是废的。
- `GET /api/v1/assets` 每次都会把当前用户 `status='succeeded'` 的任务输出幂等同步进 `user_asset`，
  再对每条记录现签读地址返回。

## 与本地存储的关系

浏览器本地（localforage）仍是 **离线可用的第一现场**：未登录时前端只读本地，不发未授权请求。
登录后云端是权威副本，本地保留一份缓存。换设备登录后能看到的是 **云端** 的项目、素材与生成历史。

## 相关

- [data-model/README.md](./README.md)：表清单
- [data-model/errors.md](./errors.md)：`PROJECT_NOT_FOUND` / `ASSET_NOT_FOUND`
- [api/gateway-contract.md](../api/gateway-contract.md)：`/api/v1/projects`、`/api/v1/assets`、`/api/v1/jobs/history`
