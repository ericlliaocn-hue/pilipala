// Executes production callbacks with in-memory state and controlled services.
// This covers callback wiring, not React rendering, DOM, real image decoding or billing.
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { expect, test } from "bun:test";
import * as storyboard from "../src/lib/canvas/canvas-storyboard";
import { CanvasNodeType } from "../src/types/canvas";

const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
Object.defineProperty(globalThis, "localStorage", { configurable: true, value: { getItem: () => null, setItem: () => {}, removeItem: () => {} } });
const { collectGroupRerunNodes, snapshotGroupWorkflow } = await import("../src/lib/canvas/canvas-group-workflow");
const { resetInterruptedGeneration } = await import("../src/lib/canvas/canvas-generation-helpers");
if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
else Reflect.deleteProperty(globalThis, "localStorage");

const source = readFileSync(new URL("../src/pages/canvas/project.tsx", import.meta.url), "utf8");
const transpiler = new Bun.Transpiler({ loader: "tsx", tsconfig: { compilerOptions: { jsx: "react" } } });
function callback(name: string, next: string, scope: Record<string, unknown>) {
    const from = source.indexOf(`    const ${name} = useCallback`);
    const to = source.indexOf(`    const ${next} = useCallback`, from);
    assert(from >= 0 && to > from);
    const bindings = Object.entries(scope).filter(([key]) => key !== name);
    return new Function(...bindings.map(([key]) => key), transpiler.transformSync(source.slice(from, to)) + `\nreturn ${name};`)(...bindings.map(([, value]) => value));
}
function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}
const node = (id: string, type: string, metadata: any = {}) => ({ id, type, title: id, position: { x: 0, y: 0 }, width: 200, height: 200, metadata });
const uploaded = { url: "blob:new", storageKey: "image:new", width: 300, height: 300, bytes: 1, mimeType: "image/png" };
function harness(nodes: any[]) {
    const nodesRef = { current: structuredClone(nodes) };
    const connectionsRef = { current: [] as any[] };
    const generationRequestsRef = { current: new Map<string, any>() };
    const notices: string[] = [];
    let serial = 0;
    let runningId: string | null = null;
    const requests: any[] = [];
    const confirmations: any[] = [];
    let authOpened = false;
    const noop = () => {};
    const scope: any = {
        ...storyboard,
        collectGroupRerunNodes,
        snapshotGroupWorkflow,
        CanvasNodeType,
        nodesRef,
        connectionsRef,
        generationRequestsRef,
        useCallback: (fn: unknown) => fn,
        nanoid: () => `id-${++serial}`,
        setNodes: (fn: any) => {
            nodesRef.current = typeof fn === "function" ? fn(nodesRef.current) : fn;
        },
        setConnections: (fn: any) => {
            connectionsRef.current = typeof fn === "function" ? fn(connectionsRef.current) : fn;
        },
        setRunningNodeId: (value: any) => {
            runningId = typeof value === "function" ? value(runningId) : value;
        },
        setSelectedNodeIds: noop,
        setSelectedConnectionId: noop,
        setDialogNodeId: noop,
        setSplitNodeId: noop,
        setStoryboardNodeId: noop,
        setAuthModalOpen: () => {
            authOpened = true;
        },
        openConfigDialog: noop,
        t: (key: string, params?: any) => (params ? key + JSON.stringify(params) : key),
        React: { createElement: (_type: any, _props: any, ...children: any[]) => children },
        modal: { confirm: (config: any) => confirmations.push(config) },
        hasResumableVideoTask: () => false,
        pollVideoNodeTask: noop,
        completeVideoNodeTask: noop,
        isVideoTaskFailed: () => false,
        findRetrySourceNode: () => null,
        resolveMetadataReferences: async (metadata: any) => metadata.references || [],
        sourceNodeReferenceImages: () => [],
        clipboardRef: { current: null },
        selectedNodeIdsRef: { current: new Set<string>() },
        getCanvasCenter: () => ({ x: 0, y: 0 }),
        setContextMenu: noop,
        setHoveredNodeId: noop,
        setToolbarNodeId: noop,
        setInfoNodeId: noop,
        setCropNodeId: noop,
        setMaskEditNodeId: noop,
        setAngleNodeId: noop,
        setPreviewNodeId: noop,
        setReferencePickerNodeId: noop,
        setExpandedBatchNodeIds: noop,
        cleanupCanvasFiles: noop,
        projectId: "test",
        chatSessions: [],
        addTemplate: noop,
        message: { success: (s: string) => notices.push(s), warning: (s: string) => notices.push(s), error: (s: string) => notices.push(s) },
        NODE_STATUS_LOADING: "loading",
        NODE_STATUS_IDLE: "idle",
        NODE_STATUS_SUCCESS: "success",
        NODE_STATUS_ERROR: "error",
        NODE_DEFAULT_SIZE: { [CanvasNodeType.Image]: { width: 200, height: 200 } },
        effectiveConfig: { channelMode: "local", model: "test" },
        token: "test",
        buildGenerationConfig: (config: any) => config,
        isAiConfigReady: () => true,
        createCanvasNode: (type: string, position: any, metadata: any) => ({ ...node("generated-grid", type, metadata), position }),
        buildNodeGenerationContext: (_: string, __: any, ___: any, prompt: string) => ({ prompt, referenceImages: [] }),
        buildImageGenerationMetadata: (generationType: string, config: any, count: number, references: any[]) => ({ generationType, model: config.model, size: config.size, count, references }),
        hydrateNodeGenerationContext: async (context: any) => context,
        requestGeneration: async (...args: any[]) => {
            requests.push(["generation", ...args]);
            return [{ dataUrl: "data:fake" }];
        },
        requestEdit: async (...args: any[]) => {
            requests.push(["edit", ...args]);
            return [{ dataUrl: "data:fake" }];
        },
        uploadImage: async (value: string) => ({ ...uploaded, url: "blob:" + value, storageKey: "image:" + value }),
        splitDataUrl: async (_value: string, params: any) => Array.from({ length: params.rows * params.columns }, (_, i) => ({ row: Math.floor(i / params.columns), column: i % params.columns, dataUrl: "cell-" + i })),
        imageMetadata: (image: any) => ({ content: image.url, storageKey: image.storageKey, status: "success", naturalWidth: image.width, naturalHeight: image.height }),
        fitNodeSize: (width: number, height: number) => ({ width, height }),
        isGenerationCanceled: (error: any) => error?.name === "AbortError",
    };
    scope.startGenerationRequest = callback("startGenerationRequest", "finishGenerationRequest", scope);
    scope.finishGenerationRequest = callback("finishGenerationRequest", "completeVideoNodeTask", scope);
    scope.stopGenerationByRunningId = callback("stopGenerationByRunningId", "confirmStopGeneration", scope);
    scope.splitImageNode = callback("splitImageNode", "maskEditImageNode", scope);
    scope.handleRetryNode = callback("handleRetryNode", "rerunGroup", scope);
    return {
        nodesRef,
        connectionsRef,
        generationRequestsRef,
        notices,
        scope,
        requests,
        confirmations,
        get runningId() {
            return runningId;
        },
        get authOpened() {
            return authOpened;
        },
    };
}

