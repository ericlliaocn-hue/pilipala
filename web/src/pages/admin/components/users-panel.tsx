import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, Input, InputNumber, Modal, Space, Table } from "antd";
import { RefreshCw, Search } from "lucide-react";
import { useTranslation } from "react-i18next";

import { PilipalaApiError } from "@/services/api/pilipala-client";
import { adminTopUpWallet, fetchAdminUsers, fetchAdminWalletLedger, type PilipalaAdminLedgerEntry, type PilipalaAdminUser } from "@/services/api/pilipala-admin";
import { useAuthStore } from "@/stores/use-auth-store";

/**
 * 运维面板：用户与钱包。
 *
 * <p>充值走后端的受控加额接口（服务端走单账本，前端只发一个正整数）。
 */
export function UsersPanel({ onForbidden }: { onForbidden: () => void }) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const currentUser = useAuthStore((state) => state.user);
    const [keyword, setKeyword] = useState("");
    const [users, setUsers] = useState<PilipalaAdminUser[]>([]);
    const [loading, setLoading] = useState(false);
    const [topUpTarget, setTopUpTarget] = useState<PilipalaAdminUser | null>(null);
    const [amount, setAmount] = useState<number>(100);
    const [submitting, setSubmitting] = useState(false);
    const [ledgerTarget, setLedgerTarget] = useState<PilipalaAdminUser | null>(null);
    const [ledger, setLedger] = useState<PilipalaAdminLedgerEntry[]>([]);
    const [ledgerLoading, setLedgerLoading] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setUsers(await fetchAdminUsers({ q: keyword.trim() || undefined, limit: 50 }));
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.users.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [keyword, onForbidden, message, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const openLedger = async (user: PilipalaAdminUser) => {
        setLedgerTarget(user);
        setLedgerLoading(true);
        try {
            setLedger(await fetchAdminWalletLedger(user.user_id, 50));
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.users.loadFailed"));
        } finally {
            setLedgerLoading(false);
        }
    };

    const submitTopUp = async () => {
        if (!topUpTarget) return;
        setSubmitting(true);
        try {
            await adminTopUpWallet(topUpTarget.user_id, amount);
            message.success(t("admin.users.topUpDone"));
            setTopUpTarget(null);
            // 给自己充值时同步刷新顶栏余额。
            if (currentUser?.user_id === topUpTarget.user_id) void useAuthStore.getState().refreshWallet();
            await load();
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(error instanceof Error ? error.message : t("admin.loadFailed"));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
                <Input
                    className="max-w-xs"
                    allowClear
                    prefix={<Search className="size-4 text-stone-400" />}
                    value={keyword}
                    placeholder={t("admin.users.search")}
                    onChange={(event) => setKeyword(event.target.value)}
                />
                <Button icon={<RefreshCw className="size-3.5" />} loading={loading} onClick={() => void load()}>
                    {t("common.refresh")}
                </Button>
            </div>
            <Table<PilipalaAdminUser>
                rowKey="user_id"
                size="small"
                loading={loading}
                dataSource={users}
                pagination={{ pageSize: 20, showSizeChanger: false, size: "small" }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.users.empty")} /> }}
                columns={[
                    { title: t("admin.users.username"), dataIndex: "username" },
                    { title: t("admin.users.available"), dataIndex: "available", width: 120 },
                    { title: t("admin.users.frozen"), dataIndex: "frozen", width: 120 },
                    {
                        title: t("admin.users.createdAt"),
                        dataIndex: "created_at",
                        width: 200,
                        render: (value: string) => new Date(value).toLocaleString(),
                    },
                    {
                        title: t("common.actions"),
                        width: 200,
                        render: (_, user) => (
                            <Space size={4}>
                                <Button
                                    size="small"
                                    type="text"
                                    onClick={() => {
                                        setAmount(100);
                                        setTopUpTarget(user);
                                    }}
                                >
                                    {t("admin.users.topUp")}
                                </Button>
                                <Button size="small" type="text" onClick={() => void openLedger(user)}>
                                    {t("admin.users.ledger")}
                                </Button>
                            </Space>
                        ),
                    },
                ]}
            />

            <Modal
                open={Boolean(topUpTarget)}
                title={t("admin.users.topUpTitle", { username: topUpTarget?.username ?? "" })}
                onCancel={() => setTopUpTarget(null)}
                onOk={() => void submitTopUp()}
                okText={t("common.save")}
                cancelText={t("common.cancel")}
                confirmLoading={submitting}
                destroyOnHidden
            >
                <div className="flex items-center gap-2 py-2">
                    <InputNumber className="w-40" min={1} precision={0} value={amount} onChange={(value) => setAmount(value ?? 0)} />
                    <Button size="small" onClick={() => setAmount((value) => value + 100)}>
                        +100
                    </Button>
                    <span className="text-xs text-stone-500">{t("admin.users.amount")}</span>
                </div>
            </Modal>

            <Modal open={Boolean(ledgerTarget)} title={t("admin.users.ledgerTitle", { username: ledgerTarget?.username ?? "" })} footer={null} onCancel={() => setLedgerTarget(null)} width={720} destroyOnHidden>
                <Table<PilipalaAdminLedgerEntry>
                    rowKey="id"
                    size="small"
                    loading={ledgerLoading}
                    dataSource={ledger}
                    pagination={{ pageSize: 10, showSizeChanger: false, size: "small" }}
                    locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.users.ledgerEmpty")} /> }}
                    columns={[
                        { title: t("admin.i18n.locale"), dataIndex: "type", width: 100 },
                        { title: t("admin.users.available"), dataIndex: "amount", width: 100 },
                        { title: t("admin.jobs.jobId"), dataIndex: "job_id", width: 240, render: (value: string | null) => value ?? "-" },
                        { title: t("admin.jobs.createdAt"), dataIndex: "created_at", width: 200, render: (value: string) => new Date(value).toLocaleString() },
                    ]}
                />
            </Modal>
        </div>
    );
}
