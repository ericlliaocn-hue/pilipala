-- M4 独立管理后台：租户边界 + 平台/租户 RBAC。
-- Ref: docs/engineering/feature-briefs/2026-11-01-admin-console.md（步骤 1）
-- 字段级照抄源：docs/engineering/data-model/tenancy.md
--
-- 本迁移做四件事：
--   1. 建租户 / RBAC / 日志 / Banner / 字典 / 幂等新表；
--   2. users 加 status，删 users.role；
--   3. 钱包改挂租户（wallet.tenant_id 唯一非空，删 wallet.user_id）；
--   4. 给业务表加 tenant_id 并按用户回填到个人租户 + 种权限 / 角色 / 字典。
--
-- 硬约束：
--   - 钱包属于租户。余额只能靠流水变，禁止直接写 available。
--   - tenant_id 归属为准，业务表保留 user_id 只表示操作者。
--   - 种子角色 system=true：不能删、不能改权限集合。
--   - 权限码全集以 brief 第 2 节为准，这里逐项落地，不用通配符。
--
-- 与 brief 的一处偏差（必要且已记录在 data-model/tenancy.md）：
--   brief 写「permission：code TEXT PK」与「权限的 scope 与角色的 scope 相同」，
--   但第 2 节的权限码全集里 job.read 同时出现在平台清单和租户清单。
--   两者不能同时成立：同一码在两个范围各有一行，所以权限的身份是 (code, scope)，
--   role_permission 多带一列 permission_scope 指向它。权限码本身没有新增或改名。

-- ============================================================================
-- 1. 新表
-- ============================================================================

