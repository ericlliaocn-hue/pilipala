import { afterAll, expect, test } from "bun:test";

// The admin client keeps its token in the admin site's own localStorage.
const values = new Map<string, string>();
const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  },
});
values.set("pilipala:admin_token", "test-token");

const originalFetch = globalThis.fetch;
afterAll(() => {
  if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
  else Reflect.deleteProperty(globalThis, "localStorage");
  globalThis.fetch = originalFetch;
});

const {
  adminCreditProductsPath,
  adminI18nMessagesPath,
  adminJobsPath,
  adminMembershipPlanPricesPath,
  adminMembershipPlansPath,
  adminModelsPath,
  adminRetryJob,
  adminTopUpWallet,
  adminUpdateModel,
  adminUpsertI18nMessage,
  adminUsersPath,
  adminWalletLedgerPath,
  adminWalletTopupPath,
  fetchAdminJobs,
  fetchAdminI18nMessages,
  fetchAdminUsers,
  fetchAdminWalletLedger,
} = await import("./endpoints");

const USER_ID = "22222222-2222-2222-2222-222222222222";
const JOB_ID = "33333333-3333-3333-3333-333333333333";

type RecordedCall = { url: string; method: string; headers: Record<string, string>; body: string };

function json(payload: unknown) {
  return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

function mockFetch(handler: (url: string, method: string) => Response | Promise<Response>) {
  const calls: RecordedCall[] = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const headers = (init?.headers || {}) as Record<string, string>;
    calls.push({ url, method: init?.method || "GET", headers, body: typeof init?.body === "string" ? init.body : "" });
    return await handler(url, init?.method || "GET");
  }) as typeof fetch;
  return calls;
}

test("the user list is read from the admin users endpoint", async () => {
  const calls = mockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
  await fetchAdminUsers({ q: "dem", limit: 50 });
  expect(calls[0].url).toBe("/api/v1/admin/users?q=dem&limit=50");
  expect(calls[0].method).toBe("GET");
  expect(calls[0].headers["pilipala-token"]).toBe("test-token");
});

test("the user list without a keyword asks for everything", async () => {
  const calls = mockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
  await fetchAdminUsers();
  expect(calls[0].url).toBe("/api/v1/admin/users");
});

test("a top up posts the amount, the idempotency key and the reason", async () => {
  const calls = mockFetch(() => json({ ok: true, data: { user_id: USER_ID, available: 220, frozen: 0 }, error_code: null, message: null }));
  await adminTopUpWallet(USER_ID, { amount: 100, idempotency_key: "key-1", reason: "补偿用户积分" });
  expect(calls[0].method).toBe("POST");
  expect(calls[0].url).toBe(`/api/v1/admin/wallets/${USER_ID}/topup`);
  expect(JSON.parse(calls[0].body)).toEqual({ amount: 100, idempotency_key: "key-1", reason: "补偿用户积分" });
});

test("the ledger of one user is listed by the wallet endpoint", async () => {
  const calls = mockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
  await fetchAdminWalletLedger(USER_ID, 50);
  expect(calls[0].url).toBe(`/api/v1/admin/wallets/${USER_ID}/ledger?limit=50`);
});

test("the job list is filtered by status and user", async () => {
  const calls = mockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
  await fetchAdminJobs({ status: "dead", user_id: USER_ID, limit: 20 });
  expect(calls[0].url).toBe(`/api/v1/admin/jobs?status=dead&user_id=${USER_ID}&limit=20`);
});

test("retrying a job posts to the retry endpoint", async () => {
  const calls = mockFetch(() => json({ ok: true, data: { job_id: JOB_ID, status: "queued" }, error_code: null, message: null }));
  await adminRetryJob(JOB_ID);
  expect(calls[0].method).toBe("POST");
  expect(calls[0].url).toBe(`/api/v1/admin/jobs/${JOB_ID}/retry`);
});

test("an i18n message is listed by keyword and upserted with key, locale and template", async () => {
  const calls = mockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
  await fetchAdminI18nMessages("WALLET");
  expect(calls[0].url).toBe("/api/v1/admin/i18n-messages?q=WALLET");
  await adminUpsertI18nMessage({ key: "WALLET_INSUFFICIENT", locale: "zh", template: "余额不足。" });
  expect(calls[1].method).toBe("PUT");
  expect(calls[1].url).toBe("/api/v1/admin/i18n-messages");
  expect(JSON.parse(calls[1].body)).toEqual({ key: "WALLET_INSUFFICIENT", locale: "zh", template: "余额不足。" });
});

test("the model display config is updated by the model path", async () => {
  const calls = mockFetch(() => json({ ok: true, data: {}, error_code: null, message: null }));
  await adminUpdateModel("gpt-image-2", { display_name: "GPT Image 2", capability: "image.generate", enabled: false, sort_order: 10 });
  expect(calls[0].method).toBe("PUT");
  expect(calls[0].url).toBe("/api/v1/admin/models/gpt-image-2");
  expect(JSON.parse(calls[0].body)).toEqual({ display_name: "GPT Image 2", capability: "image.generate", enabled: false, sort_order: 10 });
});

test("the remaining admin paths are built from one place", () => {
  expect(adminWalletTopupPath(USER_ID)).toBe(`/api/v1/admin/wallets/${USER_ID}/topup`);
  expect(adminWalletLedgerPath(USER_ID)).toBe(`/api/v1/admin/wallets/${USER_ID}/ledger`);
  expect(adminJobsPath({ limit: 200 })).toBe("/api/v1/admin/jobs?limit=200");
  expect(adminI18nMessagesPath("WALLET")).toBe("/api/v1/admin/i18n-messages?q=WALLET");
  expect(adminModelsPath()).toBe("/api/v1/admin/models");
  expect(adminCreditProductsPath()).toBe("/api/v1/admin/credit-products");
  expect(adminMembershipPlansPath()).toBe("/api/v1/admin/membership-plans");
  expect(adminMembershipPlanPricesPath()).toBe("/api/v1/admin/membership-plans/prices");
});
