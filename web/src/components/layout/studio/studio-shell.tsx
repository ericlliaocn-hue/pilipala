import type { ReactNode } from "react";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { AppConfigModal } from "@/components/layout/app-config-modal";
import { StudioMobileDrawer } from "@/components/layout/studio/studio-mobile-drawer";
import { StudioPromoStrip } from "@/components/layout/studio/studio-promo-strip";
import { StudioSidebar } from "@/components/layout/studio/studio-sidebar";
import { StudioUtilityBar } from "@/components/layout/studio/studio-utility-bar";

import { CanvasPluginManagerModal } from "@/components/canvas/canvas-plugin-manager-modal";
import { CreditSupermarketModal, useCreditSupermarket } from "@/components/layout/studio/credit-supermarket-modal";
import { useAgentStore } from "@/stores/use-agent-store";

type StudioShellProps = {
    children: ReactNode;
};

export function StudioShell({ children }: StudioShellProps) {
    const { pathname } = useLocation();
    const navigate = useNavigate();
    const hideChrome = /^\/canvas\/[^/]+/.test(pathname);
    const isMembershipPage = pathname === "/billing/membership";
    const creditStore = useCreditSupermarket();
    const [mobileNavOpen, setMobileNavOpen] = useState(false);
    const [pluginsOpen, setPluginsOpen] = useState(false);
    const autoConnectRef = useRef(false);

    const agentToken = useAgentStore((state) => state.token);
    const agentEnabled = useAgentStore((state) => state.enabled);
    const agentConnected = useAgentStore((state) => state.connected);
    const connectAgent = useAgentStore((state) => state.connectAgent);
    const togglePanel = useAgentStore((state) => state.togglePanel);
    const panelOpen = useAgentStore((state) => state.panelOpen);

    useEffect(() => {
        if (autoConnectRef.current || agentEnabled || agentConnected || !agentToken.trim()) return;
        autoConnectRef.current = true;
        connectAgent({ silent: true });
    }, [agentConnected, agentEnabled, agentToken, connectAgent]);

    if (hideChrome) {
        return (
            <>
                <div className="min-h-0 flex-1 overflow-hidden">{children}</div>
                <AppConfigModal />
                <CanvasPluginManagerModal open={pluginsOpen} onClose={() => setPluginsOpen(false)} />
            </>
        );
    }

    return (
        <>
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#0b0b0b] text-neutral-100">
                <StudioPromoStrip />
                <div className="flex min-h-0 flex-1">
                    <StudioSidebar onAgent={togglePanel} onPlugins={() => setPluginsOpen(true)} />
                    <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
                        <StudioUtilityBar
                            onOpenMenu={() => setMobileNavOpen(true)}
                            onToggleAgent={togglePanel}
                            onOpenMembership={() => navigate("/billing/membership")}
                            onOpenCreditStore={creditStore.openStore}
                            agentPanelOpen={panelOpen}
                        />
                        <div className={`min-h-0 flex-1 ${isMembershipPage ? "overflow-y-auto" : "overflow-hidden"}`}>
                            {children}
                        </div>
                    </div>
                </div>
            </div>
            <StudioMobileDrawer
                open={mobileNavOpen}
                onClose={() => setMobileNavOpen(false)}
                onAgent={togglePanel}
                onPlugins={() => {
                    setMobileNavOpen(false);
                    setPluginsOpen(true);
                }}
            />
            <AppConfigModal />
            <CreditSupermarketModal open={creditStore.open} onClose={creditStore.closeStore} />
            <CanvasPluginManagerModal open={pluginsOpen} onClose={() => setPluginsOpen(false)} />
        </>
    );
}
