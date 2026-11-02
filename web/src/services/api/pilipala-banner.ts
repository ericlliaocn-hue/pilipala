import { pilipalaRequest } from "./pilipala-client";

/** Banner 广告位：只有这两个位置，服务端也只认这两个值。 */
export type BannerPlacement = "home_promo" | "membership";

export type PilipalaBanner = {
    id: string;
    placement: BannerPlacement;
    title: string;
    subtitle: string | null;
    image_object_key: string | null;
    link_url: string | null;
    starts_at: string | null;
    ends_at: string | null;
    sort_order: number;
    enabled: boolean;
    campaign_id: string | null;
    created_at: string;
    updated_at: string;
};

/**
 * 当前广告位里有效的那一条 Banner。
 *
 * <p>公开接口，免登录；没有有效记录时 data 为 null，调用方据此不画活动条。
 */
export function fetchCurrentBanner(placement: BannerPlacement, fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaBanner | null>(`/api/v1/banners/current?placement=${encodeURIComponent(placement)}`, { fetchImpl });
}

/** Banner 的截止时刻；没有配结束时间就是不限时，返回 null。 */
export function parseBannerEndMs(endsAt: string | null | undefined): number | null {
    const iso = endsAt?.trim();
    if (!iso) return null;
    const ms = Date.parse(iso);
    return Number.isFinite(ms) ? ms : null;
}