const scriptNode = () => node("script", CanvasNodeType.Text, { content: "剧情" });
const gridNode = (size = 3) => node("grid", CanvasNodeType.Image, { content: "old-grid", storageKey: "old-key", status: "error", storyboardGridSize: size, generationType: "generation", prompt: "saved prompt", model: "saved model", size: "1:1" });

for (const size of [3, 5] as const) {
    test(`actual generation and retry create ${size ** 2} shots with one request each`, async () => {
        const h = harness([scriptNode()]);
        await callback("generateStoryboardGrid", "insertAssistantImage", h.scope)(h.nodesRef.current[0], size);
        const grid = h.nodesRef.current.find((n) => n.id === "generated-grid")!;
        expect(h.requests).toHaveLength(1);
        expect(h.requests[0][1]).toMatchObject({ size: "1:1", count: "1" });
        expect(h.nodesRef.current.filter((n) => n.metadata?.storyboardParentId === grid.id)).toHaveLength(size ** 2);
        expect(h.connectionsRef.current.filter((c) => c.fromNodeId === grid.id)).toHaveLength(size ** 2);
        const oldIds = h.nodesRef.current.map((n) => n.id);
        expect(await h.scope.handleRetryNode(grid)).toBe(true);
        expect(h.requests).toHaveLength(2);
        expect(h.nodesRef.current.map((n) => n.id)).toEqual(oldIds);
    });

    test(`actual retry uses saved ${size}x${size} request after interrupted-node reset`, async () => {
        const grid = gridNode(size);
        grid.metadata = { ...grid.metadata, status: "loading", generationType: "edit", references: [{ id: "ref", storageKey: "reference-key" }] };
        const nodes = resetInterruptedGeneration(JSON.parse(JSON.stringify([grid, node("ordinary", CanvasNodeType.Text)])));
        const h = harness(nodes);
        h.connectionsRef.current = [{ id: "ordinary-edge", fromNodeId: grid.id, toNodeId: "ordinary" }];
        expect(await h.scope.handleRetryNode(h.nodesRef.current[0])).toBe(true);
        expect(h.requests).toHaveLength(1);
        expect(h.requests[0].slice(0, 4)).toEqual(["edit", expect.objectContaining({ model: "saved model", size: "1:1", count: "1" }), "saved prompt", grid.metadata.references]);
        expect(h.connectionsRef.current[0].toNodeId).toBe("ordinary");
        expect(h.nodesRef.current.filter((n) => n.metadata?.storyboardParentId === grid.id)).toHaveLength(size ** 2);
    });
}

