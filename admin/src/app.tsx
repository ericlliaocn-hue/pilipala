import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router";
import { Alert, Button, DatePicker, Descriptions, Divider, Drawer, Form, Input, InputNumber, Layout, Menu, Modal, Select, Space, Spin, Statistic, Table, Tag, Tree, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ApiError, downloadCsv, getToken, patch, post, put, qs, request, setToken, subscribeMutations } from "./api";
import { bannerRequestBody, canEnterAdmin, createIdempotencyKey, MENU, topupRequestBody, visibleMenu } from "./access";
import {
  adminCreditProductsPath,
  adminI18nMessagesPath,
  adminJobsPath,
  adminMembershipPlanPricesPath,
  adminMembershipPlansPath,
  adminModelsPath,
  adminRetryJob,
  adminSetCreditProductEnabled,
  adminSetMembershipPlanEnabled,
  adminSetMembershipPlanPriceEnabled,
  adminTopUpWallet,
  adminUpdateModel,
  adminUpsertI18nMessage,
  adminUsersPath,
  fetchAdminWalletLedger,
} from "./endpoints";

const { Sider, Header, Content } = Layout;
const { Title, Text } = Typography;
type Row = Record<string, any>;
type Me = { user_id: string; username?: string; platform_permissions?: string[] | null };
const ENV_NAME = import.meta.env.VITE_PILIPALA_ENV_NAME || "local";
const labels: Record<string, string> = {
  user_id: "用户 ID", username: "用户名", locale: "语言", available: "可用积分", frozen: "冻结积分", created_at: "创建时间", updated_at: "更新时间", status: "状态", id: "ID", type: "类型", amount: "金额", hold_id: "冻结单", job_id: "任务 ID", recharge_order_id: "充值单 ID", tenant_id: "租户 ID", model: "模型", capability: "能力", succeeded: "成功", failed: "失败", registered: "新注册", activated: "已激活", login_users: "登录用户", api: "API", worker: "Worker", new_api: "NewAPI", pending_outbox: "待投递 Outbox", running_jobs: "运行中任务", name: "名称", code: "编码", scope: "范围", system: "系统角色", permissions: "权限", role_codes: "角色", title: "标题", subtitle: "副标题", placement: "位置", image_object_key: "图片对象键", link_url: "跳转链接", starts_at: "开始时间", ends_at: "结束时间", sort_order: "排序", enabled: "启用", campaign_id: "活动 ID", dict_code: "字典", item_code: "项目", label: "文案", permission_code: "权限", target_type: "对象类型", target_id: "对象 ID", summary: "摘要", idempotency_key: "幂等键", out_trade_no: "商户单号", money_fen: "金额（分）", credits: "积分", package_id: "商品包", provider_trade_no: "渠道单号", paid_at: "支付时间", error_code: "错误码", prompt: "提示词", attempt: "尝试次数", max_attempts: "最大尝试", hold_status: "冻结状态", raw_status: "原始状态", raw_body: "原始响应", phone: "手机号", role: "角色", description: "说明", name_zh: "中文名称", name_en: "英文名称", price_fen: "价格（分）", bonus_credits: "赠送积分", credits_per_month: "月积分", cycle: "周期", variant_code: "规格", price: "价格", daily_ledger: "每日流水", models: "模型统计", day: "日期", topup: "加额", confirm: "确认", release: "释放", provider_tasks: "渠道任务", steps: "步骤", method: "方式", actor_user_id: "操作人", createdAt: "创建时间", updatedAt: "更新时间", roleCodes: "角色", imageObjectKey: "图片对象键", linkUrl: "跳转链接", campaignId: "活动 ID", sortOrder: "排序", userId: "用户 ID", plan_id: "方案 ID", list_price_fen: "划线价", badge_zh: "标签", meta_json: "附加信息", description_zh: "中文说明", description_en: "英文说明", template: "模板", key: "键",
};

function labelFor(key: string) { return labels[key] || key; }
function display(value: unknown): React.ReactNode {
  if (value === null || value === undefined || value === "") return <Text type="secondary">—</Text>;
  if (typeof value === "boolean") return value ? <Tag color="green">是</Tag> : <Tag>否</Tag>;
  if (typeof value === "object") return <span className="mono">{JSON.stringify(value)}</span>;
  return String(value);
}
function useLoad<T>(url: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);
  useEffect(() => subscribeMutations(refresh), [refresh]);
  useEffect(() => {
    if (!url) return;
    let alive = true;
    setBusy(true); setError("");
    request<T>(url).then((value) => { if (alive) setData(value); }).catch((e: unknown) => { if (alive) setError(e instanceof ApiError ? e.message : "加载失败"); }).finally(() => { if (alive) setBusy(false); });
    return () => { alive = false; };
  }, [url, revision]);
  return { data, busy, error, refresh };
}

function PageTitle({ title, extra }: { title: string; extra?: React.ReactNode }) {
  return <div className="page-title"><div><Text className="eyebrow">PILIPALA / OPERATIONS</Text><Title level={3}>{title}</Title></div>{extra}</div>;
}

function GenericTable({ title, url, rowKey = "id", extra, columns, transform }: { title: string; url: string | null; rowKey?: string; extra?: React.ReactNode; columns?: ColumnsType<Row>; transform?: (data: any) => Row[] }) {
  const { data, busy, error } = useLoad<any>(url);
  const rows = useMemo(() => transform ? transform(data) : Array.isArray(data) ? data : [], [data, transform]);
  const visibleColumns = columns || (rows[0] ? Object.keys(rows[0]).filter((key) => !["steps", "provider_tasks", "raw_body"].includes(key)).map((key) => ({ title: labelFor(key), dataIndex: key, key, ellipsis: true, render: (value: unknown) => display(value) })) : []);
  return <><PageTitle title={title} extra={extra} />{error && <Alert type="error" showIcon message={error} />}{!error && <Table<Row> rowKey={(row) => String(row[rowKey] ?? JSON.stringify(row))} loading={busy} columns={visibleColumns} dataSource={rows} scroll={{ x: true }} pagination={{ pageSize: 12, showSizeChanger: true }} locale={{ emptyText: "暂无数据" }} />}</>;
}

