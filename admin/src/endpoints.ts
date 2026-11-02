import { post, put, request, qs } from "./api";

type Row = Record<string, any>;

/**
 * 运维后台接口：路径、参数与返回结构只在这里定义一处。
 *
 * <p>这份客户端原先放在创作站 `web/` 里，创作站去掉内嵌后台后由 admin/ 接手，
 * 两边不再各写一份之后分叉。令牌仍在 admin 自己的 localStorage 里（见 ./api）。
 */

export type AdminUser = {
  user_id: string;
  username: string;
  locale: string | null;
  available: number;
  frozen: number;
  status?: string;
  created_at: string;
  updated_at: string;
};

export type AdminLedgerEntry = {
  id: string;
  user_id: string;
  type: string;
  amount: number;
  hold_id: string | null;
  job_id: string | null;
  created_at: string;
};

export type AdminJob = {
  job_id: string;
  user_id: string;
  username: string;
  status: string;
  capability: string;
  model: string;
  prompt: string;
  attempt: number;
  max_attempts: number;
  hold_id: string | null;
  hold_status: string | null;
  error_code: string | null;
  created_at: string;
  updated_at: string;
  steps?: Row[];
  provider_tasks?: Row[];
};

export type AdminI18nMessage = {
  id: string;
  key: string;
  locale: string;
  template: string;
  updated_at: string;
};

export type AdminModelDisplayConfig = {
  id: string;
  model: string;
  capability: string;
  display_name: string;
  enabled: boolean;
  sort_order: number;
  updated_at: string;
};

export type AdminCreditProduct = {
  id: number;
  code: string;
  tab: string;
  name_zh: string;
  name_en: string;
  price_fen: number;
  credits: number;
  bonus_credits: number;
  enabled: boolean;
  sort_order: number;
};

export type AdminMembershipPlan = {
  id: number;
  code: string;
  name_zh: string;
  name_en: string;
  sort_order: number;
  credits_per_month: number;
  enabled: boolean;
};

export type AdminMembershipPlanPrice = {
  id: number;
  plan_id: number;
  cycle: string;
  variant_code: string;
  credits_per_month: number;
  price_fen: number;
  enabled: boolean;
};

export type AdminWallet = {
  user_id: string;
  available: number;
  frozen: number;
  updated_at: string;
};

export const adminUsersPath = (params?: { q?: string; limit?: number }) =>
  `/api/v1/admin/users${qs({ q: params?.q, limit: params?.limit })}`;

export function fetchAdminUsers(params?: { q?: string; limit?: number }) {
  return request<AdminUser[]>(adminUsersPath(params));
}

export const adminWalletTopupPath = (userId: string) => `/api/v1/admin/wallets/${encodeURIComponent(userId)}/topup`;

/** 加额必须带幂等键与原因，同一操作者同一幂等键只入账一次。 */
export function adminTopUpWallet(userId: string, body: { amount: number; idempotency_key: string; reason: string }) {
  return post<AdminWallet>(adminWalletTopupPath(userId), body);
}

export const adminWalletLedgerPath = (userId: string, limit?: number) =>
  `/api/v1/admin/wallets/${encodeURIComponent(userId)}/ledger${qs({ limit })}`;

export function fetchAdminWalletLedger(userId: string, limit?: number) {
  return request<AdminLedgerEntry[]>(adminWalletLedgerPath(userId, limit));
}

export const adminJobsPath = (params?: { status?: string; user_id?: string; limit?: number }) =>
  `/api/v1/admin/jobs${qs({ status: params?.status, user_id: params?.user_id, limit: params?.limit })}`;

export function fetchAdminJobs(params?: { status?: string; user_id?: string; limit?: number }) {
  return request<AdminJob[]>(adminJobsPath(params));
}

export function adminRetryJob(jobId: string) {
  return post<AdminJob>(`/api/v1/admin/jobs/${encodeURIComponent(jobId)}/retry`);
}

export const adminI18nMessagesPath = (q?: string) => `/api/v1/admin/i18n-messages${qs({ q })}`;

export function fetchAdminI18nMessages(q?: string) {
  return request<AdminI18nMessage[]>(adminI18nMessagesPath(q));
}

export function adminUpsertI18nMessage(input: { key: string; locale: string; template: string }) {
  return put<AdminI18nMessage>("/api/v1/admin/i18n-messages", input);
}

export const adminModelsPath = () => "/api/v1/admin/models";

export function fetchAdminModels() {
  return request<AdminModelDisplayConfig[]>(adminModelsPath());
}

export function adminUpdateModel(
  model: string,
  input: { display_name: string; capability?: string; enabled: boolean; sort_order: number },
) {
  return put<AdminModelDisplayConfig>(`/api/v1/admin/models/${encodeURIComponent(model)}`, input);
}

export const adminCreditProductsPath = (params?: { tab?: string; enabled?: boolean }) =>
  `/api/v1/admin/credit-products${qs({ tab: params?.tab, enabled: params?.enabled == null ? undefined : String(params.enabled) })}`;

export function fetchAdminCreditProducts(params?: { tab?: string; enabled?: boolean }) {
  return request<AdminCreditProduct[]>(adminCreditProductsPath(params));
}

export function adminSetCreditProductEnabled(id: number, enabled: boolean) {
  return post<AdminCreditProduct>(`/api/v1/admin/credit-products/${id}/${enabled ? "enable" : "disable"}`);
}

export const adminMembershipPlansPath = () => "/api/v1/admin/membership-plans";

export function fetchAdminMembershipPlans() {
  return request<AdminMembershipPlan[]>(adminMembershipPlansPath());
}

export const adminMembershipPlanPricesPath = (planId?: number) =>
  `/api/v1/admin/membership-plans/prices${qs({ plan_id: planId })}`;

export function fetchAdminMembershipPlanPrices(planId?: number) {
  return request<AdminMembershipPlanPrice[]>(adminMembershipPlanPricesPath(planId));
}

export function adminSetMembershipPlanEnabled(id: number, enabled: boolean) {
  return post<AdminMembershipPlan>(`/api/v1/admin/membership-plans/${id}/${enabled ? "enable" : "disable"}`);
}

export function adminSetMembershipPlanPriceEnabled(id: number, enabled: boolean) {
  return post<AdminMembershipPlanPrice>(`/api/v1/admin/membership-plans/prices/${id}/${enabled ? "enable" : "disable"}`);
}