CREATE TABLE tenant (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    type       TEXT        NOT NULL CHECK (type IN ('personal', 'organization')),
    name       TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX tenant_type_status_idx ON tenant (type, status);

CREATE TABLE tenant_membership (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenant (id),
    user_id    UUID        NOT NULL REFERENCES users (id),
    status     TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tenant_membership_tenant_user_key UNIQUE (tenant_id, user_id)
);

CREATE INDEX tenant_membership_user_id_idx ON tenant_membership (user_id);

-- 权限码全集里 job.read 同时出现在平台清单和租户清单，
-- 而角色分配要求「权限的 scope 与角色的 scope 相同」。
-- 所以权限的身份是 (code, scope)：同一码在两个范围各有一行，
-- 而不是把 job.read 塞进其中一边、让另一边拿不到。
CREATE TABLE permission (
    code        TEXT NOT NULL,
    scope       TEXT NOT NULL CHECK (scope IN ('platform', 'tenant')),
    description TEXT NOT NULL,
    PRIMARY KEY (code, scope)
);

CREATE TABLE role (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code       TEXT        NOT NULL UNIQUE,
    name       TEXT        NOT NULL,
    scope      TEXT        NOT NULL CHECK (scope IN ('platform', 'tenant')),
    system     BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permission (
    role_id          UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_code  TEXT NOT NULL,
    permission_scope TEXT NOT NULL CHECK (permission_scope IN ('platform', 'tenant')),
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT role_permission_permission_fkey
        FOREIGN KEY (permission_code, permission_scope) REFERENCES permission (code, scope)
);

CREATE TABLE role_assignment (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    role_id    UUID        NOT NULL REFERENCES role (id),
    tenant_id  UUID        NULL REFERENCES tenant (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 平台分配：一个用户同一平台角色只留一条。
CREATE UNIQUE INDEX role_assignment_platform_key
    ON role_assignment (user_id, role_id) WHERE tenant_id IS NULL;

-- 租户分配：同一用户同一租户内的同一角色只留一条。
CREATE UNIQUE INDEX role_assignment_tenant_key
    ON role_assignment (user_id, role_id, tenant_id) WHERE tenant_id IS NOT NULL;

CREATE INDEX role_assignment_tenant_id_idx ON role_assignment (tenant_id);

CREATE TABLE login_log (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NULL,
    tenant_id  UUID        NULL,
    result     TEXT        NOT NULL CHECK (result IN ('success', 'failure')),
    ip         TEXT        NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX login_log_user_created_idx ON login_log (user_id, created_at DESC);
CREATE INDEX login_log_created_idx ON login_log (created_at DESC);

-- 只追加：不提供 UPDATE / DELETE 路径。
CREATE TABLE audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID        NOT NULL,
    permission_code TEXT        NOT NULL,
    tenant_id       UUID        NULL,
    target_type     TEXT        NOT NULL,
    target_id       TEXT        NOT NULL,
    idempotency_key TEXT        NULL,
    summary         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_created_idx ON audit_log (created_at DESC);
CREATE INDEX audit_log_actor_created_idx ON audit_log (actor_user_id, created_at DESC);

-- 只有两个广告位；campaign_id 只是预留键，没有广告表。
CREATE TABLE banner (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    placement        TEXT        NOT NULL CHECK (placement IN ('home_promo', 'membership')),
    title            TEXT        NOT NULL,
    subtitle         TEXT        NULL,
    image_object_key TEXT        NULL,
    link_url         TEXT        NULL,
    starts_at        TIMESTAMPTZ NULL,
    ends_at          TIMESTAMPTZ NULL,
    sort_order       INT         NOT NULL DEFAULT 0,
    enabled          BOOLEAN     NOT NULL DEFAULT false,
    campaign_id      UUID        NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX banner_placement_idx ON banner (placement, enabled, sort_order, created_at);

-- 只能改显示名与排序：不能新增字典项，也不能新增任务状态 / 流水类型。
CREATE TABLE dictionary_entry (
    dict_code  TEXT        NOT NULL,
    item_code  TEXT        NOT NULL,
    locale     TEXT        NOT NULL CHECK (locale IN ('zh', 'en')),
    label      TEXT        NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (dict_code, item_code, locale)
);

CREATE TABLE admin_idempotency (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID        NOT NULL,
    action          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    response_json   JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT admin_idempotency_actor_action_key UNIQUE (actor_user_id, action, idempotency_key)
);

-- ============================================================================
-- 2. 范围守卫：权限的 scope 必须与角色的 scope 一致；租户角色必须带 tenant_id
-- ============================================================================

CREATE OR REPLACE FUNCTION guard_role_permission_scope() RETURNS trigger AS $$
DECLARE
    v_role_scope TEXT;
BEGIN
    SELECT scope INTO v_role_scope FROM role WHERE id = NEW.role_id;
    IF v_role_scope IS NULL THEN
        RAISE EXCEPTION 'role_permission 引用了不存在的角色';
    END IF;
    IF v_role_scope <> NEW.permission_scope THEN
        RAISE EXCEPTION '权限 % 的 scope(%) 与角色 scope(%) 不一致',
            NEW.permission_code, NEW.permission_scope, v_role_scope;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER role_permission_scope_guard
    BEFORE INSERT OR UPDATE ON role_permission
    FOR EACH ROW EXECUTE FUNCTION guard_role_permission_scope();

CREATE OR REPLACE FUNCTION guard_role_assignment_scope() RETURNS trigger AS $$
DECLARE
    v_scope TEXT;
BEGIN
    SELECT scope INTO v_scope FROM role WHERE id = NEW.role_id;
    IF v_scope IS NULL THEN
        RAISE EXCEPTION 'role_assignment 引用了不存在的角色';
    END IF;
    IF v_scope = 'tenant' AND NEW.tenant_id IS NULL THEN
        RAISE EXCEPTION '租户范围的角色分配必须带 tenant_id';
    END IF;
    IF v_scope = 'platform' AND NEW.tenant_id IS NOT NULL THEN
        RAISE EXCEPTION '平台范围的角色分配不能带 tenant_id';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER role_assignment_scope_guard
    BEFORE INSERT OR UPDATE ON role_assignment
    FOR EACH ROW EXECUTE FUNCTION guard_role_assignment_scope();

-- ============================================================================
-- 3. users：加 status、删 role
-- ============================================================================

ALTER TABLE users
    ADD COLUMN status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled'));

-- ============================================================================
-- 4. 存量用户 → personal 租户 + 成员
--    租户名优先 username，其次手机号后四位；两者都没有时用 id 尾部兜底。
-- ============================================================================

WITH personal AS MATERIALIZED (
    SELECT u.id AS user_id,
           gen_random_uuid() AS tenant_id,
           COALESCE(
               NULLIF(u.username, ''),
               NULLIF(right(u.phone, 4), ''),
               'user-' || right(u.id::text, 8)
           ) AS tenant_name
    FROM users u
),
created_tenant AS (
    INSERT INTO tenant (id, type, name)
    SELECT tenant_id, 'personal', tenant_name FROM personal
    RETURNING id
)
INSERT INTO tenant_membership (tenant_id, user_id, status)
SELECT p.tenant_id, p.user_id, 'active'
FROM personal p
WHERE EXISTS (SELECT 1 FROM created_tenant ct WHERE ct.id = p.tenant_id);

-- ============================================================================
-- 5. 钱包改挂租户
-- ============================================================================

ALTER TABLE wallet
    ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);

DO $$
DECLARE
    v_wallet_rows BIGINT;
    v_after       BIGINT;
    v_missing     BIGINT;
BEGIN
    SELECT count(*) INTO v_wallet_rows FROM wallet;

    UPDATE wallet w
    SET tenant_id = t.id
    FROM tenant t
    JOIN tenant_membership m ON m.tenant_id = t.id
    WHERE t.type = 'personal'
      AND m.status = 'active'
      AND m.user_id = w.user_id;

    SELECT count(*) INTO v_missing FROM wallet WHERE tenant_id IS NULL;
    IF v_missing > 0 THEN
        RAISE EXCEPTION 'wallet 有 % 行未回填 tenant_id', v_missing;
    END IF;

    SELECT count(*) INTO v_after FROM wallet;
    IF v_after <> v_wallet_rows THEN
        RAISE EXCEPTION 'wallet 行数变了：回填前 % 行，回填后 % 行', v_wallet_rows, v_after;
    END IF;
END $$;

ALTER TABLE wallet ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE wallet DROP CONSTRAINT wallet_user_id_key;
CREATE UNIQUE INDEX wallet_tenant_id_key ON wallet (tenant_id);

ALTER TABLE wallet DROP COLUMN user_id;

-- ============================================================================
-- 6. 业务表加 tenant_id（先可空 → 按 user_id 回填到个人租户 → 设非空）
--    这些表保留 user_id，只表示操作者；归属以 tenant_id 为准。
-- ============================================================================

ALTER TABLE wallet_hold      ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE wallet_ledger   ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE recharge_order  ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE job             ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE canvas_project  ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE user_asset      ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE user_subscription ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);
ALTER TABLE credit_grant    ADD COLUMN tenant_id UUID NULL REFERENCES tenant (id);

UPDATE wallet_hold h
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = h.user_id;

UPDATE wallet_ledger l
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = l.user_id;

UPDATE recharge_order r
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = r.user_id;

UPDATE job j
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = j.user_id;

UPDATE canvas_project p
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = p.user_id;

UPDATE user_asset a
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = a.user_id;

UPDATE user_subscription s
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = s.user_id;

UPDATE credit_grant g
SET tenant_id = t.id
FROM tenant t
JOIN tenant_membership m ON m.tenant_id = t.id
WHERE t.type = 'personal' AND m.status = 'active' AND m.user_id = g.user_id;

-- 同一用户的多条订阅全部挂到他的个人租户：不合并、不删除。
DO $$
DECLARE
    v_missing TEXT;
BEGIN
    SELECT string_agg(src, '、') INTO v_missing
    FROM (
        SELECT 'wallet' AS src WHERE EXISTS (SELECT 1 FROM wallet WHERE tenant_id IS NULL)
        UNION ALL SELECT 'wallet_hold' WHERE EXISTS (SELECT 1 FROM wallet_hold WHERE tenant_id IS NULL)
        UNION ALL SELECT 'wallet_ledger' WHERE EXISTS (SELECT 1 FROM wallet_ledger WHERE tenant_id IS NULL)
        UNION ALL SELECT 'recharge_order' WHERE EXISTS (SELECT 1 FROM recharge_order WHERE tenant_id IS NULL)
        UNION ALL SELECT 'job' WHERE EXISTS (SELECT 1 FROM job WHERE tenant_id IS NULL)
        UNION ALL SELECT 'canvas_project' WHERE EXISTS (SELECT 1 FROM canvas_project WHERE tenant_id IS NULL)
        UNION ALL SELECT 'user_asset' WHERE EXISTS (SELECT 1 FROM user_asset WHERE tenant_id IS NULL)
        UNION ALL SELECT 'user_subscription' WHERE EXISTS (SELECT 1 FROM user_subscription WHERE tenant_id IS NULL)
        UNION ALL SELECT 'credit_grant' WHERE EXISTS (SELECT 1 FROM credit_grant WHERE tenant_id IS NULL)
    ) missing_rows;

    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION '以下表仍有未回填 tenant_id 的行：%', v_missing;
    END IF;
END $$;

ALTER TABLE wallet_hold        ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE wallet_ledger     ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE recharge_order    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE job               ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE canvas_project    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE user_asset        ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE user_subscription ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE credit_grant      ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX wallet_hold_tenant_id_created_at_idx ON wallet_hold (tenant_id, created_at);
CREATE INDEX wallet_ledger_tenant_id_created_at_idx ON wallet_ledger (tenant_id, created_at);
CREATE INDEX recharge_order_tenant_id_created_at_idx ON recharge_order (tenant_id, created_at);
CREATE INDEX job_tenant_id_created_at_idx ON job (tenant_id, created_at);
CREATE INDEX canvas_project_tenant_id_created_at_idx ON canvas_project (tenant_id, created_at);
CREATE INDEX user_asset_tenant_id_created_at_idx ON user_asset (tenant_id, created_at);
CREATE INDEX user_subscription_tenant_id_created_at_idx ON user_subscription (tenant_id, created_at);
CREATE INDEX credit_grant_tenant_id_created_at_idx ON credit_grant (tenant_id, created_at);

-- users.role 已被 RBAC 取代：回填完成后再删。
ALTER TABLE users DROP COLUMN role;

-- ============================================================================
-- 7. 种子：权限码全集
-- ============================================================================

INSERT INTO permission (code, scope, description) VALUES
    ('staff.read',          'platform', '查看运维员工与平台角色分配'),
    ('staff.assign_role',   'platform', '调整运维员工的平台角色'),
    ('role.read',           'platform', '查看角色与权限码'),
    ('role.write',          'platform', '新建角色并调整其权限集合'),
    ('tenant.read',         'platform', '查看租户列表与详情'),
    ('tenant.member.read',  'platform', '查看租户成员'),
    ('user.read',           'platform', '查看用户列表与详情'),
    ('user.disable',        'platform', '停用用户账号'),
    ('user.enable',         'platform', '恢复已停用的用户账号'),
    ('user.read_phone',     'platform', '查看用户完整手机号'),
    ('wallet.read',         'platform', '查看租户钱包余额'),
    ('wallet.topup',        'platform', '为租户钱包加额'),
    ('ledger.read',         'platform', '查看钱包流水'),
    ('recharge.read',       'platform', '查看充值订单'),
    ('recharge.refund',     'platform', '退还充值订单'),
    ('job.read',            'platform', '查看生成任务'),
    ('job.retry',           'platform', '重试生成任务'),
    ('job.cancel',          'platform', '取消生成任务'),
    ('job.provider_raw',    'platform', '查看上游原始响应'),
    ('model.read',          'platform', '查看模型展示配置与视频产品'),
    ('model.write',         'platform', '调整模型展示配置与视频产品上下架'),
    ('i18n.read',           'platform', '查看动态文案'),
    ('i18n.write',          'platform', '修改动态文案'),
    ('credit_product.read', 'platform', '查看积分商品'),
    ('credit_product.write','platform', '调整积分商品'),
    ('membership.read',     'platform', '查看会员方案'),
    ('membership.write',    'platform', '调整会员方案'),
    ('banner.read',         'platform', '查看 Banner 广告位'),
    ('banner.write',        'platform', '编辑 Banner 广告位'),
    ('dictionary.read',     'platform', '查看字典条目'),
    ('dictionary.write',    'platform', '修改字典显示名与排序'),
    ('runtime.read',        'platform', '查看运行时开关'),
    ('runtime.write',       'platform', '修改运行时开关'),
    ('stats.read',          'platform', '查看统计与异常报表'),
    ('status.read',         'platform', '查看系统运行状态'),
    ('audit.read',          'platform', '查看审计日志'),
    ('member.invite',       'tenant',   '邀请成员加入租户'),
    ('member.remove',       'tenant',   '移除租户成员'),
    ('member.assign_role',  'tenant',   '分配租户内角色'),
    ('billing.recharge',    'tenant',   '发起租户充值'),
    ('billing.read',        'tenant',   '查看租户账单与余额'),
    ('project.read',        'tenant',   '查看租户项目'),
    ('project.write',       'tenant',   '编辑租户项目'),
    ('asset.read',          'tenant',   '查看租户素材'),
    ('asset.write',         'tenant',   '管理租户素材'),
    ('job.submit',          'tenant',   '提交生成任务'),
    ('job.read',            'tenant',   '查看租户生成任务')
ON CONFLICT (code, scope) DO NOTHING;

-- ============================================================================
-- 8. 种子：8 个角色（system=true，不可删、不可改权限集合）
-- ============================================================================

INSERT INTO role (code, name, scope, system) VALUES
    ('platform_owner',    '平台超级管理员', 'platform', TRUE),
    ('platform_support',  '平台客服',       'platform', TRUE),
    ('platform_finance',  '平台财务',       'platform', TRUE),
    ('platform_operator', '平台运营',       'platform', TRUE),
    ('tenant_owner',      '租户所有者',     'tenant',   TRUE),
    ('tenant_admin',      '租户管理员',     'tenant',   TRUE),
    ('tenant_billing',    '租户财务',       'tenant',   TRUE),
    ('tenant_member',     '租户成员',       'tenant',   TRUE)
ON CONFLICT (code) DO NOTHING;

-- platform_owner：全部平台权限
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, p.code, p.scope
FROM role r
CROSS JOIN permission p
WHERE r.code = 'platform_owner' AND p.scope = 'platform'
ON CONFLICT DO NOTHING;

-- platform_support
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, v.code, 'platform'
FROM role r
CROSS JOIN (VALUES
    ('user.read'), ('user.read_phone'), ('tenant.read'), ('tenant.member.read'),
    ('job.read'), ('job.provider_raw'), ('ledger.read'), ('recharge.read'),
    ('stats.read'), ('status.read')
) AS v(code)
WHERE r.code = 'platform_support'
ON CONFLICT DO NOTHING;

-- platform_finance
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, v.code, 'platform'
FROM role r
CROSS JOIN (VALUES
    ('user.read'), ('user.read_phone'), ('wallet.read'), ('wallet.topup'),
    ('ledger.read'), ('recharge.read'), ('recharge.refund'), ('stats.read')
) AS v(code)
WHERE r.code = 'platform_finance'
ON CONFLICT DO NOTHING;

-- platform_operator
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, v.code, 'platform'
FROM role r
CROSS JOIN (VALUES
    ('model.read'), ('model.write'), ('i18n.read'), ('i18n.write'),
    ('credit_product.read'), ('credit_product.write'), ('membership.read'), ('membership.write'),
    ('banner.read'), ('banner.write'), ('dictionary.read'), ('dictionary.write'),
    ('runtime.read'), ('runtime.write'), ('job.read'), ('job.retry'),
    ('stats.read'), ('status.read')
) AS v(code)
WHERE r.code = 'platform_operator'
ON CONFLICT DO NOTHING;

-- tenant_owner / tenant_admin：全部租户权限
-- 两者权限集合相同；「不能移除本租户的 tenant_owner」是接口层约束，不在这里表达。
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, p.code, p.scope
FROM role r
CROSS JOIN permission p
WHERE r.code IN ('tenant_owner', 'tenant_admin') AND p.scope = 'tenant'
ON CONFLICT DO NOTHING;

-- tenant_billing
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, v.code, 'tenant'
FROM role r
CROSS JOIN (VALUES
    ('billing.recharge'), ('billing.read'), ('job.read'), ('project.read'), ('asset.read')
) AS v(code)
WHERE r.code = 'tenant_billing'
ON CONFLICT DO NOTHING;

-- tenant_member
INSERT INTO role_permission (role_id, permission_code, permission_scope)
SELECT r.id, v.code, 'tenant'
FROM role r
CROSS JOIN (VALUES
    ('project.read'), ('project.write'), ('asset.read'), ('asset.write'), ('job.submit'), ('job.read')
) AS v(code)
WHERE r.code = 'tenant_member'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 9. 种子：字典（只覆盖已有枚举；错误码文案仍在 i18n_message）
-- ============================================================================

INSERT INTO dictionary_entry (dict_code, item_code, locale, label, sort_order) VALUES
    ('job_status', 'created',   'zh', '待创建', 10),
    ('job_status', 'created',   'en', 'Created', 10),
    ('job_status', 'queued',    'zh', '已入队', 20),
    ('job_status', 'queued',    'en', 'Queued', 20),
    ('job_status', 'running',   'zh', '执行中', 30),
    ('job_status', 'running',   'en', 'Running', 30),
    ('job_status', 'succeeded', 'zh', '已成功', 40),
    ('job_status', 'succeeded', 'en', 'Succeeded', 40),
    ('job_status', 'failed',    'zh', '已失败', 50),
    ('job_status', 'failed',    'en', 'Failed', 50),
    ('job_status', 'cancelled', 'zh', '已取消', 60),
    ('job_status', 'cancelled', 'en', 'Cancelled', 60),
    ('job_status', 'dead',      'zh', '已死信', 70),
    ('job_status', 'dead',      'en', 'Dead', 70),
    ('ledger_type', 'topup',   'zh', '充值', 10),
    ('ledger_type', 'topup',   'en', 'Top-up', 10),
    ('ledger_type', 'freeze',  'zh', '冻结', 20),
    ('ledger_type', 'freeze',  'en', 'Freeze', 20),
    ('ledger_type', 'confirm', 'zh', '确认扣费', 30),
    ('ledger_type', 'confirm', 'en', 'Confirm', 30),
    ('ledger_type', 'release', 'zh', '释放冻结', 40),
    ('ledger_type', 'release', 'en', 'Release', 40),
    ('ledger_type', 'refund',  'zh', '退款', 50),
    ('ledger_type', 'refund',  'en', 'Refund', 50),
    ('ledger_type', 'expire',  'zh', '过期', 60),
    ('ledger_type', 'expire',  'en', 'Expire', 60),
    ('recharge_order_status', 'created',     'zh', '已创建', 10),
    ('recharge_order_status', 'created',     'en', 'Created', 10),
    ('recharge_order_status', 'pending_pay', 'zh', '待支付', 20),
    ('recharge_order_status', 'pending_pay', 'en', 'Pending payment', 20),
    ('recharge_order_status', 'paid',        'zh', '已支付', 30),
    ('recharge_order_status', 'paid',        'en', 'Paid', 30),
    ('recharge_order_status', 'failed',      'zh', '支付失败', 40),
    ('recharge_order_status', 'failed',      'en', 'Failed', 40),
    ('recharge_order_status', 'closed',      'zh', '已关闭', 50),
    ('recharge_order_status', 'closed',      'en', 'Closed', 50),
    ('recharge_order_status', 'refunded',    'zh', '已退款', 60),
    ('recharge_order_status', 'refunded',    'en', 'Refunded', 60),
    ('tenant_type', 'personal',     'zh', '个人租户', 10),
    ('tenant_type', 'personal',     'en', 'Personal', 10),
    ('tenant_type', 'organization', 'zh', '组织租户', 20),
    ('tenant_type', 'organization', 'en', 'Organization', 20),
    ('capability', 'image.generate', 'zh', '图片生成', 10),
    ('capability', 'image.generate', 'en', 'Image generation', 10),
    ('capability', 'video.generate', 'zh', '视频生成', 20),
    ('capability', 'video.generate', 'en', 'Video generation', 20)
ON CONFLICT (dict_code, item_code, locale) DO NOTHING;