function DateFields({ onValues }: { onValues: (from: string, to: string) => void }) {
  const [anchor, setAnchor] = useState<number | null>(null);
  return <DatePicker.RangePicker format="YYYY-MM-DD" onCalendarChange={(dates) => setAnchor(dates?.[0]?.valueOf() ?? null)} disabledDate={(date) => anchor !== null && (date.valueOf() < anchor || date.valueOf() > anchor + 30 * 86400000)} onChange={(dates) => { const from = dates?.[0]?.format("YYYY-MM-DD") || ""; const to = dates?.[1]?.format("YYYY-MM-DD") || ""; setAnchor(null); onValues(from, to); }} />;
}

function OverviewPage() {
  const [dates, setDates] = useState<[string, string]>(["", ""]);
  const url = dates[0] && dates[1] ? `/api/v1/admin/stats/overview${qs({ from: dates[0], to: dates[1] })}` : null;
  const { data, busy, error } = useLoad<Row>(url);
  return <><PageTitle title="概览" extra={<DateFields onValues={(from, to) => setDates([from, to])} />} />{!url && <Alert message="请选择完整的统计日期范围" type="info" showIcon />}{error && <Alert message={error} type="error" showIcon />}{busy && <Spin />}{data && <><div className="stat-grid">{[["新注册", data.registered], ["已激活", data.activated], ["登录用户", data.login_users]].map(([name, value]) => <div className="stat-cell" key={String(name)}><Statistic title={name} value={value as number} /></div>)}</div><section className="data-section"><Title level={5}>模型成败</Title><Table rowKey="model" dataSource={data.models || []} columns={[{ title: "模型", dataIndex: "model" }, { title: "成功", dataIndex: "succeeded" }, { title: "失败", dataIndex: "failed" }]} pagination={false} /></section><section className="data-section"><Title level={5}>每日流水</Title><Table rowKey="day" dataSource={data.daily_ledger || []} columns={[{ title: "日期", dataIndex: "day" }, { title: "加额", dataIndex: "topup" }, { title: "确认", dataIndex: "confirm" }, { title: "释放", dataIndex: "release" }]} pagination={false} /></section></>}</>;
}

function StatusPage() {
  const { data, busy, error } = useLoad<Row>("/api/v1/admin/status");
  return <><PageTitle title="系统状态" />{error && <Alert type="error" message={error} showIcon />}{data && <><div className="status-grid">{["api", "worker", "new_api"].map((key) => <div className="status-cell" key={key}><Text type="secondary">{labelFor(key)}</Text><div className="status-value">{String(data[key] ?? "unknown") === "up" ? <Tag color="green">up</Tag> : String(data[key] ?? "unknown") === "down" ? <Tag color="red">down</Tag> : <Tag>unknown</Tag>}</div></div>)}</div><Descriptions bordered column={2} items={[{ key: "outbox", label: "待投递 Outbox", children: data.pending_outbox }, { key: "jobs", label: "运行中任务", children: data.running_jobs }]} /></>}{busy && <Spin />}</>;
}

function MenuPage({ permissions }: { permissions: string[] }) {
  const treeData = MENU.map((item) => ({ key: item.key, title: <Space><span>{item.label}</span><Text type="secondary">{item.permission}</Text><Tag color={permissions.includes(item.permission) ? "green" : "default"}>{permissions.includes(item.permission) ? "可见" : "无权限"}</Tag></Space>, isLeaf: true }));
  return <><PageTitle title="菜单" /><Tree treeData={treeData} selectable={false} showLine /></>;
}

function ActionModal({ title, fields, onSubmit, children, buttonText = "新建", disabled = false }: { title: string; fields: { name: string; label: string; required?: boolean; type?: "textarea" | "number" | "select"; options?: { label: string; value: string }[]; initial?: any }[]; onSubmit: (values: Row) => Promise<void>; children?: React.ReactNode; buttonText?: string; disabled?: boolean }) {
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    try {
      const values = await form.validateFields(); setBusy(true); await onSubmit(values); setOpen(false); form.resetFields();
    } catch (error) {
      if (error instanceof ApiError) showError(error);
    } finally { setBusy(false); }
  };
  return <><Button type="primary" disabled={disabled} onClick={() => { form.setFieldsValue(Object.fromEntries(fields.map((field) => [field.name, field.initial]))); setOpen(true); }}>{buttonText}</Button><Modal title={title} open={open} onCancel={() => setOpen(false)} onOk={submit} confirmLoading={busy} destroyOnClose>{children}<Form form={form} layout="vertical">{fields.map((field) => <Form.Item key={field.name} name={field.name} label={field.label} rules={[...(field.required ? [{ required: true, whitespace: true, message: `请填写${field.label}` }] : []), ...(field.name === "reason" ? [{ max: 200, message: "原因不能超过 200 个字符" }] : [])]}>{field.type === "textarea" ? <Input.TextArea rows={3} maxLength={field.name === "reason" ? 200 : undefined} showCount={field.name === "reason"} /> : field.type === "number" ? <InputNumber min={field.name === "amount" ? 1 : 0} precision={0} style={{ width: "100%" }} /> : field.type === "select" ? <Select options={field.options} /> : <Input />}</Form.Item>)}</Form></Modal></>;
}

