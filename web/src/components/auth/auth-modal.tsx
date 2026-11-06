import { useEffect, useRef, useState, type ReactNode } from "react";
import { App, Button, Form, Input, Modal } from "antd";
import { Eye, EyeOff, X } from "lucide-react";
import { useTranslation } from "react-i18next";

import { shouldResetAuthModal } from "@/components/auth/auth-modal-lifecycle";
import { PilipalaLogo } from "@/components/brand/pilipala-logo";
import { authIdentityPayload, authOtpDestination } from "@/lib/auth-identity";
import { PilipalaApiError } from "@/services/api/pilipala-client";
import { fetchAuthFeatures, sendAuthOtp, type AuthFeatures } from "@/services/api/pilipala-auth";
import { useAuthStore } from "@/stores/use-auth-store";

const MIN_PASSWORD_LENGTH = 8;

type AuthFormValues = {
    identity: string;
    password: string;
    otp: string;
};

type PanelMode = "login" | "register" | "forgot";

export function AuthModal() {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const [form] = Form.useForm<AuthFormValues>();
    const open = useAuthStore((state) => state.authModalOpen);
    const tab = useAuthStore((state) => state.authModalTab);
    const loading = useAuthStore((state) => state.loading);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const login = useAuthStore((state) => state.login);
    const register = useAuthStore((state) => state.register);
    const resetPassword = useAuthStore((state) => state.resetPassword);

    const [panel, setPanel] = useState<PanelMode>("login");
    const [identityKind, setIdentityKind] = useState<"phone" | "email">("phone");
    const [loginMode, setLoginMode] = useState<"password" | "otp">("password");
    const [otpCooldown, setOtpCooldown] = useState(0);
    const [features, setFeatures] = useState<AuthFeatures | null>(null);
    const [passwordVisible, setPasswordVisible] = useState(false);
    const wasOpenRef = useRef(false);

    useEffect(() => {
        if (!open) {
            wasOpenRef.current = false;
            return;
        }
        const justOpened = shouldResetAuthModal(open, wasOpenRef.current);
        wasOpenRef.current = true;
        setPanel(tab);
        if (!justOpened) {
            return;
        }
        form.resetFields();
        setOtpCooldown(0);
        setIdentityKind("phone");
        setLoginMode("password");
        void fetchAuthFeatures().then(setFeatures).catch(() => setFeatures({ email_login_enabled: false, email_register_enabled: false }));
    }, [open, tab, form]);

    useEffect(() => {
        if (otpCooldown <= 0) return;
        const timer = window.setTimeout(() => setOtpCooldown((s) => s - 1), 1000);
        return () => window.clearTimeout(timer);
    }, [otpCooldown]);

    const showEmailOption =
        panel === "register"
            ? features?.email_register_enabled
            : panel === "login" || panel === "forgot"
              ? features?.email_login_enabled
              : false;

    const otpPurpose = () => {
        if (panel === "register") return "register" as const;
        if (panel === "forgot") return "reset_password" as const;
        return "login" as const;
    };

    const sendOtp = async () => {
        const identity = form.getFieldValue("identity")?.trim();
        if (!identity) {
            message.warning(t("auth.phonePlaceholder"));
            return;
        }
        try {
            const result = await sendAuthOtp({
                channel: identityKind === "phone" ? "sms" : "email",
                destination: authOtpDestination(identityKind, identity),
                purpose: otpPurpose(),
            });
            setOtpCooldown(result.cooldown);
            message.success(t("auth.otpSent"));
        } catch (error) {
            message.error(error instanceof PilipalaApiError ? error.message : t("auth.otpSendFailed"));
        }
    };

    const submit = async (values: AuthFormValues) => {
        const identity = authIdentityPayload(identityKind, values.identity);
        const otp = values.otp?.trim();
        try {
            if (panel === "login") {
                if (loginMode === "password") {
                    await login({ ...identity, password: values.password });
                } else {
                    await login({ ...identity, otp });
                }
            } else if (panel === "register") {
                await register({
                    ...identity,
                    password: values.password,
                    otp,
                    locale: i18n.resolvedLanguage === "en-US" ? "en" : "zh",
                });
            } else {
                await resetPassword({
                    ...identity,
                    otp,
                    new_password: values.password,
                });
            }
        } catch (error) {
            const failKey = panel === "login" ? "auth.loginFailed" : panel === "register" ? "auth.registerFailed" : "auth.loginFailed";
            message.error(error instanceof PilipalaApiError ? error.message : t(failKey));
        }
    };

    const title = panel === "login" ? t("auth.loginTitle") : panel === "register" ? t("auth.registerTitle") : t("auth.forgotPassword");
    const subtitle =
        panel === "login"
            ? t("auth.loginSubtitle")
            : panel === "register"
              ? t("auth.registerSubtitle")
              : t("auth.loginSubtitle");

    return (
        <Modal
            open={open}
            onCancel={() => setAuthModalOpen(false)}
            footer={null}
            closable={false}
            centered
            width="min(520px, calc(100vw - 2rem))"
            destroyOnHidden
            styles={{
                mask: { backgroundColor: "rgba(0,0,0,0.88)" },
                container: { background: "transparent", boxShadow: "none", padding: 0, maxWidth: "calc(100vw - 2rem)" },
                body: { padding: 0 },
            }}
        >
            <div className="relative mx-auto flex min-h-[34rem] w-full max-w-[32rem] items-center justify-center px-1 py-8 sm:px-0">
                <div className="pointer-events-none absolute inset-0 flex items-center justify-center overflow-visible" aria-hidden>
                    <img
                        src="/logo-mark-3x.png"
                        alt=""
                        className="h-[min(42rem,120vw)] w-[min(44rem,125vw)] max-w-none object-contain opacity-40"
                        style={{
                            filter: "blur(4px) drop-shadow(0 0 100px rgba(232,134,122,0.55)) drop-shadow(0 0 160px rgba(255,255,255,0.18))",
                        }}
                    />
                </div>

                <div
                    className="relative z-10 w-full rounded-2xl border border-white/10 px-7 pb-7 pt-8 backdrop-blur-xl sm:px-9 sm:pb-8 sm:pt-9"
                    style={{ backgroundColor: "rgba(18,18,20,0.72)" }}
                >
                    <button
                        type="button"
                        className="absolute right-4 top-4 rounded p-1 text-white/70 transition hover:bg-white/10 hover:text-white"
                        onClick={() => setAuthModalOpen(false)}
                        aria-label={t("common.cancel")}
                    >
                        <X className="h-4 w-4" strokeWidth={1.5} />
                    </button>

                    <div className="mb-8 flex flex-col items-center gap-2.5 text-center">
                        <PilipalaLogo variant="lockup" className="h-[5.5rem] w-[7.75rem] sm:h-[6rem] sm:w-[8.5rem]" />
                        <h2 className="text-xl font-medium tracking-tight text-white sm:text-2xl">{title}</h2>
                        <p className="text-sm text-white/55">{subtitle}</p>
                    </div>

                    <AuthFormBody
                        panel={panel}
                        form={form}
                        loading={loading}
                        identityKind={identityKind}
                        loginMode={loginMode}
                        otpCooldown={otpCooldown}
                        passwordVisible={passwordVisible}
                        showEmailOption={!!showEmailOption}
                        onIdentityKindChange={setIdentityKind}
                        onLoginModeChange={setLoginMode}
                        onPasswordVisibleChange={setPasswordVisible}
                        onSendOtp={sendOtp}
                        onSubmit={submit}
                        onForgot={() => {
                            setPanel("forgot");
                        }}
                    />

                    <div className="mt-6 flex justify-end text-sm">
                        {panel === "login" ? (
                            <button
                                type="button"
                                className="text-white/55 underline-offset-2 hover:text-white hover:underline"
                                onClick={() => {
                                    setPanel("register");
                                    setAuthModalOpen(true, "register");
                                }}
                            >
                                {t("auth.gotoRegister")}
                            </button>
                        ) : panel === "register" ? (
                            <button
                                type="button"
                                className="text-white/55 underline-offset-2 hover:text-white hover:underline"
                                onClick={() => {
                                    setPanel("login");
                                    setAuthModalOpen(true, "login");
                                }}
                            >
                                {t("auth.gotoLogin")}
                            </button>
                        ) : (
                            <button
                                type="button"
                                className="text-white/55 underline-offset-2 hover:text-white hover:underline"
                                onClick={() => {
                                    setPanel("login");
                                    setAuthModalOpen(true, "login");
                                }}
                            >
                                {t("auth.gotoLogin")}
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </Modal>
    );
}

function AuthFormBody({
    panel,
    form,
    loading,
    identityKind,
    loginMode,
    otpCooldown,
    passwordVisible,
    showEmailOption,
    onIdentityKindChange,
    onLoginModeChange,
    onPasswordVisibleChange,
    onSendOtp,
    onSubmit,
    onForgot,
}: {
    panel: PanelMode;
    form: ReturnType<typeof Form.useForm<AuthFormValues>>[0];
    loading: boolean;
    identityKind: "phone" | "email";
    loginMode: "password" | "otp";
    otpCooldown: number;
    passwordVisible: boolean;
    showEmailOption: boolean;
    onIdentityKindChange: (k: "phone" | "email") => void;
    onLoginModeChange: (m: "password" | "otp") => void;
    onPasswordVisibleChange: (v: boolean) => void;
    onSendOtp: () => void;
    onSubmit: (values: AuthFormValues) => void;
    onForgot: () => void;
}) {
    const { t } = useTranslation();

    const fieldClass =
        "h-12 rounded-lg border border-white/15 bg-black/25 text-base text-white placeholder:text-white/35 [&_input]:text-base [&_input]:text-white";

    return (
        <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            onFinish={onSubmit}
            className="auth-modal-form [&_.ant-form-item]:mb-5 [&_.ant-form-item-label]:pb-1.5"
        >
            {showEmailOption ? (
                <div className="mb-4 flex flex-wrap gap-2.5">
                    <Chip active={identityKind === "phone"} onClick={() => onIdentityKindChange("phone")}>{t("auth.phone")}</Chip>
                    <Chip active={identityKind === "email"} onClick={() => onIdentityKindChange("email")}>{t("auth.email")}</Chip>
                </div>
            ) : null}

            {panel === "login" ? (
                <div className="mb-4 flex flex-wrap gap-2.5">
                    <Chip active={loginMode === "password"} onClick={() => onLoginModeChange("password")}>{t("auth.passwordLogin")}</Chip>
                    <Chip active={loginMode === "otp"} onClick={() => onLoginModeChange("otp")}>{t("auth.otpLogin")}</Chip>
                </div>
            ) : null}

            <Form.Item
                name="identity"
                label={<span className="text-sm text-white/70">{identityKind === "phone" ? t("auth.phone") : t("auth.email")}</span>}
                rules={[{ required: true, message: t("auth.phonePlaceholder") }]}
            >
                {identityKind === "phone" ? (
                    <Input
                        size="large"
                        className={fieldClass}
                        placeholder={t("auth.phonePlaceholder")}
                        autoComplete="tel"
                        addonBefore={<span className="text-base text-white/80">+86</span>}
                    />
                ) : (
                    <Input size="large" className={fieldClass} placeholder={t("auth.emailPlaceholder")} autoComplete="email" />
                )}
            </Form.Item>

            {(panel === "register" || panel === "forgot" || (panel === "login" && loginMode === "password")) && (
                <Form.Item
                    name="password"
                    label={<span className="text-sm text-white/70">{t("auth.password")}</span>}
                    rules={[
                        { required: true, message: t("auth.passwordPlaceholder") },
                        { min: MIN_PASSWORD_LENGTH, message: t("auth.passwordPlaceholder") },
                    ]}
                >
                    <Input
                        size="large"
                        type={passwordVisible ? "text" : "password"}
                        className={fieldClass}
                        placeholder={t("auth.passwordPlaceholder")}
                        autoComplete={panel === "login" ? "current-password" : "new-password"}
                        suffix={
                            <button
                                type="button"
                                className="text-white/60 hover:text-white"
                                onClick={() => onPasswordVisibleChange(!passwordVisible)}
                                tabIndex={-1}
                            >
                                {passwordVisible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                            </button>
                        }
                    />
                </Form.Item>
            )}

            {panel === "login" && loginMode === "password" ? (
                <div className="-mt-1 mb-2 flex justify-end">
                    <button type="button" className="text-xs text-white/50 hover:text-white/80" onClick={onForgot}>
                        {t("auth.forgotPassword")}
                    </button>
                </div>
            ) : null}

            {(panel === "register" || panel === "forgot" || (panel === "login" && loginMode === "otp")) && (
                <Form.Item
                    name="otp"
                    label={<span className="text-sm text-white/70">{t("auth.otp")}</span>}
                    rules={[{ required: true, message: t("auth.otpPlaceholder") }]}
                >
                    <OtpCodeField fieldClass={fieldClass} otpCooldown={otpCooldown} onSendOtp={onSendOtp} />
                </Form.Item>
            )}

            <Button
                type="primary"
                htmlType="submit"
                block
                size="large"
                loading={loading}
                className="auth-coral-cta mt-2 !h-12 border-0 text-base font-medium"
            >
                {panel === "login" ? t("auth.login") : panel === "register" ? t("auth.startCreate") : t("auth.login")}
            </Button>
        </Form>
    );
}

function OtpCodeField({
    value,
    onChange,
    fieldClass,
    otpCooldown,
    onSendOtp,
}: {
    value?: string;
    onChange?: (value: string) => void;
    fieldClass: string;
    otpCooldown: number;
    onSendOtp: () => void;
}) {
    const { t } = useTranslation();
    return (
        <div className="flex gap-2">
            <div className="min-w-0 flex-1">
                <Input
                    size="large"
                    className={`${fieldClass} w-full`}
                    placeholder={t("auth.otpPlaceholder")}
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    maxLength={6}
                    value={value}
                    onChange={(event) => onChange?.(event.target.value)}
                />
            </div>
            <button
                type="button"
                disabled={otpCooldown > 0}
                onClick={onSendOtp}
                className={`auth-coral-cta h-12 shrink-0 rounded-lg px-4 text-sm font-medium ${otpCooldown > 0 ? "auth-otp-wait" : ""}`}
            >
                {otpCooldown > 0 ? t("auth.otpResend", { seconds: otpCooldown }) : t("auth.getOtp")}
            </button>
        </div>
    );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                active ? "studio-solid-on-white bg-white" : "bg-white/10 text-white/80 hover:bg-white/15"
            }`}
        >
            {children}
        </button>
    );
}
