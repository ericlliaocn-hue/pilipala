// Executes production helpers and the real project.tsx submit callback with in-memory state.
// This covers callback wiring and generation-context isolation, not React rendering, DOM or real image decoding.
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { expect, test } from "bun:test";

import { CanvasNodeType, type CanvasConnection, type CanvasNodeData, type CanvasNodeMetadata } from "../src/types/canvas";

const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
Object.defineProperty(globalThis, "localStorage", { configurable: true, value: { getItem: () => null, setItem: () => {}, removeItem: () => {} } });
const { buildCharacterCardNodes, normalizeCharacterCardInput, resolveCharacterCardImage } = await import("../src/lib/canvas/canvas-character-card");
const { buildImageToolbarTools, defaultImageQuickToolIds, normalizeImageQuickToolIds, readImageQuickToolsConfig } = await import("../src/components/canvas/canvas-image-toolbar-tools");
const { buildNodeGenerationContext } = await import("../src/components/canvas/canvas-node-generation");
const { snapshotGroupWorkflow } = await import("../src/lib/canvas/canvas-group-workflow");
const { planWorkflowInsertion } = await import("../src/lib/canvas/canvas-storyboard");
if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
else Reflect.deleteProperty(globalThis, "localStorage");

function node(id: string, type: CanvasNodeData["type"], metadata?: CanvasNodeMetadata, position = { x: 0, y: 0 }, width = 200, height = 200): CanvasNodeData {
    return { id, type, title: id, position, width, height, metadata };
}

function connection(id: string, fromNodeId: string, toNodeId: string): CanvasConnection {
    return { id, fromNodeId, toNodeId };
}

const primaryImage = { id: "primary", status: "success" as const, content: "data:image/png;base64,PRIMARY", storageKey: "image:primary", naturalWidth: 100, naturalHeight: 200, bytes: 10, mimeType: "image/png" };
const otherImage = { id: "other", status: "success" as const, content: "data:image/png;base64,OTHER", storageKey: "image:other", naturalWidth: 50, naturalHeight: 50, bytes: 20, mimeType: "image/png" };

function batchSource(metadata: CanvasNodeMetadata = {}): CanvasNodeData {
    return node("source", CanvasNodeType.Image, {
        content: otherImage.content,
        storageKey: otherImage.storageKey,
        status: "error",
        errorDetails: "old error",
        prompt: "old prompt",
        generationType: "generation",
        model: "old model",
        size: "1:1",
        videoTaskId: "video-task",
        storyboardParentId: "grid",
        storyboardCellIndex: 2,
        storyboardGridSize: 3,
        storyboardAutoStorageKey: "auto-key",
        storyboardAutoPrompt: "auto prompt",
        groupId: "old-group",
        images: [otherImage, primaryImage],
        primaryImageId: primaryImage.id,
        ...metadata,
    }, { x: 500, y: 100 }, 200, 200);
}

test("builds exactly a group, image and text from the batch main image with whitelisted fields", () => {
    const source = batchSource();
    const before = structuredClone(source);
    const card = buildCharacterCardNodes(source, { name: "  Alice  ", description: "  tall, white coat  " });

    expect(card).not.toBeNull();
    expect(card!.group.type).toBe(CanvasNodeType.Group);
    expect(card!.group.title).toBe("Alice");
    expect(card!.image.type).toBe(CanvasNodeType.Image);
    expect(card!.text.type).toBe(CanvasNodeType.Text);
    expect(card!.image.metadata?.groupId).toBe(card!.group.id);
    expect(card!.text.metadata?.groupId).toBe(card!.group.id);
    expect(card!.image.metadata).toEqual({
        content: primaryImage.content,
        storageKey: primaryImage.storageKey,
        status: "success",
        naturalWidth: primaryImage.naturalWidth,
        naturalHeight: primaryImage.naturalHeight,
        bytes: primaryImage.bytes,
        mimeType: primaryImage.mimeType,
        groupId: card!.group.id,
    });
    expect(card!.text.metadata).toMatchObject({ content: "Alice\n\ntall, white coat", status: "success" });
    expect(card!.image.width / card!.image.height).toBeCloseTo(primaryImage.naturalWidth / primaryImage.naturalHeight);
    expect(card!.image.title).toBe("Alice");
    expect(card!.text.title).toBe("Alice");
    expect(card!.group.position.x).toBeLessThanOrEqual(card!.image.position.x);
    expect(card!.group.position.y).toBeLessThanOrEqual(card!.image.position.y);
    expect(card!.group.position.x + card!.group.width).toBeGreaterThanOrEqual(card!.text.position.x + card!.text.width);
    expect(source).toEqual(before);
});

