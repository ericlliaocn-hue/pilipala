export type MenuEntry = { key: string; label: string; permission: string };

export const MENU: MenuEntry[] = [
  { key: "overview", label: "概览", permission: "stats.read" },
  { key: "exceptions", label: "异常", permission: "stats.read" },
  { key: "status", label: "系统状态", permission: "status.read" },
  { key: "users", label: "用户与钱包", permission: "user.read" },
  { key: "recharges", label: "充值订单", permission: "recharge.read" },
  { key: "jobs", label: "任务", permission: "job.read" },
  { key: "tenants", label: "租户", permission: "tenant.read" },
  { key: "staff", label: "员工与角色", permission: "staff.read" },
  { key: "menu", label: "菜单", permission: "role.read" },
  { key: "dictionaries", label: "字典", permission: "dictionary.read" },
  { key: "i18n", label: "文案", permission: "i18n.read" },
  { key: "banners", label: "Banner", permission: "banner.read" },
  { key: "models", label: "模型展示", permission: "model.read" },
  { key: "video", label: "视频产品", permission: "model.read" },
  { key: "credit-products", label: "积分商品", permission: "credit_product.read" },
  { key: "membership", label: "会员", permission: "membership.read" },
  { key: "runtime", label: "运行开关", permission: "runtime.read" },
  { key: "audit", label: "审计", permission: "audit.read" },
];

export function visibleMenu(permissions: readonly string[]) {
  return MENU.filter((item) => permissions.includes(item.permission));
}

export function canEnterAdmin(permissions: readonly string[] | null | undefined) {
  return Array.isArray(permissions) && permissions.length > 0;
}

export function createIdempotencyKey() {
  return crypto.randomUUID().replaceAll("-", "");
}

export function topupRequestBody(amount: number, reason: string) {
  return { amount, idempotency_key: createIdempotencyKey(), reason };
}

export function bannerRequestBody(values: Record<string, any>) {
  return {
    placement: values.placement,
    title: values.title,
    subtitle: values.subtitle || null,
    image_object_key: values.image_object_key || null,
    link_url: values.link_url || null,
    starts_at: values.starts_at || null,
    ends_at: values.ends_at || null,
    sort_order: values.sort_order || 0,
    campaign_id: values.campaign_id || null,
    enabled: values.enabled === true || values.enabled === "true",
  };
}