function UsersPage({ permissions, me }: { permissions: string[]; me: Me }) {
  const [query, setQuery] = useState(""); const [dates, setDates] = useState<[string, string]>(["", ""]); const [revealed, setRevealed] = useState<Record<string, string>>({}); const [ledger, setLedger] = useState<{ userId: string; rows: Row[] } | null>(null);
  const { data, busy, error, refresh } = useLoad<Row[]>(adminUsersPath({ q: query, limit: 200 }));
  const rows = data || [];
  const perform = async (fn: () => Promise<unknown>) => { await fn(); message.success("操作已完成"); refresh(); };
  const columns: ColumnsType<Row> = [
    { title: "用户名", dataIndex: "username" }, { title: "用户 ID", dataIndex: "user_id", ellipsis: true }, { title: "语言", dataIndex: "locale" }, { title: "可用积分", dataIndex: "available" }, { title: "冻结积分", dataIndex: "frozen" }, { title: "创建时间", dataIndex: "created_at" },
    { title: "操作", key: "actions", fixed: "right", render: (_, row) => <Space wrap>
      {permissions.includes("user.read_phone") && (revealed[row.user_id] ? <Text>{revealed[row.user_id]}</Text> : <Button size="small" onClick={() => perform(async () => { const result = await post<Row>(`/api/v1/admin/users/${row.user_id}/phone-reveal`); setRevealed((state) => ({ ...state, [row.user_id]: result.phone })); })}>查看</Button>)}
      {permissions.includes("wallet.topup") && <ActionModal title={`为 ${row.username} 加额`} buttonText="加额" fields={[{ name: "amount", label: "积分数量", required: true, type: "number" }, { name: "reason", label: "原因", required: true, type: "textarea" }]} onSubmit={async (v) => { await adminTopUpWallet(row.user_id, topupRequestBody(v.amount, v.reason)); message.success("加额已提交"); refresh(); }} />}
      {permissions.includes("ledger.read") && <Button size="small" onClick={async () => { const rows = await fetchAdminWalletLedger(row.user_id); setLedger({ userId: row.user_id, rows }); }}>流水</Button>}
      {"status" in row && row.user_id !== me.user_id && row.status !== "disabled" && permissions.includes("user.disable") && <ActionModal title={`封禁 ${row.username}`} buttonText="封禁" fields={[{ name: "reason", label: "原因", required: true, type: "textarea" }]} onSubmit={async () => { await post(`/api/v1/admin/users/${row.user_id}/disable`); message.success("账号已封禁"); refresh(); }} />}
      {"status" in row && row.status === "disabled" && permissions.includes("user.enable") && <ActionModal title={`启用 ${row.username}`} buttonText="启用" fields={[{ name: "reason", label: "原因", required: true, type: "textarea" }]} onSubmit={async () => { await post(`/api/v1/admin/users/${row.user_id}/enable`); message.success("账号已启用"); refresh(); }} />}
    </Space> },
  ];
  return <><PageTitle title="用户与钱包" extra={<Space wrap>{permissions.includes("ledger.read") && <><DateFields onValues={(from, to) => setDates([from, to])} /><Button disabled={!dates[0] || !dates[1]} onClick={() => { if (!validRange(...dates)) return message.error("日期范围最长 31 天"); void downloadCsv(`/api/v1/admin/exports/ledger${qs({ from: dates[0], to: dates[1] })}`, `ledger-${dates[0]}-${dates[1]}.csv`).catch(showError); }}>导出流水</Button></>}<Input.Search allowClear placeholder="搜索用户" onSearch={setQuery} style={{ width: 260 }} /></Space>} />{error && <Alert type="error" message={error} showIcon />}{rows.length > 0 && !("status" in rows[0]) && <Alert className="page-warning" type="warning" showIcon message="用户接口未返回 status，已隐藏封禁与启用操作。" />}<Table rowKey="user_id" loading={busy} dataSource={rows} columns={columns} scroll={{ x: 1450 }} pagination={{ pageSize: 12 }} locale={{ emptyText: "暂无用户" }} />{ledger && <Drawer title="钱包流水" open onClose={() => setLedger(null)} width={760}><Table rowKey="id" dataSource={ledger.rows} columns={Object.keys(ledger.rows[0] || {}).map((key) => ({ title: labelFor(key), dataIndex: key, render: display }))} pagination={{ pageSize: 10 }} /></Drawer>}</>;
}

function validRange(from: string, to: string) { const days = (Date.parse(`${to}T00:00:00`) - Date.parse(`${from}T00:00:00`)) / 86400000; return Number.isFinite(days) && days >= 0 && days <= 30; }
function showError(error: unknown) { message.error(error instanceof ApiError ? error.message : "操作失败"); }

function RechargePage({ permissions }: { permissions: string[] }) {
  const { data, busy, error } = useLoad<Row[]>(`/api/v1/admin/billing/recharge-orders${qs({ limit: 200 })}`);
  const [dates, setDates] = useState<[string, string]>(["", ""]);
  const columns: ColumnsType<Row> = [
    { title: "充值单", dataIndex: "id", ellipsis: true }, { title: "用户", dataIndex: "user_id" }, { title: "商户单号", dataIndex: "out_trade_no" }, { title: "状态", dataIndex: "status", render: display }, { title: "金额（分）", dataIndex: "money_fen" }, { title: "积分", dataIndex: "credits" }, { title: "商品包", dataIndex: "package_id" }, { title: "渠道单号", dataIndex: "provider_trade_no" }, { title: "支付时间", dataIndex: "paid_at" }, { title: "创建时间", dataIndex: "created_at" },
    ...(permissions.includes("recharge.refund") ? [{ title: "操作", fixed: "right" as const, render: (_: unknown, row: Row) => <ActionModal title={`退款 ${row.out_trade_no}`} buttonText="退款" fields={[{ name: "reason", label: "原因", required: true, type: "textarea" }]} onSubmit={async (v) => { await post(`/api/v1/admin/billing/recharge-orders/${row.id}/refund`, { idempotency_key: createIdempotencyKey(), reason: v.reason }); message.success("退款已提交"); }} /> }] : []),
  ];
  return <><PageTitle title="充值订单" extra={permissions.includes("recharge.read") && <Space><DateFields onValues={(from, to) => setDates([from, to])} /><Button disabled={!dates[0] || !dates[1]} onClick={() => { if (!validRange(...dates)) return message.error("日期范围最长 31 天"); void downloadCsv(`/api/v1/admin/exports/recharge-orders${qs({ from: dates[0], to: dates[1] })}`, `recharge-orders-${dates[0]}-${dates[1]}.csv`).catch(showError); }}>导出</Button></Space>} />{error && <Alert type="error" message={error} showIcon />}<Table rowKey="id" loading={busy} dataSource={data || []} columns={columns} scroll={{ x: 1400 }} pagination={{ pageSize: 12 }} locale={{ emptyText: "暂无充值订单" }} /></>;
}