test("rejects blank names, missing/empty sources and non-image nodes", () => {
    expect(normalizeCharacterCardInput({ name: "   " })).toBeNull();
    expect(buildCharacterCardNodes(batchSource(), { name: "  " })).toBeNull();
    expect(resolveCharacterCardImage(node("empty", CanvasNodeType.Image, { content: "", images: [{ ...primaryImage, content: "" }] }))).toBeNull();
    expect(resolveCharacterCardImage(node("missing", CanvasNodeType.Image, { images: [] }))).toBeNull();
    expect(resolveCharacterCardImage(node("text", CanvasNodeType.Text, { content: "hello" }))).toBeNull();
    expect(resolveCharacterCardImage(undefined)).toBeNull();
});

test("keeps an ordinary image source ratio and description optional", () => {
    const source = node("plain", CanvasNodeType.Image, { content: "data:image/jpeg;base64,PLAIN", storageKey: "image:plain", status: "success", naturalWidth: 300, naturalHeight: 150, bytes: 5, mimeType: "image/jpeg" });
    const card = buildCharacterCardNodes(source, { name: "Bob" });
    expect(card!.image.metadata).toEqual({ content: source.metadata!.content, storageKey: "image:plain", status: "success", naturalWidth: 300, naturalHeight: 150, bytes: 5, mimeType: "image/jpeg", groupId: card!.group.id });
    expect(card!.text.metadata?.content).toBe("Bob");
    expect(card!.image.width / card!.image.height).toBeCloseTo(2);
});

test("uses the displayed node image when its selected batch image no longer exists", () => {
    const source = batchSource({ primaryImageId: "removed", content: primaryImage.content, storageKey: primaryImage.storageKey, images: [otherImage] });
    const card = buildCharacterCardNodes(source, { name: "Alice" });
    expect(card!.image.metadata?.content).toBe(primaryImage.content);
    expect(card!.image.metadata?.storageKey).toBe(primaryImage.storageKey);
});

test("registers the toolbar tool for new defaults but leaves old custom selections untouched", () => {
    const source = batchSource();
    const calls: CanvasNodeData[] = [];
    const noop = () => {};
    const tools = buildImageToolbarTools(source, {
        onUpload: noop,
        onToggleFreeResize: noop,
        onMaskEdit: noop,
        onCrop: noop,
        onSplit: noop,
        onUpscale: noop,
        onSuperResolve: noop,
        onAngle: noop,
        onViewImage: noop,
        onCopyPrompt: noop,
        onReversePrompt: noop,
        onCharacterCard: (item) => calls.push(item),
    });
    const tool = tools.find((item) => item.id === "characterCard");
    expect(tool).toBeDefined();
    expect(defaultImageQuickToolIds).toContain("characterCard");
    tool!.onClick();
    expect(calls).toEqual([source]);

    const oldConfig = readImageQuickToolsConfig({ ids: ["info", "delete", "copyPrompt"], showLabels: false });
    expect(oldConfig.ids).not.toContain("characterCard");
    expect(normalizeImageQuickToolIds(["characterCard", "download", "unknown"])).toEqual(["download", "characterCard"]);
    expect(readImageQuickToolsConfig(null).ids).toContain("characterCard");
});

const source = readFileSync(new URL("../src/pages/canvas/project.tsx", import.meta.url), "utf8");
const transpiler = new Bun.Transpiler({ loader: "tsx", tsconfig: { compilerOptions: { jsx: "react" } } });
function callback(name: string, next: string, scope: Record<string, unknown>) {
    const from = source.indexOf(`    const ${name} = useCallback`);
    const to = source.indexOf(`    const ${next} = useCallback`, from);
    assert(from >= 0 && to > from);
    const bindings = Object.entries(scope).filter(([key]) => key !== name);
    return new Function(...bindings.map(([key]) => key), transpiler.transformSync(source.slice(from, to)) + `\nreturn ${name};`)(...bindings.map(([, value]) => value));
}

