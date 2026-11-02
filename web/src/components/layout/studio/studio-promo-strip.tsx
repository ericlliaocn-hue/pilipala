import { useState } from "react";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";

import { useCurrentBanner } from "@/hooks/use-current-banner";
import { formatCountdownUnit, usePromoCountdown } from "@/hooks/use-promo-countdown";
import { parseBannerEndMs } from "@/services/api/pilipala-banner";

/** 关掉的是哪一条 Banner：换了新 Banner 还会再显示一次。 */
const STORAGE_KEY = "pilipala:studio-promo-dismissed:v2";

export function StudioPromoStrip() {
    const { t } = useTranslation();
    const banner = useCurrentBanner("home_promo");
    const countdown = usePromoCountdown(parseBannerEndMs(banner?.ends_at) ?? 0);
    const [dismissedId, setDismissedId] = useState<string | null>(() => {
        try {
            return localStorage.getItem(STORAGE_KEY);
        } catch {
            return null;
        }
    });

    if (!banner || dismissedId === banner.id) {
        return null;
    }

    return (
        <div
            className="relative z-50 flex h-10 shrink-0 items-center justify-center gap-3 bg-[#0e7a78] px-10 text-xs text-white"
            role="region"
            aria-label={t("studio.promo.label")}
        >
            {!countdown.expired ? (
                <div
                    className="inline-flex items-center gap-1"
                    aria-live="polite"
                    aria-label={t("studio.promo.countdownLabel", {
                        days: countdown.days,
                        hours: countdown.hours,
                        minutes: countdown.minutes,
                        seconds: countdown.seconds,
                    })}
                >
                    <TimerUnit value={formatCountdownUnit(countdown.days)} />
                    <Colon />
                    <TimerUnit value={formatCountdownUnit(countdown.hours)} />
                    <Colon />
                    <TimerUnit value={formatCountdownUnit(countdown.minutes)} />
                    <Colon />
                    <TimerUnit value={formatCountdownUnit(countdown.seconds)} />
                </div>
            ) : null}
            <p className="hidden text-[12.5px] sm:block">
                <strong className="font-semibold text-white">{banner.title}</strong>
                {banner.subtitle ? <span className="text-white/85"> · {banner.subtitle}</span> : null}
            </p>
            {banner.link_url ? (
                <a
                    href={banner.link_url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="studio-solid-on-white h-7 shrink-0 rounded-md bg-white px-3.5 text-xs font-semibold transition hover:bg-[#e8fffc]"
                >
                    {t("common.details")}
                </a>
            ) : null}
            <button
                type="button"
                className="absolute right-3 top-1/2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-white/80 transition hover:bg-white/10 hover:text-white"
                aria-label={t("studio.promo.dismiss")}
                onClick={() => {
                    setDismissedId(banner.id);
                    try {
                        localStorage.setItem(STORAGE_KEY, banner.id);
                    } catch {
                        /* ignore */
                    }
                }}
            >
                <X className="size-4" />
            </button>
        </div>
    );
}

function TimerUnit({ value }: { value: string }) {
    return (
        <span className="inline-flex items-center gap-0.5">
            <DigitBox digit={value[0] ?? "0"} />
            <DigitBox digit={value[1] ?? "0"} />
        </span>
    );
}

function DigitBox({ digit }: { digit: string }) {
    return (
        <span className="inline-flex h-[22px] min-w-[18px] items-center justify-center rounded-[4px] bg-white px-1 text-[12px] font-semibold tabular-nums text-black">
            {digit}
        </span>
    );
}

function Colon() {
    return <span className="px-0.5 text-[12px] font-semibold text-white/70">:</span>;
}
