import type { CSSProperties } from "react";
import { useEffect, useRef } from "react";
import { Tooltip } from "antd";
import { BookOpen, Keyboard, Puzzle, Settings2, ShieldCheck, Wallet } from "lucide-react";
import { useTranslation } from "react-i18next";

import { AnimatedThemeToggler } from "@/components/ui/animated-theme-toggler";
import { GitHubLink } from "@/components/layout/github-link";
import { VersionReleaseModal } from "@/components/layout/version-release-modal";
import { ADMIN_CONSOLE_URL, DOCS_URL } from "@/constant/env";
import { changeAppLocale, type AppLocale } from "@/i18n";
import { cn } from "@/lib/utils";
import { canvasThemes } from "@/lib/canvas-theme";
import { AccountMenuPopover, WalletBalancePopover, walletAvailableDisplay } from "@/components/layout/auth-session-menus";
import { formatAuthIdentity, useAuthStore } from "@/stores/use-auth-store";
import { useConfigStore } from "@/stores/use-config-store";
import { useThemeStore } from "@/stores/use-theme-store";

type UserStatusActionsProps = {
    showConfig?: boolean;
    variant?: "default" | "canvas";
    onOpenShortcuts?: () => void;
    onOpenPlugins?: () => void;
};

export function UserStatusActions({ showConfig = true, variant = "default", onOpenShortcuts, onOpenPlugins }: UserStatusActionsProps) {
    const { i18n, t } = useTranslation();
    const theme = useThemeStore((state) => state.theme);
    const setTheme = useThemeStore((state) => state.setTheme);
    const openConfigDialog = useConfigStore((state) => state.openConfigDialog);
    const canvasTheme = canvasThemes[theme];
    const naturalIconClass = "inline-flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-stone-600 transition-colors hover:bg-black/5 hover:text-stone-950 dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white [&_svg]:size-4";
    const flatTextClass = "inline-flex h-7 shrink-0 cursor-pointer items-center gap-1.5 rounded-md px-1.5 text-xs font-medium text-stone-600 transition-colors hover:bg-black/5 hover:text-stone-950 dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white";
    const iconStyle: CSSProperties | undefined = variant === "canvas" ? { color: canvasTheme.node.text } : undefined;
    const versionStyle = iconStyle;
    const gitHubClassName = "size-7 text-base";
    const gitHubStyle = iconStyle;
    const locale = i18n.resolvedLanguage as AppLocale;
    const nextLocale = locale === "zh-CN" ? "en-US" : "zh-CN";
    const languageLabel = t("topNav.switchLanguage", { language: t(nextLocale === "zh-CN" ? "locale.zhCN" : "locale.enUS") });

    const user = useAuthStore((state) => state.user);
    const wallet = useAuthStore((state) => state.wallet);
    const initialize = useAuthStore((state) => state.initialize);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const startedRef = useRef(false);

    useEffect(() => {
        if (startedRef.current) return;
        startedRef.current = true;
        void initialize();
    }, [initialize]);

    const walletLabel = wallet
        ? `${t("wallet.available")} ${wallet.available}${wallet.frozen > 0 ? ` · ${t("wallet.frozen")} ${wallet.frozen}` : ""}`
        : t("wallet.title");

    return (
        <div className="inline-flex shrink-0 items-center gap-1">
            {user ? (
                <>
                    <WalletBalancePopover tone="canvas">
                        <button type="button" className={flatTextClass} style={iconStyle} aria-label={walletLabel}>
                            <Wallet className="size-3.5" />
                            <span className="tabular-nums">{walletAvailableDisplay(wallet)}</span>
                            {wallet && wallet.frozen > 0 ? <span className="tabular-nums opacity-60">/{wallet.frozen}</span> : null}
                        </button>
                    </WalletBalancePopover>
                    <AccountMenuPopover tone="canvas">
                        <button type="button" className={flatTextClass} style={iconStyle} aria-label={t("auth.accountMenu")}>
                            <span className="max-w-24 truncate">{formatAuthIdentity(user)}</span>
                        </button>
                    </AccountMenuPopover>
                    {/* 独立运维后台：只有有平台权限码的人才看到入口，跳到 admin.pilipala.art。 */}
                    {user.platform_permissions?.length ? (
                        <Tooltip title={t("admin.entry")} mouseEnterDelay={0.2}>
                            <a
                                href={ADMIN_CONSOLE_URL}
                                target="_blank"
                                rel="noopener noreferrer"
                                className={flatTextClass}
                                style={iconStyle}
                                aria-label={t("admin.entry")}
                            >
                                <ShieldCheck className="size-3.5" />
                                {variant === "canvas" ? null : <span>{t("admin.entry")}</span>}
                            </a>
                        </Tooltip>
                    ) : null}
                </>
            ) : (
                <button type="button" className={flatTextClass} style={iconStyle} onClick={() => setAuthModalOpen(true, "login")}>
                    {t("auth.needLogin")}
                </button>
            )}
            {onOpenPlugins ? (
                <button type="button" className={naturalIconClass} style={iconStyle} onClick={onOpenPlugins} aria-label={t("topNav.plugins")} title={t("topNav.plugins")}>
                    <Puzzle className="size-4" />
                </button>
            ) : null}
            <a href={DOCS_URL} target="_blank" rel="noopener noreferrer" className={naturalIconClass} style={iconStyle} aria-label={t("topNav.docs")} title={t("topNav.docs")}>
                <BookOpen className="size-4" />
            </a>
            {showConfig ? (
                <button type="button" className={naturalIconClass} style={iconStyle} onClick={() => openConfigDialog(false)} aria-label={t("navigation.config")} title={t("navigation.config")}>
                    <Settings2 className="size-4" />
                </button>
            ) : null}
            <Tooltip title={languageLabel} mouseEnterDelay={0.2}>
                <button type="button" className={`${naturalIconClass} text-[11px] font-semibold tracking-tight`} style={iconStyle} onClick={() => void changeAppLocale(nextLocale)} aria-label={languageLabel}>
                    {locale === "zh-CN" ? "中" : "EN"}
                </button>
            </Tooltip>
            <AnimatedThemeToggler theme={theme} onThemeChange={setTheme} className={naturalIconClass} style={iconStyle} aria-label={t(theme === "dark" ? "topNav.lightTheme" : "topNav.darkTheme")} title={t(theme === "dark" ? "topNav.lightTheme" : "topNav.darkTheme")} />
            <VersionReleaseModal style={versionStyle} />
            <GitHubLink className={cn("bg-transparent hover:bg-transparent dark:hover:bg-transparent", gitHubClassName)} style={gitHubStyle} />
            {onOpenShortcuts ? (
                <button type="button" className={naturalIconClass} style={iconStyle} onClick={onOpenShortcuts} aria-label={t("topNav.shortcuts")} title={t("topNav.shortcuts")}>
                    <Keyboard className="size-4" />
                </button>
            ) : null}
        </div>
    );
}