for (const entry of ["generate", "retry"] as const) {
    for (const action of ["cancel", "delete", "replace"] as const) {
        for (const lateResult of ["success", "failure"] as const) {
            test(`${entry}: ${action} during upload ignores late ${lateResult}`, async () => {
                const h = harness([entry === "generate" ? scriptNode() : gridNode()]);
                const upload = deferred<typeof uploaded>();
                const entered = deferred<void>();
                h.scope.uploadImage = () => {
                    entered.resolve();
                    return upload.promise;
                };
                h.scope.splitImageNode = async () => {
                    throw new Error("Canceled result must not reach splitting");
                };
                const run = entry === "generate" ? callback("generateStoryboardGrid", "insertAssistantImage", h.scope)(h.nodesRef.current[0], 3) : callback("handleRetryNode", "rerunGroup", h.scope)(h.nodesRef.current[0]);
                await entered.promise;
                const id = entry === "generate" ? "generated-grid" : "grid";
                const noticesBefore = [...h.notices];
                let replacement: AbortController | undefined;
                if (action === "cancel") h.scope.stopGenerationByRunningId(id);
                if (action === "delete") callback("deleteNodes", "groupSelection", h.scope)(new Set([id]));
                if (action === "replace") replacement = h.scope.startGenerationRequest(id, id, id);
                const before = structuredClone(h.nodesRef.current);
                const edges = structuredClone(h.connectionsRef.current);
                if (lateResult === "success") upload.resolve(uploaded);
                else upload.reject(new Error("Late storage failure"));
                await run;
                expect(h.nodesRef.current).toEqual(before);
                expect(h.connectionsRef.current).toEqual(edges);
                expect(h.notices).toEqual(noticesBefore);
                if (replacement) {
                    expect(h.generationRequestsRef.current.get(id)?.controller).toBe(replacement);
                    expect(h.runningId).toBe(id);
                }
            });
        }
    }
}

