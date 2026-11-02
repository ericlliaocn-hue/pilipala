import { useEffect, useState } from "react";

import { fetchCurrentBanner, type BannerPlacement, type PilipalaBanner } from "@/services/api/pilipala-banner";

/**
 * 当前广告位有效的 Banner。
 *
 * <p>没有数据时是 null，调用方据此不渲染活动条；接口失败也按没有数据处理。
 */
export function useCurrentBanner(placement: BannerPlacement) {
    const [banner, setBanner] = useState<PilipalaBanner | null>(null);

    useEffect(() => {
        let cancelled = false;
        void fetchCurrentBanner(placement)
            .then((data) => {
                if (!cancelled) setBanner(data ?? null);
            })
            .catch(() => {
                if (!cancelled) setBanner(null);
            });
        return () => {
            cancelled = true;
        };
    }, [placement]);

    return banner;
}
