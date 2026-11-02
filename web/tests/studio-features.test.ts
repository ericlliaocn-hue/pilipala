import { expect, test } from "bun:test";

const {
    STUDIO_FEATURE_DEFAULTS,
    fallbackStudioFeatures,
    fetchStudioFeatures,
    parseStudioFeatures,
    shouldShowMembershipBadge,
} = await import("../src/services/api/studio-features");

test("parseStudioFeatures fills brief defaults and keeps snake_case fields", () => {
    const parsed = parseStudioFeatures({
        membership_enabled: true,
        membership_badge: "限时 4.6 折",
        enterprise_enabled: false,
    });
    expect(parsed).toEqual(STUDIO_FEATURE_DEFAULTS);
});

test("parseStudioFeatures defaults enterprise procurement to off", () => {
    expect(parseStudioFeatures({}).enterprise_enabled).toBe(false);
    expect(parseStudioFeatures({ enterprise_enabled: true }).enterprise_enabled).toBe(true);
});

test("parseStudioFeatures uses defaults for missing keys and keeps an empty membership badge", () => {
    expect(parseStudioFeatures({})).toEqual(STUDIO_FEATURE_DEFAULTS);
    expect(parseStudioFeatures({ membership_badge: "" }).membership_badge).toBe("");
});

test("empty membership badge hides the corner mark", () => {
    expect(shouldShowMembershipBadge("限时 4.6 折")).toBe(true);
    expect(shouldShowMembershipBadge("")).toBe(false);
    expect(shouldShowMembershipBadge("   ")).toBe(false);
    expect(shouldShowMembershipBadge(null)).toBe(false);
});

test("fallbackStudioFeatures keeps the membership badge copy", () => {
    expect(fallbackStudioFeatures().membership_enabled).toBe(true);
    expect(fallbackStudioFeatures({ membership_badge: "限时 4.6 折" }).membership_badge).toBe("限时 4.6 折");
});

test("fetchStudioFeatures reads GET /api/v1/studio/features", async () => {
    const calls: string[] = [];
    const impl = (async (input: RequestInfo | URL) => {
        calls.push(typeof input === "string" ? input : input.toString());
        return new Response(
            JSON.stringify({
                ok: true,
                data: {
                    membership_enabled: true,
                    membership_badge: "",
                    enterprise_enabled: false,
                },
                error_code: null,
                message: null,
            }),
            { status: 200, headers: { "Content-Type": "application/json" } },
        );
    }) as typeof fetch;

    const features = await fetchStudioFeatures(impl);
    expect(calls[0]).toBe("/api/v1/studio/features");
    expect(features.membership_enabled).toBe(true);
    expect(features.membership_badge).toBe("");
});
