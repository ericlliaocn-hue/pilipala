import { App, Switch } from "antd";
import { ChevronDown, ChevronRight, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import {
    defaultVariantCode,
    MEMBERSHIP_CYCLES,
    MembershipPlanCard,
    type MembershipCycle,
} from "@/components/layout/studio/membership-plan-card";
import { useCreditSupermarketUi } from "@/components/layout/studio/credit-supermarket-modal";
import { useCurrentBanner } from "@/hooks/use-current-banner";
import { formatCountdownUnit, usePromoCountdown } from "@/hooks/use-promo-countdown";
import {
    checkoutMembership,
    fetchMembershipMe,
    fetchMembershipPlans,
    type MembershipPlan,
    type MembershipPlanPrice,
} from "@/services/api/pilipala-membership";
import { parseBannerEndMs } from "@/services/api/pilipala-banner";
import { useAuthStore } from "@/stores/use-auth-store";

function PromoCountdownUnit({ value, label }: { value: string; label: string }) {
    return (
        <div className="flex flex-col items-center gap-1">
            <span className="inline-flex min-w-[3.25rem] items-center justify-center rounded-md bg-white/[0.08] px-2 py-2 text-xl font-semibold tabular-nums text-white sm:min-w-[3.75rem] sm:text-2xl">
                {value}
            </span>
            <span className="text-[10px] text-neutral-500">{label}</span>
        </div>
    );
}

export default function MembershipPage() {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const navigate = useNavigate();
    const banner = useCurrentBanner("membership");
    const openCreditStore = useCreditSupermarketUi((s) => s.openStore);
    const user = useAuthStore((state) => state.user);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const [loading, setLoading] = useState(false);
    const [plans, setPlans] = useState<MembershipPlan[]>([]);
    const [cycle, setCycle] = useState<MembershipCycle>("year");
    const [teamMode, setTeamMode] = useState(false);
    const [variants, setVariants] = useState<Record<string, string>>({});

    const localeZh = i18n.language.startsWith("zh");
    const audience = teamMode ? "team" : "personal";
    const countdown = usePromoCountdown(parseBannerEndMs(banner?.ends_at) ?? 0);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setPlans(await fetchMembershipPlans());
            if (user) {
                await fetchMembershipMe().catch(() => null);
            }
        } catch {
            message.error(t("membership.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [message, t, user]);

    useEffect(() => {
        void load();
    }, [load]);

    useEffect(() => {
        setVariants((prev) => {
            const next = { ...prev };
            for (const plan of plans.filter((p) => p.audience === "personal")) {
                const prices = plan.prices.filter((p) => p.cycle === cycle);
                if (prices.length <= 1) continue;
                const current = prev[plan.code];
                if (!current || !prices.some((p) => p.variant_code === current)) {
                    next[plan.code] = defaultVariantCode(prices, cycle);
                }
            }
            return next;
        });
    }, [cycle, plans]);

    const cards = useMemo(() => {
        return plans
            .filter((p) => p.audience === audience)
            .map((plan) => {
                const prices = plan.prices.filter((p) => p.cycle === cycle);
                const variantCode = variants[plan.code] ?? defaultVariantCode(prices, cycle);
                const price = prices.find((p) => p.variant_code === variantCode) ?? prices[0];
                return { plan, price, prices };
            })
            .filter((row) => row.price);
    }, [plans, cycle, audience, variants]);

    const checkout = async (price: MembershipPlanPrice) => {
        if (!user) {
            setAuthModalOpen(true, "login");
            return;
        }
        try {
            const order = await checkoutMembership(price.id);
            if (order.pay_url) {
                window.open(order.pay_url, "_blank", "noopener,noreferrer");
            } else {
                message.info(t("studio.recharge.unavailable"));
            }
        } catch (error) {
            message.error(error instanceof Error ? error.message : t("membership.checkoutFailed"));
        }
    };

    const promoTitle = banner?.title ?? "";
    const promoCopy = banner?.subtitle ?? "";

    return (
        <div className="min-h-full bg-[#050505] text-neutral-100">
            {banner ? (
                <div className="relative overflow-hidden border-b border-white/[0.06]">
                    <div
                        className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_80%_60%_at_50%_-20%,rgba(46,184,176,0.35),transparent)]"
                        aria-hidden
                    />
                    <button
                        type="button"
                        className="absolute right-4 top-4 z-10 inline-flex size-9 items-center justify-center rounded-full text-neutral-400 hover:bg-white/10 hover:text-neutral-100"
                        onClick={() => navigate(-1)}
                        aria-label={t("common.close")}
                    >
                        <X className="size-5" />
                    </button>
                    <div className="relative mx-auto flex max-w-[1680px] flex-col gap-8 px-6 py-10 sm:flex-row sm:items-center sm:justify-between sm:px-10 lg:py-12">
                        <div className="max-w-2xl pr-10">
                            <h1 className="text-2xl font-semibold leading-snug text-white sm:text-[28px]">{promoTitle}</h1>
                            {promoCopy ? <p className="mt-3 text-sm leading-relaxed text-neutral-400">{promoCopy}</p> : null}
                            <p className="mt-2 text-[13px] text-neutral-500">{t("membership.promoLine")}</p>
                        </div>
                        {!countdown.expired ? (
                            <div className="flex shrink-0 items-center gap-2 sm:gap-3" aria-live="polite">
                                <PromoCountdownUnit value={formatCountdownUnit(countdown.days)} label={localeZh ? "天" : "Days"} />
                                <span className="pb-5 text-lg text-neutral-600">:</span>
                                <PromoCountdownUnit value={formatCountdownUnit(countdown.hours)} label={localeZh ? "时" : "Hrs"} />
                                <span className="pb-5 text-lg text-neutral-600">:</span>
                                <PromoCountdownUnit value={formatCountdownUnit(countdown.minutes)} label={localeZh ? "分" : "Min"} />
                                <span className="pb-5 text-lg text-neutral-600">:</span>
                                <PromoCountdownUnit value={formatCountdownUnit(countdown.seconds)} label={localeZh ? "秒" : "Sec"} />
                            </div>
                        ) : null}
                    </div>
                </div>
            ) : null}

            <div className="sticky top-0 z-20 border-b border-white/[0.06] bg-[#050505]/95 backdrop-blur-md">
                <div className="mx-auto flex max-w-[1680px] flex-wrap items-center justify-between gap-4 px-6 py-4 sm:px-10">
                    <label className="flex items-center gap-2 text-[13px] text-neutral-400">
                        <span>{t("membership.team")}</span>
                        <Switch size="small" checked={teamMode} onChange={setTeamMode} />
                    </label>

                    <div className="flex rounded-full bg-white/[0.06] p-1">
                        {MEMBERSHIP_CYCLES.map((c) => {
                            const active = cycle === c;
                            const discountKey = `membership.cycleDiscount.${c}` as const;
                            const discount = t(discountKey);
                            const yearHighlight = c === "year";
                            return (
                                <button
                                    key={c}
                                    type="button"
                                    className={`rounded-full px-5 py-2 text-[13px] transition ${
                                        active
                                            ? "bg-white/[0.12] font-semibold text-white"
                                            : "text-neutral-400 hover:text-neutral-200"
                                    }`}
                                    onClick={() => setCycle(c)}
                                >
                                    {t(`membership.cycles.${c}`)}
                                    {discount ? (
                                        <span
                                            className={`ml-1.5 text-[12px] font-semibold ${
                                                yearHighlight ? "text-[#ff8a65]" : "text-neutral-500"
                                            }`}
                                        >
                                            {discount}
                                        </span>
                                    ) : null}
                                </button>
                            );
                        })}
                    </div>

                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            className="inline-flex items-center gap-0.5 text-[13px] text-[#6eb8b2] hover:text-[#8ed4ce]"
                            onClick={openCreditStore}
                        >
                            {t("membership.pointsMarket")}
                            <ChevronRight className="size-4" />
                        </button>
                        {banner ? null : (
                            <button
                                type="button"
                                className="inline-flex size-9 items-center justify-center rounded-full text-neutral-400 hover:bg-white/10 hover:text-neutral-100"
                                onClick={() => navigate(-1)}
                                aria-label={t("common.close")}
                            >
                                <X className="size-5" />
                            </button>
                        )}
                    </div>
                </div>
            </div>

            <div className="mx-auto max-w-[1680px] px-4 py-8 sm:px-8 lg:px-10 lg:py-10">
                {audience === "team" ? (
                    <p className="py-24 text-center text-sm text-neutral-400">{t("membership.teamStub")}</p>
                ) : loading ? (
                    <p className="py-24 text-center text-sm text-neutral-500">{t("common.loading")}</p>
                ) : (
                    <div className="grid grid-cols-1 items-stretch gap-4 md:grid-cols-2 xl:grid-cols-5 xl:gap-5">
                        {cards.map(({ plan, price, prices }) => (
                            <MembershipPlanCard
                                key={plan.code}
                                plan={plan}
                                price={price!}
                                prices={prices}
                                cycle={cycle}
                                localeZh={localeZh}
                                onSelectVariant={(code) => setVariants((prev) => ({ ...prev, [plan.code]: code }))}
                                onCheckout={() => void checkout(price!)}
                            />
                        ))}
                    </div>
                )}
            </div>

            <div className="border-t border-white/[0.06] px-6 py-8 text-center">
                <p className="text-[12px] text-neutral-500">{t("membership.footerReset")}</p>
                <button
                    type="button"
                    className="mt-4 inline-flex items-center gap-1 text-[13px] text-neutral-400 hover:text-neutral-200"
                    onClick={() => window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" })}
                >
                    {t("membership.moreBenefits")}
                    <ChevronDown className="size-4" />
                </button>
            </div>
        </div>
    );
}
