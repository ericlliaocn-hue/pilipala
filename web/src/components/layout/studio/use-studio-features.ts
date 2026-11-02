import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { fallbackStudioFeatures, fetchStudioFeatures, type StudioFeatures } from "@/services/api/studio-features";

export function useStudioFeatures(): StudioFeatures {
    const { t } = useTranslation();
    const fallback = useMemo(
        () =>
            fallbackStudioFeatures({
                membership_badge: t("studio.utilities.membershipBadge"),
            }),
        [t],
    );
    const [features, setFeatures] = useState<StudioFeatures>(fallback);

    useEffect(() => {
        let cancelled = false;
        void fetchStudioFeatures()
            .then((data) => {
                if (!cancelled) setFeatures(data);
            })
            .catch(() => {
                if (!cancelled) setFeatures(fallback);
            });
        return () => {
            cancelled = true;
        };
    }, [fallback]);

    return features;
}
