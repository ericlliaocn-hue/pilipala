// Executes the production project.tsx generateAngleNode callback with in-memory state and
// controlled request/upload stubs. This covers request/upload counts, node ownership, cancellation
// and late-result handling, not React rendering or real browser behavior.
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { expect, test } from "bun:test";

import { CanvasNodeType, type CanvasConnection, type CanvasNodeData, type CanvasNodeMetadata } from "../src/types/canvas";

const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
Object.defineProperty(globalThis, "localStorage", { configurable: true, value: { getItem: () => null, setItem: () => {}, removeItem: () => {} } });
const { buildAngleLabel, buildAnglePrompt, buildGenerationConfig, isGenerationCanceled, sourceNodeReferenceImages } = await import("../src/lib/canvas/canvas-generation-helpers");
const { buildImageGenerationMetadata, imageMetadata } = await import("../src/lib/canvas/canvas-node-factory");
const { fitNodeSize } = await import("../src/lib/canvas/canvas-node-size");
const { NODE_DEFAULT_SIZE } = await import("../src/constant/canvas");
const { defaultConfig } = await import("../src/stores/use-config-store");
if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
else Reflect.deleteProperty(globalThis, "localStorage");

type AngleParams = { mode: "single" | "threeView"; horizontalAngle: number; pitchAngle: number; cameraDistance: number; wideAngle: boolean };

type Deferred<T> = { promise: Promise<T>; resolve: (value: T) => void; reject: (error: unknown) => void };

function deferred<T>(): Deferred<T> {
    let resolve!: (value: T) => void;
    let reject!: (error: unknown) => void;
    const promise = new Promise<T>((accept, decline) => {
        resolve = accept;
        reject = decline;
    });
    return { promise, resolve, reject };
}

function node(id: string, type: CanvasNodeData["type"], metadata?: CanvasNodeMetadata): CanvasNodeData {
    return { id, type, title: id, position: { x: 0, y: 0 }, width: 200, height: 200, metadata };
}

function sourceNode(metadata: CanvasNodeMetadata = {}): CanvasNodeData {
    return node("character", CanvasNodeType.Image, {
        content: "data:image/png;base64,SOURCE",
        storageKey: "image:character",
        status: "success",
        naturalWidth: 300,
        naturalHeight: 500,
        bytes: 12,
        mimeType: "image/png",
        ...metadata,
    });
}

const uploaded = { url: "image:result", storageKey: "image:result", width: 600, height: 1000, bytes: 30, mimeType: "image/png" };
const generated = [{ dataUrl: "data:image/png;base64,RESULT" }];

const source = readFileSync(new URL("../src/pages/canvas/project.tsx", import.meta.url), "utf8");
const transpiler = new Bun.Transpiler({ loader: "tsx", tsconfig: { compilerOptions: { jsx: "react" } } });
function callback(name: string, next: string, scope: Record<string, unknown>) {
    const from = source.indexOf(`    const ${name} = useCallback`);
    const to = source.indexOf(`    const ${next} = useCallback`, from);
    assert(from >= 0 && to > from);
    const bindings = Object.entries(scope).filter(([key]) => key !== name);
    return new Function(...bindings.map(([key]) => key), transpiler.transformSync(source.slice(from, to)) + `\nreturn ${name};`)(...bindings.map(([, value]) => value));
}

type RequestCall = { config: { count: string; [key: string]: unknown }; prompt: string; references: Array<{ id: string; dataUrl: string; storageKey?: string }>; signal: AbortSignal };
type UploadCall = { dataUrl: string; signal: AbortSignal };