function JobsPage({ permissions }: { permissions: string[] }) {
  const columns: ColumnsType<Row> = [
    { title: "任务 ID", dataIndex: "job_id", ellipsis: true }, { title: "用户", dataIndex: "username" }, { title: "状态", dataIndex: "status" }, { title: "能力", dataIndex: "capability" }, { title: "模型", dataIndex: "model" }, { title: "错误码", dataIndex: "error_code" }, { title: "冻结状态", dataIndex: "hold_status" }, { title: "尝试", render: (_, row) => `${row.attempt}/${row.max_attempts}` }, { title: "创建时间", dataIndex: "created_at" },
    { title: "详情", render: (_, row) => <Button type="link" onClick={() => Modal.info({ title: `任务 ${row.job_id}`, width: 800, content: <div className="job-detail"><Descriptions column={2} items={Object.entries(row).filter(([key]) => !["steps", "provider_tasks"].includes(key)).map(([key, value]) => ({ key, label: labelFor(key), children: display(value) }))} /><Divider /><Title level={5}>步骤</Title><pre>{JSON.stringify(row.steps || [], null, 2)}</pre>{permissions.includes("job.provider_raw") && (row.provider_tasks || []).some((task: Row) => task.raw_status != null || task.raw_body != null) && <><Divider /><Title level={5}>上游原文</Title><pre>{JSON.stringify((row.provider_tasks || []).filter((task: Row) => task.raw_status != null || task.raw_body != null), null, 2)}</pre></>}</div> })}>查看</Button> },
    { title: "操作", fixed: "right", render: (_, row) => <Space>{permissions.includes("job.retry") && <Button size="small" onClick={() => adminRetryJob(row.job_id).then(() => message.success("已提交重试")).catch(showError)}>重试</Button>}{permissions.includes("job.cancel") && <ActionModal title={`取消任务 ${row.job_id}`} buttonText="取消" fields={[{ name: "reason", label: "原因", required: true, type: "textarea" }]} onSubmit={async () => { await post(`/api/v1/admin/jobs/${row.job_id}/cancel`); message.success("任务已取消"); }} />}</Space> },
  ];
  return <><GenericTable title="任务" url={adminJobsPath({ limit: 200 })} rowKey="job_id" columns={columns} /></>;
}

function TenantsPage({ permissions }: { permissions: string[] }) {
  const [type, setType] = useState(""); const [status, setStatus] = useState(""); const { data, busy, error } = useLoad<Row[]>(`/api/v1/admin/tenants${qs({ type, status, limit: 200, offset: 0 })}`); const [members, setMembers] = useState<Row[] | null>(null);
  return <><PageTitle title="租户" extra={<Space><Select allowClear placeholder="类型" style={{ width: 120 }} onChange={(v) => setType(v || "")} options={[{ label: "个人", value: "personal" }, { label: "组织", value: "organization" }]} /><Select allowClear placeholder="状态" style={{ width: 120 }} onChange={(v) => setStatus(v || "")} options={[{ label: "启用", value: "active" }, { label: "停用", value: "disabled" }]} /></Space>} />{error && <Alert type="error" message={error} showIcon />}<Table rowKey="id" loading={busy} dataSource={data || []} columns={[{ title: "租户", dataIndex: "name" }, { title: "ID", dataIndex: "id" }, { title: "类型", dataIndex: "type" }, { title: "状态", dataIndex: "status" }, { title: "创建时间", dataIndex: "created_at" }, ...(permissions.includes("tenant.member.read") ? [{ title: "成员", render: (_: unknown, row: Row) => <Button type="link" onClick={() => request<Row[]>(`/api/v1/admin/tenants/${row.id}/members`).then(setMembers).catch(showError)}>查看成员</Button> }] : [])]} pagination={{ pageSize: 12 }} />{members && <Drawer title="租户成员" open onClose={() => setMembers(null)} width={640}><Table rowKey="user_id" dataSource={members} columns={Object.keys(members[0] || {}).map((key) => ({ title: labelFor(key), dataIndex: key, render: display }))} pagination={false} /></Drawer>}</>;
}

