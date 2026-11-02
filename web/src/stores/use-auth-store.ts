import { create } from "zustand";

import {
    fetchCurrentUser,
    fetchWallet,
    loginUser,
    logoutUser,
    registerUser,
    resetPassword as resetPasswordApi,
    type PilipalaAuthSession,
    type PilipalaUser,
    type PilipalaWallet,
} from "@/services/api/pilipala-auth";
import { PilipalaApiError, getStoredPilipalaToken, setStoredPilipalaToken } from "@/services/api/pilipala-client";

/** 登录 / 注册返回的会话里没有平台权限码，登录后补一次 /auth/me。 */
async function fetchMeOrSession(session: PilipalaAuthSession, fetchImpl?: typeof fetch): Promise<PilipalaUser> {
    try {
        return await fetchCurrentUser(fetchImpl);
    } catch {
        return session;
    }
}

type AuthStore = {
    token: string | null;
    user: PilipalaUser | null;
    wallet: PilipalaWallet | null;
    loading: boolean;
    initialized: boolean;
    authModalOpen: boolean;
    authModalTab: "login" | "register";
    initialize: (fetchImpl?: typeof fetch) => Promise<void>;
    refreshWallet: (fetchImpl?: typeof fetch) => Promise<void>;
    login: (input: { phone?: string; email?: string; password?: string; otp?: string }, fetchImpl?: typeof fetch) => Promise<void>;
    register: (
        input: { phone?: string; email?: string; password: string; otp: string; locale?: "zh" | "en" },
        fetchImpl?: typeof fetch,
    ) => Promise<void>;
    resetPassword: (input: { phone?: string; email?: string; otp: string; new_password: string }, fetchImpl?: typeof fetch) => Promise<void>;
    logout: (fetchImpl?: typeof fetch) => Promise<void>;
    setAuthModalOpen: (open: boolean, tab?: "login" | "register") => void;
};

export const useAuthStore = create<AuthStore>()((set, get) => ({
    token: getStoredPilipalaToken(),
    user: null,
    wallet: null,
    loading: false,
    initialized: false,
    authModalOpen: false,
    authModalTab: "login",

    initialize: async (fetchImpl) => {
        const token = getStoredPilipalaToken();
        if (!token) {
            set({ token: null, user: null, wallet: null, initialized: true });
            return;
        }
        set({ loading: true });
        try {
            const user = await fetchCurrentUser(fetchImpl);
            set({ token, user, initialized: true });
            await get().refreshWallet(fetchImpl);
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "UNAUTHORIZED") setStoredPilipalaToken(null);
            set({ token: null, user: null, wallet: null, initialized: true });
        } finally {
            set({ loading: false });
        }
    },

    refreshWallet: async (fetchImpl) => {
        if (!(get().token || getStoredPilipalaToken())) return;
        try {
            set({ wallet: await fetchWallet(fetchImpl) });
        } catch {
            // 余额刷新失败不影响主流程，静默忽略。
        }
    },

    login: async (input, fetchImpl) => {
        set({ loading: true });
        try {
            const session = await loginUser(input, fetchImpl);
            setStoredPilipalaToken(session.token);
            set({ token: session.token, user: await fetchMeOrSession(session, fetchImpl), authModalOpen: false });
            await get().refreshWallet(fetchImpl);
        } finally {
            set({ loading: false });
        }
    },

    register: async (input, fetchImpl) => {
        set({ loading: true });
        try {
            const session = await registerUser(input, fetchImpl);
            setStoredPilipalaToken(session.token);
            set({ token: session.token, user: await fetchMeOrSession(session, fetchImpl), authModalOpen: false });
            await get().refreshWallet(fetchImpl);
        } finally {
            set({ loading: false });
        }
    },

    resetPassword: async (input, fetchImpl) => {
        set({ loading: true });
        try {
            const session = await resetPasswordApi(input, fetchImpl);
            setStoredPilipalaToken(session.token);
            set({ token: session.token, user: await fetchMeOrSession(session, fetchImpl), authModalOpen: false });
            await get().refreshWallet(fetchImpl);
        } finally {
            set({ loading: false });
        }
    },

    logout: async (fetchImpl) => {
        const token = get().token || getStoredPilipalaToken();
        if (!token) {
            setStoredPilipalaToken(null);
            set({ token: null, user: null, wallet: null });
            return;
        }
        setStoredPilipalaToken(null);
        set({ token: null, user: null, wallet: null });
        try {
            await logoutUser(fetchImpl, token);
        } catch {
            // 登出失败也要清本地登录态。
        }
    },

    setAuthModalOpen: (open, tab) => set((state) => ({ authModalOpen: open, authModalTab: tab ?? state.authModalTab })),
}));

/** 顶栏展示用：优先手机，其次邮箱。 */
export function formatAuthIdentity(user: PilipalaUser | null): string {
    if (!user) return "";
    return user.phone ?? user.email ?? user.user_id;
}