function harness(nodes: CanvasNodeData[]) {
    const nodesRef = { current: structuredClone(nodes) };
    const notices: Array<[string, string]> = [];
    const noop = () => {};
    let selected: Set<string> | null = null;
    let cardNodeId: string | null | undefined;
    const scope: Record<string, unknown> = {
        buildCharacterCardNodes,
        normalizeCharacterCardInput,
        resolveCharacterCardImage,
        nodesRef,
        useCallback: (fn: unknown) => fn,
        setNodes: (fn: unknown) => {
            nodesRef.current = typeof fn === "function" ? (fn as (value: CanvasNodeData[]) => CanvasNodeData[])(nodesRef.current) : (fn as CanvasNodeData[]);
        },
        setSelectedNodeIds: (value: Set<string>) => {
            selected = value;
        },
        setSelectedConnectionId: noop,
        setCharacterCardNodeId: (value: string | null) => {
            cardNodeId = value;
        },
        setContextMenu: noop,
        message: { success: (text: string) => notices.push(["success", text]), warning: (text: string) => notices.push(["warning", text]), error: (text: string) => notices.push(["error", text]) },
        t: (key: string) => key,
    };
    return {
        nodesRef,
        notices,
        run: callback("createCharacterCard", "cropImageNode", scope) as (id: string, input: { name: string; description?: string }) => boolean,
        get selected() {
            return selected;
        },
        get cardNodeId() {
            return cardNodeId;
        },
    };
}

test("the real submit callback appends one group, image and text and leaves the source and connections untouched", () => {
    const batch = batchSource();
    const text = node("sibling", CanvasNodeType.Text, { content: "sibling" });
    const h = harness([batch, text]);
    const before = structuredClone(h.nodesRef.current);
    const originalFetch = globalThis.fetch;
    let fetches = 0;
    globalThis.fetch = (async () => {
        fetches += 1;
        throw new Error("no service call expected");
    }) as typeof fetch;

    expect(h.run(batch.id, { name: "Alice", description: "tall" })).toBe(true);
    globalThis.fetch = originalFetch;

    expect(fetches).toBe(0);
    expect(h.nodesRef.current).toHaveLength(5);
    const group = h.nodesRef.current[2];
    expect(group.type).toBe(CanvasNodeType.Group);
    expect(h.nodesRef.current[3].type).toBe(CanvasNodeType.Image);
    expect(h.nodesRef.current[4].type).toBe(CanvasNodeType.Text);
    expect(h.selected).toEqual(new Set([group.id]));
    expect(h.cardNodeId).toBeNull();
    expect(h.nodesRef.current.slice(0, 2)).toEqual(before);
    expect(h.notices).toEqual([["success", "canvas.characterCard.created"]]);
});

test("the real submit callback fails without partial nodes when the source is missing, empty or cleared", () => {
    const batch = batchSource();
    const empty = node("empty", CanvasNodeType.Image, { content: "", images: [{ ...primaryImage, content: "" }] });
    const h = harness([batch, empty]);
    const before = structuredClone(h.nodesRef.current);

    expect(h.run("deleted", { name: "Alice" })).toBe(false);
    expect(h.run(empty.id, { name: "Alice" })).toBe(false);
    expect(h.run(batch.id, { name: "   " })).toBe(false);
    expect(h.nodesRef.current).toEqual(before);
    expect(h.notices.map(([level]) => level)).toEqual(["error", "error", "warning"]);

    h.nodesRef.current = h.nodesRef.current.filter((item) => item.id !== batch.id);
    const afterDelete = structuredClone(h.nodesRef.current);
    expect(h.run(batch.id, { name: "Alice" })).toBe(false);
    expect(h.nodesRef.current).toEqual(afterDelete);
});

function cardNodes(id: string, name: string, content: string) {
    const source = node(id, CanvasNodeType.Image, { content, storageKey: `image:${id}`, status: "success", naturalWidth: 100, naturalHeight: 200, bytes: 1, mimeType: "image/png" });
    const card = buildCharacterCardNodes(source, { name, description: `${name} description` });
    assert(card);
    return { source, ...card };
}

test("production generation context only injects the explicitly referenced character and dedupes it", () => {
    const alice = cardNodes("alice-source", "Alice", "data:image/png;base64,ALICE");
    const bob = cardNodes("bob-source", "Bob", "data:image/png;base64,BOB");
    const composerContent = `make a scene with @[node:${alice.group.id}]`;
    const config = node("config", CanvasNodeType.Config, { composerContent });
    const nodes = [alice.source, alice.group, alice.image, alice.text, bob.source, bob.group, bob.image, bob.text, config];
    const connections = [connection("c1", alice.group.id, config.id), connection("c2", bob.group.id, config.id)];
    const before = structuredClone(nodes);

    const context = buildNodeGenerationContext(config.id, nodes, connections, composerContent);

    expect(context.referenceImages).toHaveLength(1);
    expect(context.referenceImages[0].dataUrl).toBe("data:image/png;base64,ALICE");
    expect(context.textCount).toBe(1);
    expect(context.prompt).toContain("Alice description");
    expect(context.prompt).not.toContain("Bob description");
    expect(context.prompt).not.toContain("@[node:");
    expect(nodes).toEqual(before);
});