function StaffPage({ permissions }: { permissions: string[] }) {
  const { data: staff, error: staffError } = useLoad<Row[]>("/api/v1/admin/staff"); const { data: roles, error: rolesError } = useLoad<Row[]>(permissions.includes("role.read") ? "/api/v1/admin/roles" : null); const { data: permissionsList } = useLoad<Row[]>(permissions.includes("role.read") ? "/api/v1/admin/permissions" : null);
  const columns: ColumnsType<Row> = [{ title: "用户名", dataIndex: "username" }, { title: "用户 ID", dataIndex: "user_id" }, { title: "状态", dataIndex: "status" }, { title: "平台角色", dataIndex: "role_codes", render: (value) => (value || []).join("、") }, ...(permissions.includes("staff.assign_role") ? [{ title: "操作", render: (_: unknown, row: Row) => <ActionModal title={`调整 ${row.username} 的角色`} buttonText="改角色" fields={[{ name: "reason", label: "原因", required: true, type: "textarea" }, { name: "role_codes", label: "角色编码（逗号分隔）", required: false, type: "textarea", initial: (row.role_codes || []).join(",") }]} onSubmit={async (v) => { const roleCodes = String(v.role_codes || "").split(",").map((value) => value.trim()).filter(Boolean); await put(`/api/v1/admin/staff/${row.user_id}/roles`, { role_codes: roleCodes }); message.success("角色已更新"); }} /> }] : [])];
  const roleColumns: ColumnsType<Row> = [{ title: "编码", dataIndex: "code" }, { title: "名称", dataIndex: "name" }, { title: "范围", dataIndex: "scope" }, { title: "权限", dataIndex: "permissions", render: (value) => (value || []).join("、") }, { title: "系统角色", dataIndex: "system", render: display }, ...(permissions.includes("role.write") ? [{ title: "操作", render: (_: unknown, row: Row) => row.system ? null : <Space><ActionModal title={`修改 ${row.name} 权限`} buttonText="修改权限" fields={[{ name: "permission_codes", label: "权限编码（逗号分隔）", type: "textarea", initial: (row.permissions || []).join(",") }]} onSubmit={async (v) => { await put(`/api/v1/admin/roles/${row.id}/permissions`, { permission_codes: String(v.permission_codes || "").split(",").map((value) => value.trim()).filter(Boolean) }); message.success("权限已更新"); }} /><Button size="small" danger onClick={() => Modal.confirm({ title: `删除角色 ${row.name}？`, content: "删除操作将由服务端校验角色分配情况。", onOk: () => request(`/api/v1/admin/roles/${row.id}`, { method: "DELETE" }).then(() => message.success("角色已删除")).catch(showError) })}>删除</Button></Space> }] : [])];
  return <><PageTitle title="员工与角色" extra={permissions.includes("role.write") ? <ActionModal title="新建自定义角色" fields={[{ name: "code", label: "角色编码", required: true }, { name: "name", label: "角色名称", required: true }, { name: "scope", label: "范围", required: true, type: "select", options: [{ label: "平台", value: "platform" }, { label: "租户", value: "tenant" }] }, { name: "permission_codes", label: "权限编码（逗号分隔）", type: "textarea" }]} onSubmit={async (v) => { await post("/api/v1/admin/roles", { code: v.code, name: v.name, scope: v.scope, permission_codes: String(v.permission_codes || "").split(",").map((s) => s.trim()).filter(Boolean) }); message.success("角色已创建"); }} /> : null} />{staffError && <Alert type="error" message={staffError} showIcon />}<Title level={5}>员工</Title><Table rowKey="user_id" dataSource={staff || []} columns={columns} pagination={{ pageSize: 10 }} />{permissions.includes("role.read") && <><Divider /><Title level={5}>角色</Title>{rolesError && <Alert type="error" message={rolesError} showIcon />}<Table rowKey="id" dataSource={roles || []} columns={roleColumns} pagination={{ pageSize: 10 }} /><details className="permission-catalog"><summary>权限目录</summary><Text type="secondary">{(permissionsList || []).map((item) => `${item.code} (${item.scope})`).join(" · ")}</Text></details></>}</>;
}