function harness(
    nodes: CanvasNodeData[],
    options: {
        ready?: boolean;
        requestEdit?: (config: RequestCall["config"], prompt: string, references: RequestCall["references"], init: { signal: AbortSignal }) => Promise<typeof generated>;
        uploadImage?: (dataUrl: string, init: { signal: AbortSignal }) => Promise<typeof uploaded>;
    } = {},
) {
    const nodesRef = { current: structuredClone(nodes) };
    const connectionsRef = { current: [] as CanvasConnection[] };
    const requests = new Map<string, AbortController>();
    const edits: RequestCall[] = [];
    const uploads: UploadCall[] = [];
    let openConfig = 0;
    let running: string | null = null;
    let selected: Set<string> | null = null;
    let dialog: string | null = null;
    let angle: string | null = "character";
    let idCounter = 0;

    const scope: Record<string, unknown> = {
        buildAngleLabel,
        buildAnglePrompt,
        buildGenerationConfig,
        buildImageGenerationMetadata,
        imageMetadata,
        fitNodeSize,
        isGenerationCanceled,
        NODE_DEFAULT_SIZE,
        CanvasNodeType,
        NODE_STATUS_IDLE: "idle",
        NODE_STATUS_LOADING: "loading",
        NODE_STATUS_ERROR: "error",
        effectiveConfig: { ...defaultConfig },
        isAiConfigReady: () => options.ready !== false,
        openConfigDialog: () => {
            openConfig += 1;
        },
        nanoid: () => `generated-${++idCounter}`,
        requestEdit: (config: RequestCall["config"], prompt: string, references: RequestCall["references"], init: { signal: AbortSignal }) => {
            edits.push({ config, prompt, references, signal: init.signal });
            return options.requestEdit ? options.requestEdit(config, prompt, references, init) : Promise.resolve(structuredClone(generated));
        },
        uploadImage: (dataUrl: string, init: { signal: AbortSignal }) => {
            uploads.push({ dataUrl, signal: init.signal });
            return options.uploadImage ? options.uploadImage(dataUrl, init) : Promise.resolve({ ...uploaded });
        },
        t: (key: string) => key,
        useCallback: (fn: unknown) => fn,
        setNodes: (next: unknown) => {
            nodesRef.current = typeof next === "function" ? (next as (value: CanvasNodeData[]) => CanvasNodeData[])(nodesRef.current) : (next as CanvasNodeData[]);
        },
        setConnections: (next: unknown) => {
            connectionsRef.current = typeof next === "function" ? (next as (value: CanvasConnection[]) => CanvasConnection[])(connectionsRef.current) : (next as CanvasConnection[]);
        },
        setAngleNodeId: (next: unknown) => {
            angle = typeof next === "function" ? (next as (value: string | null) => string | null)(angle) : (next as string | null);
        },
        setRunningNodeId: (next: unknown) => {
            running = typeof next === "function" ? (next as (value: string | null) => string | null)(running) : (next as string | null);
        },
        setSelectedNodeIds: (next: Set<string>) => {
            selected = next;
        },
        setDialogNodeId: (next: unknown) => {
            dialog = typeof next === "function" ? (next as (value: string | null) => string | null)(dialog) : (next as string | null);
        },
        startGenerationRequest: (targetNodeId: string, _originNodeId: string, _runningId?: string, controller = new AbortController()) => {
            requests.get(targetNodeId)?.abort();
            requests.set(targetNodeId, controller);
            return controller;
        },
        finishGenerationRequest: (targetNodeId: string, controller: AbortController) => {
            if (requests.get(targetNodeId) === controller) requests.delete(targetNodeId);
        },
    };

    return {
        nodesRef,
        connectionsRef,
        requests,
        edits,
        uploads,
        get openConfig() {
            return openConfig;
        },
        get running() {
            return running;
        },
        get selected() {
            return selected;
        },
        get dialog() {
            return dialog;
        },
        get angleNodeId() {
            return angle;
        },
        run: callback("generateAngleNode", "handleFontSizeChange", scope) as (node: CanvasNodeData, params: AngleParams) => Promise<void>,
    };
}

const flush = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

const threeViewParams: AngleParams = { mode: "threeView", horizontalAngle: 42, pitchAngle: -15, cameraDistance: 7.4, wideAngle: true };
const singleParams: AngleParams = { mode: "single", horizontalAngle: 20, pitchAngle: -10, cameraDistance: 5.6, wideAngle: true };

function resultNodes(h: ReturnType<typeof harness>, originalCount: number) {
    return h.nodesRef.current.slice(originalCount);
}

