-- M4 独立管理后台（步骤 2）：存量补 tenant_owner、存量补空钱包、账号停用文案。
-- Ref: docs/engineering/feature-briefs/2026-11-01-admin-console.md（步骤 2）
-- 字段级照抄源：docs/engineering/data-model/tenancy.md
--
-- 本迁移只做三件事，不改 V24 已建的表结构，也不新建表：
--   1. 步骤 1 给每个存量用户建了个人租户与 active 成员，但没有写 role_assignment；
--      这里给「在该租户上还没有任何角色分配」的存量成员补 tenant_owner。
--   2. 还没有钱包的个人租户补一只 0 余额钱包。
--   3. ACCOUNT_DISABLED 的中英文案（errors.md 里没有能表达「账号已停用」的码）。
--
-- 硬约束：
--   - 已有租户角色的成员不改角色，也不再建一个租户。
--   - 钱包只写 tenant_id：这一列唯一且非空，user_id 列已在本计划步骤 1 删除。

-- ============================================================================
-- 1. 存量成员补 tenant_owner（只补在该 tenant_id 上还没有任何角色分配的）
-- ============================================================================

INSERT INTO role_assignment (user_id, role_id, tenant_id)
SELECT m.user_id, r.id, m.tenant_id
FROM tenant_membership m
JOIN tenant t ON t.id = m.tenant_id
CROSS JOIN role r
WHERE m.status = 'active'
  AND t.type = 'personal'
  AND r.code = 'tenant_owner'
  AND NOT EXISTS (
      SELECT 1
      FROM role_assignment ra
      WHERE ra.user_id = m.user_id AND ra.tenant_id = m.tenant_id
  )
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 2. 存量补空钱包：缺 wallet 行的 personal 租户补一只 0 余额钱包
-- ============================================================================

INSERT INTO wallet (tenant_id, available, frozen)
SELECT t.id, 0, 0
FROM tenant t
WHERE t.type = 'personal'
  AND NOT EXISTS (SELECT 1 FROM wallet w WHERE w.tenant_id = t.id);

-- ============================================================================
-- 3. ACCOUNT_DISABLED 文案（errors.md 新增的唯一错误码）
-- ============================================================================

INSERT INTO i18n_message (key, locale, template) VALUES
    ('ACCOUNT_DISABLED', 'zh', '账号已停用。'),
    ('ACCOUNT_DISABLED', 'en', 'This account has been disabled.')
ON CONFLICT (key, locale) DO NOTHING;