test("actual splitting uses the parent layout after ALL uploads finish and protects late shot edits", async () => {
    const grid = gridNode();
    const shot = node("existing-shot", CanvasNodeType.Image, { content: "old-shot", storageKey: "old-shot-key", prompt: "shot", storyboardParentId: grid.id, storyboardCellIndex: 0, storyboardAutoStorageKey: "old-shot-key", storyboardAutoPrompt: "shot" });
    const h = harness([grid, shot]);
    const gates = Array.from({ length: 9 }, () => deferred<typeof uploaded>());
    const entered = deferred<void>();
    let uploads = 0;
    h.scope.uploadImage = () => {
        const gate = gates[uploads++];
        if (uploads === 9) entered.resolve();
        return gate.promise;
    };
    const run = callback("splitImageNode", "maskEditImageNode", h.scope)(grid, { rows: 3, columns: 3 }, { storyboard: true });
    await entered.promise;
    expect(uploads).toBe(9);
    gates[0].resolve(uploaded);
    await gates[0].promise;
    h.nodesRef.current = h.nodesRef.current.map((n) =>
        n.id === grid.id ? { ...n, position: { x: 500, y: 400 }, width: 300, height: 600, title: "new title", metadata: { ...n.metadata, groupId: "new-group" } } : { ...n, metadata: { ...n.metadata, storageKey: "user-key", content: "user-image" } },
    );
    gates.slice(1).forEach((gate, i) => gate.resolve({ ...uploaded, storageKey: "cell-" + (i + 1) }));
    expect(await run).toBe(true);
    const children = h.nodesRef.current.filter((n) => n.metadata?.storyboardParentId === grid.id);
    expect(children).toHaveLength(9);
    expect(children[0]).toMatchObject({ position: { x: 896, y: 400 }, width: 100, height: 200 });
    expect(children[0].title).toContain("new title");
    expect(children.every((n) => n.metadata.groupId === "new-group")).toBe(true);
    expect(h.nodesRef.current.find((n) => n.id === shot.id)?.metadata).toMatchObject({ content: "user-image", storageKey: "user-key" });
    expect(h.nodesRef.current.find((n) => n.id === shot.id)?.metadata.storyboardParentId).toBeUndefined();
});

for (const action of ["cancel", "delete", "replace"] as const) {
    test(`actual retry ${action} during cell upload preserves grid and does not commit shots`, async () => {
        const h = harness([gridNode()]);
        const gate = deferred<typeof uploaded>();
        const entered = deferred<void>();
        let uploads = 0;
        h.scope.uploadImage = async (value: string) => {
            if (value === "data:fake") return uploaded;
            if (++uploads === 9) entered.resolve();
            return gate.promise;
        };
        h.scope.splitImageNode = callback("splitImageNode", "maskEditImageNode", h.scope);
        const run = callback("handleRetryNode", "rerunGroup", h.scope)(h.nodesRef.current[0]);
        await entered.promise;
        expect(uploads).toBe(9);
        expect(h.nodesRef.current[0].metadata.content).toBe(uploaded.url);
        let replacement;
        if (action === "cancel") h.scope.stopGenerationByRunningId("grid");
        if (action === "delete") callback("deleteNodes", "groupSelection", h.scope)(new Set(["grid"]));
        if (action === "replace") replacement = h.scope.startGenerationRequest("grid", "grid", "grid");
        const before = structuredClone(h.nodesRef.current);
        gate.resolve(uploaded);
        expect(await run).toBe(false);
        expect(h.nodesRef.current).toEqual(before);
        expect(h.connectionsRef.current).toEqual([]);
        expect(h.notices).toEqual([]);
        if (replacement) expect(h.runningId).toBe("grid");
    });
}

test("actual group rerun does not report success when cutting fails despite a valid grid image", async () => {
    const group = node("group", CanvasNodeType.Group);
    const grid = gridNode();
    grid.metadata.groupId = group.id;
    const h = harness([group, grid]);
    h.scope.splitImageNode = async () => {
        throw new Error("Cut failed");
    };
    h.scope.handleRetryNode = callback("handleRetryNode", "rerunGroup", h.scope);
    callback("rerunGroup", "deleteBatchImage", h.scope)(group);
    await h.confirmations[0].onOk();
    expect(h.requests).toHaveLength(1);
    expect(h.nodesRef.current.find((n) => n.id === grid.id)?.metadata.status).toBe("success");
    expect(h.notices).toContain("canvas.storyboard.splitFailed");
    expect(h.notices).not.toContain("canvas.template.rerunDone");
});

