import { afterAll, afterEach, expect, test } from "bun:test";

// The config / i18n / auth stores touch browser storage during module initialization.
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
values.set("pilipala:auth_token", "test-token");
afterAll(() => {
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "localStorage");
});

const { defaultConfig } = await import("../src/stores/use-config-store");
const { createVideoGenerationTask, pollVideoGenerationTask } = await import("../src/services/api/video");
const { resolvePilipalaVideoModel, submitPilipalaVideoJob, usesPilipalaManagedVideoChannel } = await import("../src/services/api/pilipala-jobs");
const { fromCloudAssetToStoreAsset } = await import("../src/services/api/pilipala-cloud");

const JOB_ID = "22222222-2222-2222-2222-222222222222";
const VIDEO_URL = "http://127.0.0.1:18080/pilipala-dev/anytoken/vid/abc/1.mp4?X-Tos-Signature=abc";

const remoteConfig = { ...defaultConfig, channelMode: "remote" as const, model: "seedance2", videoModel: "seedance2" };

type RecordedCall = { url: string; method: string; body: string };

function json(payload: unknown) {
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

/** 用全局 fetch 兜住托管视频链路：pilipalaRequest 没有 fetchImpl 入参。 */
function stubFetch(handler: (url: string, method: string) => Response | Promise<Response>) {
    const calls: RecordedCall[] = [];
    const original = globalThis.fetch;
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === "string" ? input : input.toString();
        calls.push({ url, method: init?.method || "GET", body: typeof init?.body === "string" ? init.body : "" });
        return await handler(url, init?.method || "GET");
    }) as typeof fetch;
    return {
        calls,
        restore: () => {
            globalThis.fetch = original;
        },
    };
}

const originalFetch = globalThis.fetch;

afterEach(() => {
    globalThis.fetch = originalFetch;
});

function jobView(status: string, errorCode: string | null = null, message: string | null = null) {
    return { ok: true, data: { job_id: JOB_ID, status, attempt: 1, max_attempts: 3, error_code: errorCode, message, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null };
}

test("the managed video channel is used in remote mode without a channel model", () => {
    expect(usesPilipalaManagedVideoChannel(remoteConfig)).toBe(true);
    expect(usesPilipalaManagedVideoChannel({ ...remoteConfig, channelMode: "local" })).toBe(false);
    expect(usesPilipalaManagedVideoChannel({ ...remoteConfig, model: "chan-1::sora-2", videoModel: "chan-1::sora-2" })).toBe(false);
});

test("the managed video model falls back to the seedance-2-720p-stable product SKU for unknown models", () => {
    // 只有 model_product 里真实存在的 SKU 才认；seedance2 / seedance2.5 / 未知模型一律回落到默认产品 SKU。
    expect(resolvePilipalaVideoModel(remoteConfig)).toBe("seedance-2-720p-stable");
    expect(resolvePilipalaVideoModel({ ...remoteConfig, model: "seedance2.5", videoModel: "seedance2.5" })).toBe("seedance-2-720p-stable");
    expect(resolvePilipalaVideoModel({ ...remoteConfig, model: "unknown-video", videoModel: "unknown-video" })).toBe("seedance-2-720p-stable");
});

test("a managed video job submits video.generate and returns a pilipala provider task", async () => {
    const stub = stubFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: true, data: { job_id: JOB_ID, status: "queued", hold_id: "hold-1" }, error_code: null, message: null });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const task = await createVideoGenerationTask(remoteConfig, "a slow pan over the sea");

    expect(task.provider).toBe("pilipala");
    expect(task.model).toBe("seedance-2-720p-stable");
    expect(task.id).toBe(JOB_ID);

    const submit = stub.calls.find((call) => call.url === "/api/v1/jobs/submit");
    expect(submit?.method).toBe("POST");
    const payload = JSON.parse(submit?.body || "{}");
    expect(payload.capability).toBe("video.generate");
    expect(payload.model).toBe("seedance-2-720p-stable");
    expect(payload.params.prompt).toBe("a slow pan over the sea");
    expect(payload.params.duration).toBeGreaterThan(0);
    expect(payload.params.resolution).toBeTruthy();
    // 严禁把浏览器里的密钥或 Base64 塞进 params。
    expect(payload.params.apiKey).toBeUndefined();
    expect(payload.params.api_key).toBeUndefined();
    expect(payload.params.baseUrl).toBeUndefined();
    stub.restore();
});