function DictionaryPage({ permissions }: { permissions: string[] }) {
  const { data, busy, error } = useLoad<Row[]>("/api/v1/admin/dictionaries"); const [editing, setEditing] = useState<Row | null>(null); const [form] = Form.useForm();
  return <><PageTitle title="字典" /><Text type="secondary">仅允许编辑现有条目的文案和排序。</Text>{error && <Alert type="error" message={error} showIcon />}<Table rowKey={(row) => `${row.dict_code}:${row.item_code}:${row.locale}`} loading={busy} dataSource={data || []} columns={[{ title: "字典", dataIndex: "dict_code" }, { title: "项目", dataIndex: "item_code" }, { title: "语言", dataIndex: "locale" }, { title: "文案", dataIndex: "label" }, { title: "排序", dataIndex: "sort_order" }, ...(permissions.includes("dictionary.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <Button type="link" onClick={() => { setEditing(row); form.setFieldsValue(row); }}>编辑</Button> }] : [])]} pagination={{ pageSize: 15 }} /><Modal title="编辑字典条目" open={!!editing} onCancel={() => setEditing(null)} onOk={async () => { const v = await form.validateFields(); try { await put("/api/v1/admin/dictionaries", { dict_code: editing!.dict_code, item_code: editing!.item_code, locale: editing!.locale, label: v.label, sort_order: v.sort_order }); setEditing(null); message.success("已保存"); } catch (error) { showError(error); throw error; } }}><Form form={form} layout="vertical"><Form.Item name="label" label="文案" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="sort_order" label="排序" rules={[{ required: true }]}><InputNumber style={{ width: "100%" }} /></Form.Item></Form></Modal></>;
}

function I18nPage({ permissions }: { permissions: string[] }) {
  const [q, setQ] = useState("");
  return <><GenericTable title="文案" url={adminI18nMessagesPath(q)} extra={<Input.Search placeholder="搜索键" allowClear onSearch={setQ} style={{ width: 260 }} />} rowKey="id" columns={[{ title: "键", dataIndex: "key" }, { title: "语言", dataIndex: "locale" }, { title: "文案模板", dataIndex: "template", ellipsis: true }, { title: "更新时间", dataIndex: "updated_at" }, ...(permissions.includes("i18n.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <ActionModal title={`编辑 ${row.key}`} buttonText="编辑" fields={[{ name: "template", label: "文案模板", required: true, type: "textarea", initial: row.template }]} onSubmit={async (v) => { await adminUpsertI18nMessage({ key: row.key, locale: row.locale, template: v.template }); message.success("文案已更新"); }} /> }] : [])]} /></>;
}

function BannersPage({ permissions }: { permissions: string[] }) {
  const fields = (row?: Row) => [{ name: "placement", label: "位置", required: true, type: "select" as const, options: [{ label: "首页推广", value: "home_promo" }, { label: "会员", value: "membership" }], initial: row?.placement }, { name: "title", label: "标题", required: true, initial: row?.title }, { name: "subtitle", label: "副标题", initial: row?.subtitle }, { name: "image_object_key", label: "图片对象键", initial: row?.image_object_key }, { name: "link_url", label: "跳转链接", initial: row?.link_url }, { name: "starts_at", label: "开始时间（ISO）", initial: row?.starts_at }, { name: "ends_at", label: "结束时间（ISO）", initial: row?.ends_at }, { name: "sort_order", label: "排序", type: "number" as const, initial: row?.sort_order ?? 0 }, { name: "enabled", label: "启用", type: "select" as const, options: [{ label: "启用", value: "true" }, { label: "关闭", value: "false" }], initial: row ? String(Boolean(row.enabled)) : "false" }, { name: "campaign_id", label: "活动 ID（可空）", initial: row?.campaign_id }];
  return <><GenericTable title="Banner" url="/api/v1/admin/banners" rowKey="id" extra={permissions.includes("banner.write") && <ActionModal title="新建 Banner" fields={fields()} onSubmit={async (v) => { await post("/api/v1/admin/banners", bannerRequestBody(v)); message.success("Banner 已创建"); }} />} columns={[{ title: "标题", dataIndex: "title" }, { title: "位置", dataIndex: "placement" }, { title: "图片对象键", dataIndex: "image_object_key", ellipsis: true }, { title: "开始时间", dataIndex: "starts_at" }, { title: "结束时间", dataIndex: "ends_at" }, { title: "排序", dataIndex: "sort_order" }, { title: "启用", dataIndex: "enabled", render: display }, ...(permissions.includes("banner.write") ? [{ title: "操作", fixed: "right" as const, render: (_: unknown, row: Row) => <Space><ActionModal title="编辑 Banner" buttonText="编辑" fields={fields(row)} onSubmit={async (v) => { await put(`/api/v1/admin/banners/${row.id}`, bannerRequestBody(v)); message.success("Banner 已更新"); }} /><Button size="small" onClick={() => post(`/api/v1/admin/banners/${row.id}/enable`).then(() => message.success("Banner 已启用")).catch(showError)}>启用</Button></Space> }] : [])]} /></>;
}

function ModelsPage({ permissions }: { permissions: string[] }) {
  return <GenericTable title="模型展示" url={adminModelsPath()} rowKey="model" columns={[{ title: "模型", dataIndex: "model" }, { title: "能力", dataIndex: "capability" }, { title: "展示名称", dataIndex: "display_name" }, { title: "启用", dataIndex: "enabled", render: display }, { title: "排序", dataIndex: "sort_order" }, ...(permissions.includes("model.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <ActionModal title={`编辑 ${row.model}`} buttonText="编辑" fields={[{ name: "display_name", label: "展示名称", required: true, initial: row.display_name }, { name: "capability", label: "能力", initial: row.capability }, { name: "enabled", label: "启用", type: "select", options: [{ label: "启用", value: "true" }, { label: "停用", value: "false" }], initial: String(row.enabled) }, { name: "sort_order", label: "排序", type: "number", initial: row.sort_order }]} onSubmit={async (v) => { await adminUpdateModel(row.model, { display_name: v.display_name, capability: v.capability, enabled: v.enabled === "true", sort_order: v.sort_order }); message.success("模型配置已更新"); }} /> }] : [])]} />;
}

function VideoPage({ permissions }: { permissions: string[] }) {
  const { data: products, error: productsError } = useLoad<Row[]>("/api/v1/admin/video/products"); const { data: groups, error: groupsError } = useLoad<Row[]>("/api/v1/admin/video/groups");
  return <><PageTitle title="视频产品" />{productsError && <Alert type="error" message={productsError} showIcon />}{groupsError && <Alert type="error" message={groupsError} showIcon />}<Table rowKey="code" dataSource={products || []} columns={[{ title: "编码", dataIndex: "code" }, { title: "展示名称", dataIndex: "display_name" }, { title: "能力", dataIndex: "capability" }, { title: "价格（积分）", dataIndex: "price_credits" }, { title: "上架", dataIndex: "enabled", render: display }, ...(permissions.includes("model.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <Button onClick={() => put(`/api/v1/admin/video/products/${encodeURIComponent(row.code)}/enabled`, { enabled: !row.enabled }).then(() => message.success("状态已更新")).catch(showError)}>{row.enabled ? "下架" : "上架"}</Button> }] : [])]} pagination={{ pageSize: 12 }} /><Divider /><Title level={5}>分组</Title><Table rowKey="id" dataSource={groups || []} columns={[{ title: "分组", dataIndex: "group_code" }, { title: "模型", dataIndex: "model_code" }, { title: "产品", dataIndex: "product_id", ellipsis: true }, { title: "优先级", dataIndex: "priority" }, { title: "超时（秒）", dataIndex: "timeout_seconds" }, { title: "成本上限", dataIndex: "max_cost_amount" }, { title: "启用", dataIndex: "enabled", render: display }]} pagination={{ pageSize: 12 }} /></>;
}

function CreditProductsPage({ permissions }: { permissions: string[] }) {
  const columns: ColumnsType<Row> = [{ title: "编码", dataIndex: "code" }, { title: "中文名称", dataIndex: "name_zh" }, { title: "英文名称", dataIndex: "name_en" }, { title: "价格（分）", dataIndex: "price_fen" }, { title: "积分", dataIndex: "credits" }, { title: "赠送", dataIndex: "bonus_credits" }, { title: "启用", dataIndex: "enabled", render: display }, ...(permissions.includes("credit_product.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <Button onClick={() => adminSetCreditProductEnabled(row.id, !row.enabled).then(() => message.success("状态已更新")).catch(showError)}>{row.enabled ? "停用" : "启用"}</Button> }] : [])];
  return <GenericTable title="积分商品" url={adminCreditProductsPath()} rowKey="id" columns={columns} />;
}

function MembershipPage({ permissions }: { permissions: string[] }) {
  const { data: plans, error: plansError } = useLoad<Row[]>(adminMembershipPlansPath()); const { data: prices, error: pricesError } = useLoad<Row[]>(adminMembershipPlanPricesPath());
  return <><PageTitle title="会员" />{plansError && <Alert type="error" message={plansError} showIcon />}{pricesError && <Alert type="error" message={pricesError} showIcon />}<Title level={5}>会员方案</Title><Table rowKey="id" dataSource={plans || []} columns={[{ title: "编码", dataIndex: "code" }, { title: "中文名称", dataIndex: "name_zh" }, { title: "英文名称", dataIndex: "name_en" }, { title: "月积分", dataIndex: "credits_per_month" }, { title: "启用", dataIndex: "enabled", render: display }, ...(permissions.includes("membership.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <Button onClick={() => adminSetMembershipPlanEnabled(row.id, !row.enabled).then(() => message.success("状态已更新")).catch(showError)}>{row.enabled ? "停用" : "启用"}</Button> }] : [])]} pagination={false} /><Divider /><Title level={5}>方案价格</Title><Table rowKey="id" dataSource={prices || []} columns={[{ title: "方案", dataIndex: "plan_id" }, { title: "周期", dataIndex: "cycle" }, { title: "规格", dataIndex: "variant_code" }, { title: "价格（分）", dataIndex: "price_fen" }, { title: "月积分", dataIndex: "credits_per_month" }, { title: "启用", dataIndex: "enabled", render: display }, ...(permissions.includes("membership.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <Button onClick={() => adminSetMembershipPlanPriceEnabled(row.id, !row.enabled).then(() => message.success("价格状态已更新")).catch(showError)}>{row.enabled ? "停用" : "启用"}</Button> }] : [])]} pagination={false} /></>;
}

function RuntimePage({ permissions }: { permissions: string[] }) {
  const keys = ["auth.email_login_enabled", "auth.email_register_enabled"];
  const { data, busy, error } = useLoad<Row>("/api/v1/admin/runtime-settings");
  const missing = data ? keys.filter((key) => typeof data[key] !== "string") : [];
  const rows = keys.filter((key) => typeof data?.[key] === "string").map((key) => ({ key, value: data![key] }));
  return <><PageTitle title="运行开关" />{error && <Alert type="error" message={error} showIcon />}{missing.length > 0 && <Alert className="page-warning" type="error" showIcon message={`接口缺少配置：${missing.join("、")}`} />}{data && <Table rowKey="key" loading={busy} dataSource={rows} columns={[{ title: "配置键", dataIndex: "key" }, { title: "当前值", dataIndex: "value", render: display }, ...(permissions.includes("runtime.write") ? [{ title: "操作", render: (_: unknown, row: Row) => <Button onClick={() => patch("/api/v1/admin/runtime-settings", { [row.key]: row.value === "true" ? "false" : "true" }).then(() => message.success("开关已更新")).catch(showError)}>{row.value === "true" ? "关闭" : "开启"}</Button> }] : [])]} pagination={false} />}</>;
}

function LoginPage({ onLogin, denied }: { onLogin: () => Promise<void>; denied: boolean }) {
  const [form] = Form.useForm(); const [sending, setSending] = useState(false); const [submitting, setSubmitting] = useState(false); const [feedback, setFeedback] = useState(""); const [notice, contextHolder] = message.useMessage();
  const send = async () => {
    const phone = await form.validateFields(["phone"]).then((v) => v.phone);
    setSending(true);
    try { await post("/api/v1/auth/otp/send", { channel: "sms", destination: phone, purpose: "login" }); notice.success("验证码已发送"); }
    catch (error) { notice.error(error instanceof ApiError ? error.message : "发送失败"); }
    finally { setSending(false); }
  };
  const login = async (values: Row) => {
    setSubmitting(true); setFeedback("");
    try { const result = await post<Row>("/api/v1/auth/login", { phone: values.phone, otp: values.otp }); if (!result.token) throw new ApiError("登录响应缺少令牌"); setToken(result.token); await onLogin(); }
    catch (error) { setToken(null); const text = error instanceof ApiError ? error.message : "登录失败"; setFeedback(text); }
    finally { setSubmitting(false); }
  };
  return <div className="login-shell">{contextHolder}<section className="login-panel"><div className="brand-mark">P</div><Text className="eyebrow">PILIPALA / OPERATIONS</Text><Title level={2}>运维后台</Title><Text type="secondary">使用平台账号登录</Text>{denied && <Alert className="login-alert" type="warning" showIcon message="没有运维后台权限" />}{feedback && <Alert className="login-alert" type="error" showIcon message={feedback} />}
    <Form form={form} layout="vertical" onFinish={login} requiredMark={false}><Form.Item name="phone" label="手机号" rules={[{ required: true, message: "请输入手机号" }, { pattern: /^\+?[0-9]{6,20}$/, message: "手机号格式不正确" }]}><Input size="large" autoComplete="tel" /></Form.Item><Form.Item name="otp" label="短信验证码" rules={[{ required: true, message: "请输入验证码" }]}><Input size="large" autoComplete="one-time-code" addonAfter={<Button type="link" loading={sending} onClick={send}>发送验证码</Button>} /></Form.Item><Button block type="primary" size="large" htmlType="submit" loading={submitting}>登录</Button></Form></section><div className="login-rail"><span>SECURE OPERATIONS</span><span>ENV · {ENV_NAME}</span></div></div>;
}

function AuditPage() { return <GenericTable title="审计" url="/api/v1/admin/audit-logs" rowKey="id" columns={[{ title: "时间", dataIndex: "created_at" }, { title: "操作人", dataIndex: "actor_user_id" }, { title: "权限", dataIndex: "permission_code" }, { title: "对象类型", dataIndex: "target_type" }, { title: "对象 ID", dataIndex: "target_id" }, { title: "幂等键", dataIndex: "idempotency_key" }, { title: "摘要", dataIndex: "summary" }]} />; }

function ExceptionsPage() {
  const typeNames: Record<string, string> = { succeeded_hold_unconfirmed: "succeeded_hold_unconfirmed", terminal_hold_unreleased: "terminal_hold_unreleased", paid_without_ledger: "paid_without_ledger", running_timeout: "running_timeout" };
  return <GenericTable title="异常" url="/api/v1/admin/stats/exceptions" columns={[{ title: "异常类型", dataIndex: "type", render: (value) => typeNames[value] || value }, { title: "任务 ID", dataIndex: "job_id" }, { title: "充值单 ID", dataIndex: "recharge_order_id" }, { title: "租户 ID", dataIndex: "tenant_id" }, { title: "冻结单 ID", dataIndex: "hold_id" }, { title: "状态", dataIndex: "status" }, { title: "更新时间", dataIndex: "updated_at" }]} />;
}

function Page({ routeKey, permissions, me }: { routeKey: string; permissions: string[]; me: Me }) {
  if (routeKey === "overview") return <OverviewPage />;
  if (routeKey === "exceptions") return <ExceptionsPage />;
  if (routeKey === "status") return <StatusPage />;
  if (routeKey === "users") return <UsersPage permissions={permissions} me={me} />;
  if (routeKey === "recharges") return <RechargePage permissions={permissions} />;
  if (routeKey === "jobs") return <JobsPage permissions={permissions} />;
  if (routeKey === "tenants") return <TenantsPage permissions={permissions} />;
  if (routeKey === "staff") return <StaffPage permissions={permissions} />;
  if (routeKey === "menu") return <MenuPage permissions={permissions} />;
  if (routeKey === "dictionaries") return <DictionaryPage permissions={permissions} />;
  if (routeKey === "i18n") return <I18nPage permissions={permissions} />;
  if (routeKey === "banners") return <BannersPage permissions={permissions} />;
  if (routeKey === "models") return <ModelsPage permissions={permissions} />;
  if (routeKey === "video") return <VideoPage permissions={permissions} />;
  if (routeKey === "credit-products") return <CreditProductsPage permissions={permissions} />;
  if (routeKey === "membership") return <MembershipPage permissions={permissions} />;
  if (routeKey === "runtime") return <RuntimePage permissions={permissions} />;
  return <AuditPage />;
}

function GuardedRoute({ permissions, routeKey, me }: { permissions: string[]; routeKey: string; me: Me }) {
  if (!visibleMenu(permissions).some((item) => item.key === routeKey)) return <Navigate to="/" replace />;
  return <Page routeKey={routeKey} permissions={permissions} me={me} />;
}

export default function App() {
  const navigate = useNavigate(); const location = useLocation(); const [me, setMe] = useState<Me | null>(null); const [checking, setChecking] = useState(true); const [denied, setDenied] = useState(false); const [mobileOpen, setMobileOpen] = useState(false);
  const permissions = Array.isArray(me?.platform_permissions) ? me!.platform_permissions! : [];
  const entries = visibleMenu(permissions);
  const current = location.pathname.split("/").filter(Boolean)[0] || entries[0]?.key || "";
  const loadMe = useCallback(async () => {
    const currentMe = await request<Me>("/api/v1/auth/me");
    setMe(currentMe);
    if (!canEnterAdmin(currentMe.platform_permissions)) { setDenied(true); throw new ApiError("没有运维后台权限"); }
    setDenied(false);
  }, []);
  useEffect(() => {
    const token = getToken();
    if (!token) { setChecking(false); return; }
    loadMe().catch((error) => { setToken(null); if (!(error instanceof ApiError && error.message === "没有运维后台权限")) setDenied(false); }).finally(() => setChecking(false));
  }, [loadMe]);
  const logout = async () => {
    try { await post("/api/v1/auth/logout"); } catch { /* 服务端登出失败也清掉本地令牌。 */ }
    setToken(null); setMe(null); setDenied(false); navigate("/login", { replace: true });
  };
  if (checking) return <div className="loading-screen"><Spin size="large" /></div>;
  if (!me || !canEnterAdmin(me.platform_permissions)) return <LoginPage denied={denied} onLogin={async () => { await loadMe(); navigate("/", { replace: true }); }} />;
  const menu = <Menu mode="inline" selectedKeys={[current]} items={entries.map(({ key, label }) => ({ key, label: <Link to={`/${key}`} onClick={() => setMobileOpen(false)}>{label}</Link> }))} />;
  return <Layout className="app-layout"><Sider width={232} className="app-sider" breakpoint="lg" collapsedWidth={0} trigger={null}><div className="brand"><div className="brand-mark small">P</div><div><strong>pilipala</strong><span>OPERATIONS</span></div></div><div className="nav-caption">工作台</div>{menu}<div className="sider-bottom"><span className="environment-dot" />{ENV_NAME}<span className="sider-user">{me.username || me.user_id}</span><Button type="text" onClick={logout}>退出</Button></div></Sider><Layout><Header className="topbar"><Button className="mobile-nav" onClick={() => setMobileOpen(true)}>菜单</Button><div className="top-env"><span className="environment-dot" />{ENV_NAME}</div></Header><Content className="content"><Routes>{entries.map((item) => <Route key={item.key} path={`/${item.key}`} element={<GuardedRoute permissions={permissions} routeKey={item.key} me={me} />} />)}<Route path="/login" element={<Navigate to="/" replace />} /><Route path="*" element={<Navigate to={entries[0] ? `/${entries[0].key}` : "/login"} replace />} /></Routes></Content></Layout><Drawer title="导航" placement="left" open={mobileOpen} onClose={() => setMobileOpen(false)}>{menu}</Drawer></Layout>;
}
