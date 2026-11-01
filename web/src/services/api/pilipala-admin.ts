import { pilipalaRequest } from "./pilipala-client";

/**
 * 运维后台 API 客户端。
 *
 * <p>只封装 {@code /api/v1/admin/**} 与 {@code /api/v1/models}：
 * 失败一律由 {@link pilipalaRequest} 抛 {@link PilipalaApiError}，
 * 普通用户访问时 error_code 是 FORBIDDEN，页面据此展示无权限提示。
 *
 * <p>安全边界（ADR 0005）：{@code raw_status} / {@code raw_body} 只出现在
 * {@link PilipalaAdminJob} 里，绝不放进 C 端的任务类型。
 */

export type PilipalaAdminUser = {
    user_id: string;
    username: string;
    locale: string | null;
    available: number;
    frozen: number;
    created_at: string;
    updated_at: string;
};

export type PilipalaAdminLedgerEntry = {
    id: string;
    user_id: string;
    type: "topup" | "freeze" | "confirm" | "release";
    amount: number;
    hold_id: string | null;
    job_id: string | null;
    created_at: string;
};

export type PilipalaAdminJobStep = {
    step_id: string;
    attempt: number;
    status: string;
    error_code: string | null;
    provider_task_id: string | null;
    started_at: string | null;
    ended_at: string | null;
};

export type PilipalaAdminProviderTask = {
    id: string;
    job_step_id: string | null;
    upstream_request_id: string | null;
    raw_status: string | null;
    raw_body: unknown | null;
    created_at: string;
};

export type PilipalaAdminJob = {
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
    steps: PilipalaAdminJobStep[];
    provider_tasks: PilipalaAdminProviderTask[];
};

export type PilipalaI18nMessage = {
    id: string;
    key: string;
    locale: "zh" | "en";
    template: string;
    updated_at: string;
};

export type PilipalaModelDisplayConfig = {
    id: string;
    model: string;
    capability: string;
    display_name: string;
    enabled: boolean;
    sort_order: number;
    updated_at: string;
};

export function fetchAdminUsers(params?: { q?: string; limit?: number }, fetchImpl?: typeof fetch) {
    const query = new URLSearchParams();
    if (params?.q) query.set("q", params.q);
    if (params?.limit != null) query.set("limit", String(params.limit));
    const suffix = query.toString();
    return pilipalaRequest<PilipalaAdminUser[]>(`/api/v1/admin/users${suffix ? `?${suffix}` : ""}`, { fetchImpl });
}

export function adminTopUpWallet(userId: string, amount: number, fetchImpl?: typeof fetch) {
    return pilipalaRequest<{ user_id: string; available: number; frozen: number; updated_at: string }>(`/api/v1/admin/wallets/${encodeURIComponent(userId)}/topup`, {
        method: "POST",
        body: JSON.stringify({ amount }),
        fetchImpl,
    });
}

export function fetchAdminWalletLedger(userId: string, limit?: number, fetchImpl?: typeof fetch) {
    const query = limit != null ? `?limit=${limit}` : "";
    return pilipalaRequest<PilipalaAdminLedgerEntry[]>(`/api/v1/admin/wallets/${encodeURIComponent(userId)}/ledger${query}`, { fetchImpl });
}

export function fetchAdminJobs(params?: { status?: string; userId?: string; limit?: number }, fetchImpl?: typeof fetch) {
    const query = new URLSearchParams();
    if (params?.status) query.set("status", params.status);
    if (params?.userId) query.set("user_id", params.userId);
    if (params?.limit != null) query.set("limit", String(params.limit));
    const suffix = query.toString();
    return pilipalaRequest<PilipalaAdminJob[]>(`/api/v1/admin/jobs${suffix ? `?${suffix}` : ""}`, { fetchImpl });
}

export function adminRetryJob(jobId: string, fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaAdminJob>(`/api/v1/admin/jobs/${encodeURIComponent(jobId)}/retry`, { method: "POST", fetchImpl });
}

export function fetchAdminI18nMessages(q?: string, fetchImpl?: typeof fetch) {
    const query = q ? `?q=${encodeURIComponent(q)}` : "";
    return pilipalaRequest<PilipalaI18nMessage[]>(`/api/v1/admin/i18n-messages${query}`, { fetchImpl });
}

export function adminUpsertI18nMessage(input: { key: string; locale: "zh" | "en"; template: string }, fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaI18nMessage>("/api/v1/admin/i18n-messages", {
        method: "PUT",
        body: JSON.stringify({ key: input.key, locale: input.locale, template: input.template }),
        fetchImpl,
    });
}

export function fetchAdminModels(fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaModelDisplayConfig[]>("/api/v1/admin/models", { fetchImpl });
}

export function adminUpdateModel(model: string, input: { display_name: string; capability?: string; enabled: boolean; sort_order: number }, fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaModelDisplayConfig>(`/api/v1/admin/models/${encodeURIComponent(model)}`, {
        method: "PUT",
        body: JSON.stringify({
            display_name: input.display_name,
            ...(input.capability ? { capability: input.capability } : {}),
            enabled: input.enabled,
            sort_order: input.sort_order,
        }),
        fetchImpl,
    });
}

export function fetchEnabledModels(fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaModelDisplayConfig[]>("/api/v1/models", { fetchImpl });
}

export type PilipalaAdminCreditProduct = {
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
    validity_days?: number | null;
    badge_zh?: string | null;
    meta_json?: string | null;
};

export function fetchAdminCreditProducts(fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaAdminCreditProduct[]>("/api/v1/admin/credit-products", { fetchImpl });
}

export function adminSetCreditProductEnabled(id: number, enabled: boolean, fetchImpl?: typeof fetch) {
    const action = enabled ? "enable" : "disable";
    return pilipalaRequest<PilipalaAdminCreditProduct>(`/api/v1/admin/credit-products/${id}/${action}`, {
        method: "POST",
        fetchImpl,
    });
}

export type PilipalaAdminMembershipPlan = {
    id: number;
    code: string;
    name_zh: string;
    name_en: string;
    sort_order: number;
    credits_per_month: number;
    enabled: boolean;
};

export type PilipalaAdminMembershipPlanPrice = {
    id: number;
    plan_id: number;
    cycle: string;
    variant_code: string;
    credits_per_month: number;
    price_fen: number;
    list_price_fen?: number | null;
    badge_zh?: string | null;
    enabled: boolean;
};

export function fetchAdminMembershipPlans(fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaAdminMembershipPlan[]>("/api/v1/admin/membership-plans", { fetchImpl });
}

export function fetchAdminMembershipPlanPrices(planId?: number, fetchImpl?: typeof fetch) {
    const q = planId != null ? `?plan_id=${planId}` : "";
    return pilipalaRequest<PilipalaAdminMembershipPlanPrice[]>(`/api/v1/admin/membership-plans/prices${q}`, { fetchImpl });
}

export function adminSetMembershipPlanEnabled(id: number, enabled: boolean, fetchImpl?: typeof fetch) {
    const action = enabled ? "enable" : "disable";
    return pilipalaRequest<PilipalaAdminMembershipPlan>(`/api/v1/admin/membership-plans/${id}/${action}`, {
        method: "POST",
        fetchImpl,
    });
}

export function adminSetMembershipPlanPriceEnabled(id: number, enabled: boolean, fetchImpl?: typeof fetch) {
    const action = enabled ? "enable" : "disable";
    return pilipalaRequest<PilipalaAdminMembershipPlanPrice>(`/api/v1/admin/membership-plans/prices/${id}/${action}`, {
        method: "POST",
        fetchImpl,
    });
}
