import { pilipalaRequest } from "./pilipala-client";

/**
 * Studio 顶栏公开配置。
 *
 * <p>活动条（首页推广、会员顶部）改读 {@code GET /api/v1/banners/current}，
 * 所以这里不再有 promo_* 字段；{@code app_runtime_setting} 里的旧键留着也不影响。
 */
export type StudioFeatures = {
    membership_enabled: boolean;
    membership_badge: string;
    enterprise_enabled: boolean;
};

export const STUDIO_FEATURE_DEFAULTS: StudioFeatures = {
    membership_enabled: true,
    membership_badge: "限时 4.6 折",
    enterprise_enabled: false,
};

/** 接口失败时的本地回退。 */
export function fallbackStudioFeatures(copy?: Partial<Pick<StudioFeatures, "membership_badge">>): StudioFeatures {
    return { ...STUDIO_FEATURE_DEFAULTS, ...copy };
}

export function parseStudioFeatures(data: unknown): StudioFeatures {
    const row = data && typeof data === "object" ? (data as Record<string, unknown>) : {};
    return {
        membership_enabled: readBoolean(row.membership_enabled, STUDIO_FEATURE_DEFAULTS.membership_enabled),
        membership_badge: readString(row.membership_badge, STUDIO_FEATURE_DEFAULTS.membership_badge),
        enterprise_enabled: readBoolean(row.enterprise_enabled, STUDIO_FEATURE_DEFAULTS.enterprise_enabled),
    };
}

export function shouldShowMembershipBadge(badge: string | null | undefined): boolean {
    return Boolean(badge?.trim());
}

let studioFeaturesCache: { data: StudioFeatures; at: number } | null = null;

export function fetchStudioFeatures(fetchImpl?: typeof fetch) {
    if (!fetchImpl && studioFeaturesCache && Date.now() - studioFeaturesCache.at < 60_000) {
        return Promise.resolve(studioFeaturesCache.data);
    }
    return pilipalaRequest<StudioFeatures>("/api/v1/studio/features", { fetchImpl }).then((data) => {
        const parsed = parseStudioFeatures(data);
        if (!fetchImpl) studioFeaturesCache = { data: parsed, at: Date.now() };
        return parsed;
    });
}

function readBoolean(value: unknown, fallback: boolean): boolean {
    if (typeof value === "boolean") return value;
    if (typeof value === "string") {
        if (value.toLowerCase() === "true") return true;
        if (value.toLowerCase() === "false") return false;
    }
    return fallback;
}

function readString(value: unknown, fallback: string): string {
    if (typeof value === "string") return value;
    if (value == null) return fallback;
    return String(value);
}