test("three-view confirmation sends exactly one count=1 edit, uploads once and appends one ordinary image connected to the source", async () => {
    const source = sourceNode({ groupId: "character-group" });
    const group = node("character-group", CanvasNodeType.Group, {});
    const sibling = node("sibling", CanvasNodeType.Text, { content: "Alice", groupId: "character-group" });
    const before = structuredClone([source, group, sibling]);
    const request = deferred<typeof generated>();
    const upload = deferred<typeof uploaded>();
    const h = harness([source, group, sibling], { requestEdit: () => request.promise, uploadImage: () => upload.promise });

    const running = h.run(h.nodesRef.current[0], threeViewParams);
    await flush();

    expect(h.edits).toHaveLength(1);
    expect(h.edits[0].config.count).toBe("1");
    expect(h.edits[0].prompt).toBe(buildAnglePrompt(threeViewParams));
    expect(h.edits[0].prompt).toContain("正面");
    expect(h.edits[0].prompt).toContain("侧面");
    expect(h.edits[0].prompt).toContain("背面");
    expect(h.edits[0].prompt).not.toContain("AI 多角度");
    expect(h.edits[0].references).toHaveLength(1);
    expect(h.edits[0].references[0]).toMatchObject({ id: "character", dataUrl: "data:image/png;base64,SOURCE", storageKey: "image:character" });

    const [child] = resultNodes(h, 3);
    expect(resultNodes(h, 3)).toHaveLength(1);
    expect(child.type).toBe(CanvasNodeType.Image);
    expect(child.title).toBe("角色三视图");
    expect(child.metadata?.status).toBe("loading");
    expect(child.metadata?.groupId).toBeUndefined();
    expect(h.uploads).toHaveLength(0);
    expect(h.running).toBe(child.id);

    request.resolve(structuredClone(generated));
    await flush();
    expect(h.uploads).toHaveLength(1);
    expect(h.uploads[0].dataUrl).toBe("data:image/png;base64,RESULT");

    upload.resolve(structuredClone(uploaded));
    await running;

    expect(h.edits).toHaveLength(1);
    expect(h.uploads).toHaveLength(1);
    expect(resultNodes(h, 3)).toHaveLength(1);
    const result = h.nodesRef.current[3];
    expect(result.id).toBe(child.id);
    expect(result.metadata?.status).toBe("success");
    expect(result.metadata?.content).toBe("image:result");
    expect(result.metadata?.groupId).toBeUndefined();
    expect(result.metadata?.count).toBe(1);
    expect(sourceNodeReferenceImages(result)).toMatchObject([{ id: child.id, dataUrl: "image:result", storageKey: "image:result" }]);
    expect(h.nodesRef.current.slice(0, 3)).toEqual(before);
    expect(h.connectionsRef.current).toEqual([{ id: "generated-2", fromNodeId: "character", toNodeId: child.id }]);
    expect(h.selected).toEqual(new Set([child.id]));
    expect(h.dialog).toBe(child.id);
    expect(h.angleNodeId).toBeNull();
    expect(h.running).toBeNull();
});

test("single-view mode keeps the original prompt and single-image result", async () => {
    const source = sourceNode();
    const h = harness([source]);

    await h.run(h.nodesRef.current[0], singleParams);

    expect(h.edits).toHaveLength(1);
    expect(h.edits[0].config.count).toBe("1");
    expect(h.edits[0].prompt).toBe(buildAnglePrompt(singleParams));
    expect(h.edits[0].prompt).toContain("AI 多角度");
    expect(h.edits[0].prompt).not.toContain("三视图");
    expect(h.uploads).toHaveLength(1);
    expect(resultNodes(h, 1)).toHaveLength(1);
    const result = h.nodesRef.current[1];
    expect(result.title).toBe(buildAngleLabel(singleParams));
    expect(result.metadata?.status).toBe("success");
    expect(result.metadata?.content).toBe("image:result");
    expect(result.metadata?.groupId).toBeUndefined();
    expect(h.running).toBeNull();
});

test("missing image sends no request, upload or node", async () => {
    const empty = sourceNode({ content: "", storageKey: undefined });
    const h = harness([empty]);
    const before = structuredClone(h.nodesRef.current);

    await h.run(h.nodesRef.current[0], threeViewParams);

    expect(h.edits).toHaveLength(0);
    expect(h.uploads).toHaveLength(0);
    expect(h.openConfig).toBe(0);
    expect(h.nodesRef.current).toEqual(before);
    expect(h.connectionsRef.current).toHaveLength(0);
    expect(h.running).toBeNull();
});

test("unready AI config opens the config dialog without requesting or creating a node", async () => {
    const h = harness([sourceNode()], { ready: false });
    const before = structuredClone(h.nodesRef.current);

    await h.run(h.nodesRef.current[0], threeViewParams);

    expect(h.openConfig).toBe(1);
    expect(h.edits).toHaveLength(0);
    expect(h.uploads).toHaveLength(0);
    expect(h.nodesRef.current).toEqual(before);
    expect(h.connectionsRef.current).toHaveLength(0);
});

test("request failure marks the single result node as error and never uploads", async () => {
    const h = harness([sourceNode()], { requestEdit: () => Promise.reject(new Error("upstream failed")) });

    await h.run(h.nodesRef.current[0], threeViewParams);

    expect(h.edits).toHaveLength(1);
    expect(h.uploads).toHaveLength(0);
    expect(resultNodes(h, 1)).toHaveLength(1);
    const result = h.nodesRef.current[1];
    expect(result.metadata?.status).toBe("error");
    expect(result.metadata?.errorDetails).toBe("upstream failed");
    expect(h.running).toBeNull();
});

