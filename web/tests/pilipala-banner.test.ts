import { afterAll, expect, test } from "bun:test";

// The i18n / client helpers touch browser storage during module initialization.
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
afterAll(() => {
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "localStorage");
});

const { fetchCurrentBanner, parseBannerEndMs } = await import("../src/services/api/pilipala-banner");

function json(payload: unknown) {
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

const banner = {
    id: "b1",
    placement: "home_promo",
    title: "预付费加赠限时开启",
    subtitle: "充值加赠最高 20%",
    image_object_key: null,
    link_url: "https://pilipala.art/billing/recharge",
    starts_at: null,
    ends_at: "2026-11-15T15:59:59.000Z",
    sort_order: 0,
    enabled: true,
    campaign_id: null,
    created_at: "2026-11-01T00:00:00Z",
    updated_at: "2026-11-01T00:00:00Z",
};

test("the current banner is read from the public banners endpoint", async () => {
    const calls: string[] = [];
    const impl = (async (input: RequestInfo | URL) => {
        calls.push(typeof input === "string" ? input : input.toString());
        return json({ ok: true, data: banner, error_code: null, message: null });
    }) as typeof fetch;

    const current = await fetchCurrentBanner("home_promo", impl);
    expect(calls[0]).toBe("/api/v1/banners/current?placement=home_promo");
    expect(current?.title).toBe("预付费加赠限时开启");
    expect(current?.link_url).toBe("https://pilipala.art/billing/recharge");
});

test("a placement without a valid banner comes back empty", async () => {
    const impl = (async () => json({ ok: true, data: null, error_code: null, message: null })) as typeof fetch;
    expect(await fetchCurrentBanner("membership", impl)).toBeNull();
});

test("the banner end time is only read when it is a valid instant", () => {
    expect(parseBannerEndMs("2026-11-15T15:59:59.000Z")).toBe(Date.parse("2026-11-15T15:59:59.000Z"));
    expect(parseBannerEndMs(null)).toBeNull();
    expect(parseBannerEndMs("  ")).toBeNull();
    expect(parseBannerEndMs("not-a-date")).toBeNull();
});
