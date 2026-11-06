import type { ReactNode } from "react";
import { Popover } from "antd";
import { LogOut, RefreshCw, Star, Zap } from "lucide-react";
import { useTranslation } from "react-i18next";

import { useRecharge } from "@/hooks/use-recharge";
import { formatAuthIdentity, useAuthStore } from "@/stores/use-auth-store";
import type { PilipalaUser, PilipalaWallet } from "@/services/api/pilipala-auth";

export function walletAvailableDisplay(wallet: PilipalaWallet | null | undefined): string {
    return wallet ? String(wallet.available) : "—";
}

export function shouldShowCreditsPill(user: PilipalaUser | null | undefined): boolean {
    return Boolean(user);
}

const popoverStyles = { container: { padding: 0, background: "transparent", boxShadow: "none" } };

export function WalletBalancePopover({
    children,
    tone = "studio",
}: {
    children: ReactNode;
    tone?: "studio" | "canvas";
}) {
    const refreshWallet = useAuthStore((state) => state.refreshWallet);
    const onRecharge = useRecharge();

    return (
        <Popover
            trigger="click"
            placement="bottomRight"
            arrow={false}
            styles={popoverStyles}
            onOpenChange={(open) => {
                if (open) void refreshWallet();
            }}
            content={<WalletPopoverCard tone={tone} onRecharge={onRecharge} onRefresh={() => void refreshWallet()} />}
        >
            {children}
        </Popover>
    );
}

export function AccountMenuPopover({
    children,
    tone = "studio",
}: {
    children: ReactNode;
    tone?: "studio" | "canvas";
}) {
    return (
        <Popover trigger="click" placement="bottomRight" arrow={false} styles={popoverStyles} content={<AccountPopoverCard tone={tone} />}>
            {children}
        </Popover>
    );
}

function WalletPopoverCard({
    tone,
    onRecharge,
    onRefresh,
}: {
    tone: "studio" | "canvas";
    onRecharge: () => void;
    onRefresh: () => void;
}) {
    const { t } = useTranslation();
    const wallet = useAuthStore((state) => state.wallet);
    return (
        <SessionCard tone={tone}>
            <BalanceRow tone={tone} label={t("wallet.availableCredits")} value={wallet?.available ?? 0} />
            <BalanceRow tone={tone} label={t("wallet.frozenCredits")} value={wallet?.frozen ?? 0} />
            <div className="mt-3 flex gap-2">
                <SessionButton tone={tone} onClick={onRefresh}>
                    <RefreshCw className="size-3.5" />
                    {t("wallet.refresh")}
                </SessionButton>
                <SessionButton tone={tone} emphasis onClick={onRecharge}>
                    <Star className="size-3.5" />
                    {t("studio.utilities.recharge")}
                </SessionButton>
            </div>
        </SessionCard>
    );
}

function AccountPopoverCard({ tone }: { tone: "studio" | "canvas" }) {
    const { t } = useTranslation();
    const user = useAuthStore((state) => state.user);
    const wallet = useAuthStore((state) => state.wallet);
    const logout = useAuthStore((state) => state.logout);
    return (
        <SessionCard tone={tone}>
            <p className={`truncate text-[12px] ${tone === "studio" ? "text-neutral-400" : "text-stone-500 dark:text-stone-400"}`}>
                {t("auth.signedInAs", { identity: formatAuthIdentity(user) })}
            </p>
            <p className="mt-2 flex items-center gap-1 text-[13px] font-medium tabular-nums">
                <Zap className={`size-3.5 ${tone === "studio" ? "text-[#6eb8b2]" : "text-teal-600 dark:text-[#6eb8b2]"}`} />
                {wallet?.available ?? 0}
            </p>
            <button
                type="button"
                className={`mt-3 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] ${
                    tone === "studio" ? "text-neutral-200 hover:bg-white/10" : "text-stone-800 hover:bg-black/5 dark:text-stone-100 dark:hover:bg-white/10"
                }`}
                onClick={() => void logout()}
            >
                <LogOut className="size-3.5" />
                {t("auth.logout")}
            </button>
        </SessionCard>
    );
}

function SessionCard({ tone, children }: { tone: "studio" | "canvas"; children: ReactNode }) {
    return (
        <div
            className={
                tone === "studio"
                    ? "min-w-[228px] rounded-xl border border-white/10 bg-[#161616] p-3 text-neutral-100 shadow-xl"
                    : "min-w-[228px] rounded-xl border border-black/10 bg-white p-3 text-stone-900 shadow-xl dark:border-white/10 dark:bg-[#161616] dark:text-neutral-100"
            }
        >
            {children}
        </div>
    );
}

function BalanceRow({ tone, label, value }: { tone: "studio" | "canvas"; label: string; value: number }) {
    return (
        <div className="flex items-center justify-between gap-4 py-0.5 text-[12px]">
            <span className={tone === "studio" ? "text-neutral-400" : "text-stone-500 dark:text-stone-400"}>{label}</span>
            <span className="tabular-nums font-medium">{value}</span>
        </div>
    );
}

function SessionButton({
    tone,
    emphasis,
    onClick,
    children,
}: {
    tone: "studio" | "canvas";
    emphasis?: boolean;
    onClick: () => void;
    children: ReactNode;
}) {
    const studio = emphasis
        ? "bg-white/[0.08] text-neutral-50 hover:bg-white/[0.12]"
        : "text-neutral-300 hover:bg-white/[0.06]";
    const canvas = emphasis
        ? "bg-black/5 text-stone-900 hover:bg-black/10 dark:bg-white/10 dark:text-neutral-50 dark:hover:bg-white/15"
        : "text-stone-600 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/10";
    return (
        <button
            type="button"
            className={`inline-flex h-8 flex-1 items-center justify-center gap-1 rounded-lg px-2 text-[11px] ${tone === "studio" ? studio : canvas}`}
            onClick={onClick}
        >
            {children}
        </button>
    );
}
