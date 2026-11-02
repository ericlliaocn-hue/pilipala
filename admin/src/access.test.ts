import { describe, expect, test } from "bun:test";
import { bannerRequestBody, canEnterAdmin, createIdempotencyKey, topupRequestBody, visibleMenu } from "./access";

describe("admin access boundaries", () => {
  test("hides menu entries without their permission", () => {
    expect(visibleMenu(["stats.read"]).map((item) => item.label)).toEqual(["概览", "异常"]);
    expect(visibleMenu([])).toEqual([]);
  });

  test("empty or missing platform permissions cannot enter", () => {
    expect(canEnterAdmin([])).toBe(false);
    expect(canEnterAdmin(undefined)).toBe(false);
    expect(canEnterAdmin(["user.read"])).toBe(true);
  });

  test("creates a fresh idempotency key in the accepted length", () => {
    const first = createIdempotencyKey();
    const second = createIdempotencyKey();
    expect(first).not.toBe(second);
    expect(first.length).toBeGreaterThanOrEqual(1);
    expect(first.length).toBeLessThanOrEqual(64);
  });

  test("top-up body carries a fresh idempotency key and the operator reason", () => {
    const body = topupRequestBody(250, "补偿用户积分");
    expect(body).toEqual({ amount: 250, idempotency_key: expect.any(String), reason: "补偿用户积分" });
    expect(body.idempotency_key.length).toBeGreaterThanOrEqual(1);
    expect(body.idempotency_key.length).toBeLessThanOrEqual(64);
  });

  test("banner save always sends enabled so an edit does not clear it", () => {
    expect(bannerRequestBody({ placement: "home_promo", title: "活动", enabled: "true", sort_order: 2 }).enabled).toBe(true);
    expect(bannerRequestBody({ placement: "membership", title: "会员", enabled: "false" })).toMatchObject({
      enabled: false,
      sort_order: 0,
      subtitle: null,
      campaign_id: null,
    });
  });
});