test("actual duplicate and insertion callbacks isolate ownership without changing sources", () => {
    const grid = gridNode();
    const shot = node("shot", CanvasNodeType.Image, { storyboardParentId: grid.id, storyboardCellIndex: 0, storageKey: "shot", content: "shot", storyboardAutoStorageKey: "shot", storyboardAutoPrompt: "" });
    const h = harness([grid, shot]);
    h.connectionsRef.current = [{ id: "edge", fromNodeId: grid.id, toNodeId: shot.id }];
    const snapshot = structuredClone({ nodes: h.nodesRef.current, connections: h.connectionsRef.current });
    const before = structuredClone(snapshot);
    callback("duplicateNode", "copySelectedNodes", h.scope)(shot.id);
    expect(h.nodesRef.current.at(-1).metadata.storyboardParentId).toBeUndefined();
    callback("insertWorkflowSnapshot", "pasteCopiedNodes", h.scope)(snapshot, " Copy");
    const pasted = h.nodesRef.current.slice(-2);
    expect(pasted[1].metadata.storyboardParentId).toBe(pasted[0].id);
    expect(h.nodesRef.current.slice(0, 2)).toEqual(before.nodes);
    expect(snapshot).toEqual(before);
});

test("actual generation validates empty text and unauthenticated remote mode before adding nodes", async () => {
    for (const empty of [true, false]) {
        const h = harness([node("script", CanvasNodeType.Text, { content: empty ? "  " : "script" })]);
        h.scope.token = null;
        h.scope.effectiveConfig.channelMode = "remote";
        await callback("generateStoryboardGrid", "insertAssistantImage", h.scope)(h.nodesRef.current[0], 3);
        expect(h.nodesRef.current).toHaveLength(1);
        expect(h.requests).toHaveLength(0);
        expect(h.authOpened).toBe(!empty);
    }
});

test("deleting the source text keeps its surviving grid request running to completion", async () => {
    const h = harness([scriptNode()]);
    const upload = deferred<typeof uploaded>();
    const entered = deferred<void>();
    h.scope.uploadImage = async (value: string) => {
        if (value === "data:fake") {
            entered.resolve();
            return upload.promise;
        }
        return { ...uploaded, storageKey: value };
    };
    h.scope.splitImageNode = callback("splitImageNode", "maskEditImageNode", h.scope);
    const run = callback("generateStoryboardGrid", "insertAssistantImage", h.scope)(h.nodesRef.current[0], 3);
    await entered.promise;
    const controller = h.generationRequestsRef.current.get("generated-grid").controller;
    callback("deleteNodes", "groupSelection", h.scope)(new Set(["script"]));
    const wasAborted = controller.signal.aborted;
    upload.resolve(uploaded);
    await run;
    expect(wasAborted).toBe(false);
    expect(h.nodesRef.current.find((n) => n.id === "generated-grid")?.metadata.status).toBe("success");
    expect(h.nodesRef.current.filter((n) => n.metadata?.storyboardParentId === "generated-grid")).toHaveLength(9);
    expect(h.connectionsRef.current.some((c) => c.fromNodeId === "script")).toBe(false);
    expect(h.runningId).toBeNull();
});

test("insertion releases ownership for non-grid parents and out-of-range cells without mutating the snapshot", () => {
    for (const parent of [node("parent", CanvasNodeType.Text, { storyboardGridSize: 3 }), node("parent", CanvasNodeType.Image), gridNode()]) {
        const h = harness([]);
        const index = parent.id === "grid" ? 9 : 0;
        const shot = node("shot", CanvasNodeType.Image, { content: "user-image", storageKey: "user-key", storyboardParentId: parent.id, storyboardCellIndex: index, storyboardAutoStorageKey: "user-key", storyboardAutoPrompt: "" });
        const snapshot = { nodes: [parent, shot], connections: [{ id: "edge", fromNodeId: parent.id, toNodeId: shot.id }] };
        const before = structuredClone(snapshot);
        Object.freeze(shot.metadata);
        Object.freeze(parent.metadata);
        callback("insertWorkflowSnapshot", "pasteCopiedNodes", h.scope)(snapshot, " Copy");
        const [pastedParent, pastedShot] = h.nodesRef.current;
        expect(pastedShot.metadata.storyboardParentId).toBeUndefined();
        expect(pastedShot.metadata.storyboardCellIndex).toBeUndefined();
        expect(pastedShot.metadata.storyboardAutoStorageKey).toBeUndefined();
        expect(pastedShot.metadata.storyboardAutoPrompt).toBeUndefined();
        expect(pastedShot.metadata.content).toBe("user-image");
        expect(h.connectionsRef.current[0]).toMatchObject({ fromNodeId: pastedParent.id, toNodeId: pastedShot.id });
        expect(snapshot).toEqual(before);
    }
});