test("submitting a managed video job directly keeps the same capability", async () => {
    const stub = stubFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: true, data: { job_id: JOB_ID, status: "queued", hold_id: "hold-1" }, error_code: null, message: null });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const submitted = await submitPilipalaVideoJob(remoteConfig, "hello");
    expect(submitted.jobId).toBe(JOB_ID);
    expect(submitted.model).toBe("seedance-2-720p-stable");
    const payload = JSON.parse(stub.calls[0].body || "{}");
    expect(payload.capability).toBe("video.generate");
    stub.restore();
});

test("polling a managed video task reports queued, running and the finished video", async () => {
    const stub = stubFetch((url) => {
        if (url === `/api/v1/jobs/${JOB_ID}`) return json(jobView("queued"));
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const queued = await pollVideoGenerationTask(remoteConfig, { id: JOB_ID, provider: "pilipala", model: "seedance2" });
    expect(queued.status).toBe("queued");
    expect(queued.status === "queued" ? queued.progress : 0).toBe(15);
    stub.restore();
});

test("polling a running managed video task reports processing", async () => {
    const stub = stubFetch((url) => {
        if (url === `/api/v1/jobs/${JOB_ID}`) return json(jobView("running"));
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const state = await pollVideoGenerationTask(remoteConfig, { id: JOB_ID, provider: "pilipala", model: "seedance2" });
    expect(state.status).toBe("processing");
    expect(state.status === "processing" ? state.progress : 0).toBe(50);
    stub.restore();
});

test("a succeeded managed video task returns the signed video url", async () => {
    const stub = stubFetch((url) => {
        if (url === `/api/v1/jobs/${JOB_ID}`) return json(jobView("succeeded"));
        if (url === `/api/v1/jobs/${JOB_ID}/result`) {
            return json({ ok: true, data: { job_id: JOB_ID, status: "succeeded", outputs: [{ type: "video", object_key: "anytoken/vid/abc/1.mp4", url: VIDEO_URL }], error_code: null, message: null }, error_code: null, message: null });
        }
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const state = await pollVideoGenerationTask(remoteConfig, { id: JOB_ID, provider: "pilipala", model: "seedance2" });
    expect(state.status).toBe("completed");
    expect(state.status === "completed" ? state.result.url : "").toBe(VIDEO_URL);
    expect(state.status === "completed" ? state.result.mimeType : "").toBe("video/mp4");
    stub.restore();
});

test("a dead managed video task surfaces the friendly backend message", async () => {
    const stub = stubFetch((url) => {
        if (url === `/api/v1/jobs/${JOB_ID}`) return json(jobView("dead", "UPSTREAM_VIDEO_FAILED", "视频生成失败，请稍后重试。"));
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const state = await pollVideoGenerationTask(remoteConfig, { id: JOB_ID, provider: "pilipala", model: "seedance2" });
    expect(state.status).toBe("failed");
    expect(state.status === "failed" ? state.error : "").toBe("视频生成失败，请稍后重试。");
    stub.restore();
});

test("a cloud mp4 asset becomes a video asset, not an image", () => {
    const asset = fromCloudAssetToStoreAsset({ id: "asset-1", project_id: null, job_id: "job-1", source: "generated", object_key: "anytoken/vid/abc/1.mp4", signed_url: VIDEO_URL, mime_type: "video/mp4", created_at: "2026-10-24T00:00:00Z" });
    expect(asset.kind).toBe("video");
    expect(asset.id).toBe("cloud:asset-1");
    expect(asset.kind === "video" ? asset.data.url : "").toBe(VIDEO_URL);
    expect(asset.kind === "video" ? asset.data.mimeType : "").toBe("video/mp4");
});

test("a cloud asset without a video mime type stays an image", () => {
    const asset = fromCloudAssetToStoreAsset({ id: "asset-2", project_id: null, job_id: "job-1", source: "generated", object_key: "anytoken/img/abc/1.png", signed_url: "https://flora-media-test.tos-cn-guangzhou.volces.com/flora/img/1.png", mime_type: "image/png", created_at: "2026-10-24T00:00:00Z" });
    expect(asset.kind).toBe("image");
    expect(asset.kind === "image" ? asset.data.dataUrl : "").toContain("flora/img/1.png");
});