test("aborting during upload leaves no success and no permanent loading", async () => {
    const upload = deferred<typeof uploaded>();
    const h = harness([sourceNode()], { uploadImage: () => upload.promise });

    const running = h.run(h.nodesRef.current[0], threeViewParams);
    await flush();

    expect(h.uploads).toHaveLength(1);
    const child = h.nodesRef.current[1];
    expect(child.metadata?.status).toBe("loading");
    h.requests.get(child.id)!.abort();

    upload.resolve(structuredClone(uploaded));
    await running;

    expect(h.uploads).toHaveLength(1);
    expect(resultNodes(h, 1)).toHaveLength(1);
    const result = h.nodesRef.current[1];
    expect(result.id).toBe(child.id);
    expect(result.metadata?.status).not.toBe("loading");
    expect(result.metadata?.status).not.toBe("success");
    expect(result.metadata?.content).toBeUndefined();
    expect(h.running).toBeNull();
});

test("deleting the result node while the request is pending keeps the late result out and clears running", async () => {
    const request = deferred<typeof generated>();
    const upload = deferred<typeof uploaded>();
    const h = harness([sourceNode()], { requestEdit: () => request.promise, uploadImage: () => upload.promise });

    const running = h.run(h.nodesRef.current[0], threeViewParams);
    await flush();

    const child = h.nodesRef.current[1];
    h.requests.get(child.id)!.abort();
    h.nodesRef.current = h.nodesRef.current.filter((item) => item.id !== child.id);
    h.connectionsRef.current = [];

    request.resolve(structuredClone(generated));
    upload.resolve(structuredClone(uploaded));
    await running;

    expect(h.uploads).toHaveLength(0);
    expect(h.nodesRef.current.map((item) => item.id)).toEqual(["character"]);
    expect(h.connectionsRef.current).toHaveLength(0);
    expect(h.running).toBeNull();
});

test("deleting the source does not cancel the surviving result node or rewrite the character group", async () => {
    const source = sourceNode({ groupId: "character-group" });
    const group = node("character-group", CanvasNodeType.Group, {});
    const sibling = node("sibling", CanvasNodeType.Text, { content: "Alice", groupId: "character-group" });
    const request = deferred<typeof generated>();
    const h = harness([source, group, sibling], { requestEdit: () => request.promise });

    const running = h.run(h.nodesRef.current[0], threeViewParams);
    await flush();

    const child = h.nodesRef.current[3];
    h.nodesRef.current = h.nodesRef.current.filter((item) => item.id !== "character");
    h.connectionsRef.current = h.connectionsRef.current.filter((item) => item.fromNodeId !== "character");

    request.resolve(structuredClone(generated));
    await running;

    expect(h.uploads).toHaveLength(1);
    const result = h.nodesRef.current.find((item) => item.id === child.id)!;
    expect(result.metadata?.status).toBe("success");
    expect(result.metadata?.groupId).toBeUndefined();
    expect(h.nodesRef.current.find((item) => item.id === "character-group")).toEqual(group);
    expect(h.nodesRef.current.find((item) => item.id === "sibling")).toEqual(sibling);
    expect(h.running).toBeNull();
});

test("consecutive confirmations settle independently without any permanent loading", async () => {
    const first = deferred<typeof generated>();
    const second = deferred<typeof generated>();
    const queue = [first, second];
    const h = harness([sourceNode()], { requestEdit: () => queue.shift()!.promise });

    const runA = h.run(h.nodesRef.current[0], threeViewParams);
    await flush();
    const runB = h.run(h.nodesRef.current[0], { ...threeViewParams, horizontalAngle: -30 });
    await flush();

    expect(h.edits).toHaveLength(2);
    expect(h.uploads).toHaveLength(0);
    expect(resultNodes(h, 1)).toHaveLength(2);

    second.resolve(structuredClone(generated));
    await flush();
    first.resolve(structuredClone(generated));
    await Promise.all([runA, runB]);

    expect(h.edits).toHaveLength(2);
    expect(h.edits.every((call) => call.config.count === "1")).toBe(true);
    expect(h.uploads).toHaveLength(2);
    const results = resultNodes(h, 1);
    expect(results).toHaveLength(2);
    expect(results.every((item) => item.type === CanvasNodeType.Image)).toBe(true);
    expect(results.every((item) => item.metadata?.status === "success")).toBe(true);
    expect(results.every((item) => item.metadata?.groupId === undefined)).toBe(true);
    expect(h.nodesRef.current.every((item) => item.metadata?.status !== "loading")).toBe(true);
    expect(h.running).toBeNull();
});