test("config composer text without @ does not inject either character", () => {
    const alice = cardNodes("alice-source", "Alice", "data:image/png;base64,ALICE");
    const bob = cardNodes("bob-source", "Bob", "data:image/png;base64,BOB");
    const config = node("config", CanvasNodeType.Config, { composerContent: "make a scene with both characters" });
    const nodes = [alice.source, alice.group, alice.image, alice.text, bob.source, bob.group, bob.image, bob.text, config];
    const connections = [connection("c1", alice.group.id, config.id), connection("c2", bob.group.id, config.id)];

    const context = buildNodeGenerationContext(config.id, nodes, connections, "make a scene with both characters");

    expect(context.referenceImages).toHaveLength(0);
    expect(context.textCount).toBe(0);
    expect(context.prompt).toBe("make a scene with both characters");
});

test("an unconnected character is not injected even when its token is present", () => {
    const alice = cardNodes("alice-source", "Alice", "data:image/png;base64,ALICE");
    const bob = cardNodes("bob-source", "Bob", "data:image/png;base64,BOB");
    const composerContent = `make a scene with @[node:${bob.group.id}]`;
    const config = node("config", CanvasNodeType.Config, { composerContent });
    const nodes = [alice.group, alice.image, alice.text, bob.group, bob.image, bob.text, config];
    const connections = [connection("c1", alice.group.id, config.id)];

    const context = buildNodeGenerationContext(config.id, nodes, connections, composerContent);

    expect(context.referenceImages).toHaveLength(0);
    expect(context.textCount).toBe(0);
});

test("duplicate @ references stay deduped and card assets survive deleting the original image", () => {
    const alice = cardNodes("alice-source", "Alice", "data:image/png;base64,ALICE");
    const composerContent = `use @[node:${alice.group.id}] then @[node:${alice.group.id}]`;
    const config = node("config", CanvasNodeType.Config, { composerContent });
    const withoutSource = [alice.group, alice.image, alice.text, config];
    const connections = [connection("c1", alice.group.id, config.id)];

    const context = buildNodeGenerationContext(config.id, withoutSource, connections, composerContent);

    expect(context.referenceImages).toHaveLength(1);
    expect(context.referenceImages[0].dataUrl).toBe("data:image/png;base64,ALICE");
    expect(context.textCount).toBe(1);
});

test("connection-based generation still reads a character card group without the composer path", () => {
    const alice = cardNodes("alice-source", "Alice", "data:image/png;base64,ALICE");
    const config = node("config", CanvasNodeType.Config, {});
    const nodes = [alice.group, alice.image, alice.text, config];
    const connections = [connection("c1", alice.group.id, config.id)];

    const context = buildNodeGenerationContext(config.id, nodes, connections, "paint Alice");

    expect(context.referenceImages).toHaveLength(1);
    expect(context.textCount).toBe(1);
    expect(context.prompt).toContain("Alice description");
});

test("template snapshot and insertion remap the card group without leaking storyboard ownership", () => {
    const alice = cardNodes("alice-source", "Alice", "data:image/png;base64,ALICE");
    const nodes = [alice.group, alice.image, alice.text];
    const snapshot = snapshotGroupWorkflow(alice.group.id, nodes, []);
    expect(snapshot).not.toBeNull();

    const inserted = planWorkflowInsertion(snapshot!, {
        titleSuffix: " Copy",
        offsetX: 10,
        offsetY: 20,
        createNodeId: (_node, index) => `copy-${index}`,
        createConnectionId: (item, index) => `${item.id}-${index}`,
    });
    const group = inserted.nodes.find((item) => item.type === CanvasNodeType.Group)!;
    const image = inserted.nodes.find((item) => item.type === CanvasNodeType.Image)!;
    const text = inserted.nodes.find((item) => item.type === CanvasNodeType.Text)!;

    expect(inserted.nodes).toHaveLength(3);
    expect(group.id).not.toBe(alice.group.id);
    expect(image.metadata?.groupId).toBe(group.id);
    expect(text.metadata?.groupId).toBe(group.id);
    expect(image.metadata?.content).toBe("data:image/png;base64,ALICE");
    expect(image.metadata?.storyboardParentId).toBeUndefined();
    expect(image.metadata?.storyboardCellIndex).toBeUndefined();
    expect(alice.group.metadata?.groupId).toBeUndefined();
    expect(nodes).toEqual([alice.group, alice.image, alice.text]);
});
