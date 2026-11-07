import { expect, test } from "bun:test";

import {
    buildStoryboardPrompt,
    hasStoryboardChildren,
    planNodeDuplication,
    planStoryboardSplit,
    planWorkflowInsertion,
    previewStoryboardRerun,
    releaseStoryboardNode,
    runStoryboardSplit,
    storyboardCellIndexOf,
    storyboardChildNodes,
    storyboardGridSizeOf,
    type StoryboardSplitPiece,
    type StoryboardUploadedImage,
} from "../src/lib/canvas/canvas-storyboard";
import { collectGroupRerunNodes } from "../src/lib/canvas/canvas-group-workflow";
import { CanvasNodeType, type CanvasConnection, type CanvasNodeData, type CanvasNodeMetadata } from "../src/types/canvas";

function makeNode(id: string, type: CanvasNodeData["type"], metadata?: CanvasNodeMetadata, position = { x: 0, y: 0 }): CanvasNodeData {
    return { id, type, title: id, position, width: 200, height: 200, metadata };
}

function makeConnection(id: string, fromNodeId: string, toNodeId: string): CanvasConnection {
    return { id, fromNodeId, toNodeId };
}

function makeGrid(id = "grid", size: 3 | 5 = 3, groupId?: string): CanvasNodeData {
    return makeNode(id, CanvasNodeType.Image, { storyboardGridSize: size, ...(groupId ? { groupId } : {}) });
}

function makeShot(id: string, parentId: string, cellIndex: number, content = id, extra: Partial<CanvasNodeMetadata> = {}): CanvasNodeData {
    const prompt = extra.prompt ?? `shot ${cellIndex}`;
    return makeNode(
        id,
        CanvasNodeType.Image,
        {
            storyboardParentId: parentId,
            storyboardCellIndex: cellIndex,
            status: "success",
            content,
            storageKey: `${content}-key`,
            naturalWidth: 10,
            naturalHeight: 10,
            bytes: 1,
            mimeType: "image/png",
            prompt,
            storyboardAutoStorageKey: `${content}-key`,
            storyboardAutoPrompt: `shot ${cellIndex}`,
            ...extra,
        },
        { x: cellIndex * 10, y: cellIndex },
    );
}

function makeFreshShots(size: 3 | 5, parentId: string, prefix = "fresh"): CanvasNodeData[] {
    return Array.from({ length: size * size }, (_, index) => makeShot(`${prefix}-${index}`, parentId, index, `${prefix}-${index}`));
}

test("builds a 3x3 storyboard prompt with the trimmed script", () => {
    const prompt = buildStoryboardPrompt("  女孩站在雨中的街头  ", 3);

    expect(prompt).toContain("3×3");
    expect(prompt).toContain("共 9 格");
    expect(prompt).toContain("女孩站在雨中的街头");
    expect(prompt).not.toContain("  女孩站在雨中的街头");
});

test("builds a 5x5 storyboard prompt", () => {
    const prompt = buildStoryboardPrompt("夜里的城市追逐戏", 5);

    expect(prompt).toContain("5×5");
    expect(prompt).toContain("共 25 格");
});

test("reads the storyboard grid size from serialized metadata", () => {
    expect(storyboardGridSizeOf({ storyboardGridSize: 3 })).toBe(3);
    expect(storyboardGridSizeOf(JSON.parse(JSON.stringify({ storyboardGridSize: 5 })))).toBe(5);
    expect(storyboardGridSizeOf({})).toBeNull();
    expect(storyboardGridSizeOf(undefined)).toBeNull();
    expect(storyboardGridSizeOf({ storyboardGridSize: 4 as never })).toBeNull();
});

test("owns only explicitly marked shots, not arbitrary outgoing edges", () => {
    const grid = makeGrid();
    const plainImage = makeNode("plain", CanvasNodeType.Image);
    const connections = [makeConnection("c1", grid.id, plainImage.id)];

    expect(hasStoryboardChildren(grid.id, [grid, plainImage])).toBe(false);
    expect(storyboardChildNodes(grid.id, [grid, plainImage])).toEqual([]);

    const second = makeShot("shot-2", grid.id, 2);
    const first = makeShot("shot-0", grid.id, 0);
    expect(storyboardChildNodes(grid.id, [grid, plainImage, second, first]).map((node) => node.id)).toEqual(["shot-0", "shot-2"]);
    expect(hasStoryboardChildren(grid.id, [grid, first])).toBe(true);
    expect(connections).toHaveLength(1);
});

test("plans 9 and 25 new shots on first split and joins the current group", () => {
    const grid = makeGrid("grid-9", 3, "group-1");
    const nine = planStoryboardSplit(grid.id, makeFreshShots(3, grid.id), [grid], []);
    expect(nine).not.toBeNull();
    expect(nine!.updates).toEqual([]);
    expect(nine!.releases).toEqual([]);
    expect(nine!.additions.map((node) => node.id)).toEqual(makeFreshShots(3, grid.id).map((node) => node.id));
    expect(nine!.additions.every((node) => node.metadata?.groupId === "group-1")).toBe(true);
    expect(nine!.additions.every((node) => node.metadata?.storyboardAutoStorageKey === node.metadata?.storageKey)).toBe(true);
    expect(nine!.additions.every((node) => node.metadata?.storyboardAutoPrompt === node.metadata?.prompt)).toBe(true);
    expect(nine!.connections).toEqual(nine!.additions.map((node) => ({ fromNodeId: grid.id, toNodeId: node.id })));

    const grid25 = makeGrid("grid-25", 5);
    const twentyFive = planStoryboardSplit(grid25.id, makeFreshShots(5, grid25.id), [grid25], []);
    expect(twentyFive!.additions).toHaveLength(25);
});

test("rerun refreshes unedited same-scope shots in place and keeps layout, titles, groups and edges", () => {
    const grid = makeGrid("grid", 3, "group-1");
    const existing = Array.from({ length: 9 }, (_, index) => {
        const node = makeShot(`shot-${index}`, grid.id, index, `old-${index}`, { groupId: "group-1" });
        return { ...node, title: `用户镜头 ${index}`, width: 300 + index, height: 250 + index };
    });
    const downstream = makeNode("downstream", CanvasNodeType.Image, { groupId: "group-1" });
    const reference = makeNode("reference", CanvasNodeType.Image);
    const nodes = [grid, ...existing, downstream, reference];
    const connections = [
        ...existing.map((node, index) => makeConnection(`edge-${index}`, grid.id, node.id)),
        makeConnection("edge-user", existing[0].id, downstream.id),
        makeConnection("edge-ref", reference.id, existing[1].id),
    ];
    const fresh = makeFreshShots(3, grid.id);

    const plan = planStoryboardSplit(grid.id, fresh, nodes, connections);

    expect(plan).not.toBeNull();
    expect(plan!.additions).toEqual([]);
    expect(plan!.releases).toEqual([]);
    expect(plan!.connections).toEqual([]);
    expect(plan!.updates.map((node) => node.id)).toEqual(existing.map((node) => node.id));
    expect(plan!.updates.map((node) => node.metadata?.content)).toEqual(fresh.map((node) => node.metadata?.content));
    expect(plan!.updates.map((node) => node.metadata?.storyboardAutoStorageKey)).toEqual(fresh.map((node) => node.metadata?.storageKey));
    expect(plan!.updates.map((node) => node.metadata?.storyboardAutoPrompt)).toEqual(fresh.map((node) => node.metadata?.prompt));
    expect(plan!.updates.map((node) => node.position)).toEqual(existing.map((node) => node.position));
    expect(plan!.updates.map((node) => [node.width, node.height])).toEqual(existing.map((node) => [node.width, node.height]));
    expect(plan!.updates.map((node) => node.title)).toEqual(existing.map((node) => node.title));
    expect(plan!.updates.every((node) => node.metadata?.groupId === "group-1")).toBe(true);
    expect(plan!.updates.some((node) => node.id === downstream.id)).toBe(false);
});

test("rerun fills only missing cells and links only the new shot", () => {
    const grid = makeGrid();
    const existing = [0, 1, 2, 3, 5, 6, 7, 8].map((index) => makeShot(`shot-${index}`, grid.id, index, `old-${index}`));
    const nodes = [grid, ...existing];
    const connections = existing.map((node, index) => makeConnection(`edge-${index}`, grid.id, node.id));
    const fresh = makeFreshShots(3, grid.id);

    const plan = planStoryboardSplit(grid.id, fresh, nodes, connections);

    expect(plan!.updates.map((node) => storyboardCellIndexOf(node.metadata))).toEqual([0, 1, 2, 3, 5, 6, 7, 8]);
    expect(plan!.additions.map((node) => storyboardCellIndexOf(node.metadata))).toEqual([4]);
    expect(plan!.connections).toEqual([{ fromNodeId: grid.id, toNodeId: plan!.additions[0].id }]);
});

test("protects a user-replaced image and refreshes the rest instead of overwriting it", () => {
    const grid = makeGrid("grid", 3, "group-1");
    const edited = makeShot("shot-0", grid.id, 0, "edited", { storageKey: "user-key", groupId: "group-1" });
    const rest = Array.from({ length: 8 }, (_, offset) => makeShot(`shot-${offset + 1}`, grid.id, offset + 1, `old-${offset + 1}`, { groupId: "group-1" }));
    const plan = planStoryboardSplit(grid.id, makeFreshShots(3, grid.id), [grid, edited, ...rest], []);

    expect(plan).not.toBeNull();
    expect(plan!.updates.some((node) => node.id === "shot-0")).toBe(false);
    expect(plan!.releases.map((node) => node.id)).toEqual(["shot-0"]);
    expect(plan!.releases[0].metadata?.storyboardParentId).toBeUndefined();
    expect(plan!.releases[0].metadata?.content).toBe("edited");
    expect(plan!.additions.map((node) => storyboardCellIndexOf(node.metadata))).toEqual([0]);
    expect(plan!.updates).toHaveLength(8);
});

test("protects a shot whose generation prompt was edited", () => {
    const grid = makeGrid();
    const shot = makeShot("shot-0", grid.id, 0, "old-0", { prompt: "用户改写的提示词" });
    const plan = planStoryboardSplit(grid.id, makeFreshShots(3, grid.id), [grid, shot], []);

    expect(plan!.updates).toEqual([]);
    expect(plan!.releases.map((node) => node.id)).toEqual(["shot-0"]);
    expect(plan!.additions).toHaveLength(9);
});

test("protects shots moved out of the grid group and still refreshes both-ungrouped shots", () => {
    const groupedGrid = makeGrid("grid", 3, "group-1");
    const movedOut = makeShot("moved", groupedGrid.id, 0, "moved", { groupId: "group-2" });
    const sameGroup = makeShot("same", groupedGrid.id, 1, "same", { groupId: "group-1" });
    const plan = planStoryboardSplit(groupedGrid.id, makeFreshShots(3, groupedGrid.id), [groupedGrid, movedOut, sameGroup], []);

    expect(plan!.releases.map((node) => node.id)).toEqual(["moved"]);
    expect(plan!.updates.map((node) => node.id)).toEqual(["same"]);
    expect(plan!.additions.map((node) => storyboardCellIndexOf(node.metadata))).toEqual([0, 2, 3, 4, 5, 6, 7, 8]);

    const looseGrid = makeGrid("loose");
    const looseShot = makeShot("loose-shot", looseGrid.id, 0);
    const loosePlan = planStoryboardSplit(looseGrid.id, makeFreshShots(3, looseGrid.id), [looseGrid, looseShot], []);
    expect(loosePlan!.updates.map((node) => node.id)).toEqual(["loose-shot"]);
});

test("preserves type-changed, duplicate-ownership and unidentifiable shots", () => {
    const grid = makeGrid("grid", 3, "group-1");
    const video = { ...makeShot("video", grid.id, 0, "video", { groupId: "group-1" }), type: CanvasNodeType.Video };
    const first = makeShot("dup-0", grid.id, 1, "dup-a", { groupId: "group-1" });
    const second = makeShot("dup-1", grid.id, 1, "dup-b", { groupId: "group-1" });
    const unknown = makeShot("unknown", grid.id, 2, "unknown", { groupId: "group-1", storyboardAutoStorageKey: undefined, storyboardAutoPrompt: undefined });

    const plan = planStoryboardSplit(grid.id, makeFreshShots(3, grid.id), [grid, video, first, second, unknown], []);

    expect(plan!.releases.map((node) => node.id).sort()).toEqual(["dup-0", "dup-1", "unknown", "video"]);
    expect(plan!.updates).toEqual([]);
    expect(plan!.additions.map((node) => storyboardCellIndexOf(node.metadata))).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
});

test("refuses to commit after deletion, cancellation, or when the parent is not a storyboard grid", () => {
    const grid = makeGrid();
    const fresh = makeFreshShots(3, grid.id);
    const controller = new AbortController();

    expect(planStoryboardSplit(grid.id, fresh, [grid], [], controller.signal)).not.toBeNull();
    controller.abort();
    expect(planStoryboardSplit(grid.id, fresh, [grid], [], controller.signal)).toBeNull();
    expect(planStoryboardSplit(grid.id, fresh, [], [])).toBeNull();

    const plainImage = makeNode("plain", CanvasNodeType.Image);
    const downstream = makeNode("downstream", CanvasNodeType.Image);
    const connections = [makeConnection("c1", plainImage.id, downstream.id)];
    expect(storyboardGridSizeOf(plainImage.metadata)).toBeNull();
    expect(hasStoryboardChildren(plainImage.id, [plainImage, downstream])).toBe(false);
    expect(planStoryboardSplit(plainImage.id, fresh, [plainImage, downstream], connections)).toBeNull();
});

test("ignores shots with invalid ownership indices", () => {
    const grid = makeGrid();
    const broken = makeNode("broken", CanvasNodeType.Image, { storyboardParentId: grid.id, storyboardCellIndex: -1 });
    expect(storyboardChildNodes(grid.id, [grid, broken])).toEqual([]);
    expect(hasStoryboardChildren(grid.id, [grid, broken])).toBe(false);

    const plan = planStoryboardSplit(grid.id, makeFreshShots(3, grid.id), [grid, broken], []);
    expect(plan!.releases.map((node) => node.id)).toEqual(["broken"]);
});

test("previews the true refresh, preserve and add scope before a group rerun", () => {
    const grid = makeGrid("grid", 3, "group-1");
    const refresh = Array.from({ length: 7 }, (_, index) => makeShot(`shot-${index}`, grid.id, index, `old-${index}`, { groupId: "group-1" }));
    const edited = makeShot("edited", grid.id, 7, "edited", { groupId: "group-1", storageKey: "user-key" });
    const moved = makeShot("moved", grid.id, 8, "moved", { groupId: "group-2" });

    expect(previewStoryboardRerun(grid, [grid, ...refresh, edited, moved])).toEqual({ refresh: 7, preserve: 2, add: 2 });
});

test("duplicating a single shot drops automatic ownership but keeps reference edges and source data", () => {
    const grid = makeGrid();
    const shot = makeShot("shot-0", grid.id, 0, "old-0");
    const reference = makeNode("reference", CanvasNodeType.Image);
    const connections = [makeConnection("edge-grid", grid.id, shot.id), makeConnection("edge-ref", reference.id, shot.id)];
    const before = JSON.parse(JSON.stringify(shot));

    const result = planNodeDuplication(shot, "copy-0", connections, (connection) => `copy-${connection.id}`);

    expect(result.node.id).toBe("copy-0");
    expect(result.node.title).toBe("shot-0 Copy");
    expect(result.node.position).toEqual({ x: 36, y: 36 });
    expect(result.node.metadata?.storyboardParentId).toBeUndefined();
    expect(result.node.metadata?.storyboardCellIndex).toBeUndefined();
    expect(result.node.metadata?.storyboardAutoStorageKey).toBeUndefined();
    expect(result.node.metadata?.storyboardAutoPrompt).toBeUndefined();
    expect(result.node.metadata?.prompt).toBe("shot 0");
    expect(result.connections.map((connection) => [connection.id, connection.fromNodeId, connection.toNodeId])).toEqual([
        ["copy-edge-grid", grid.id, "copy-0"],
        ["copy-edge-ref", reference.id, "copy-0"],
    ]);
    expect(shot).toEqual(before);
});

test("inserting a full snapshot remaps storyboard parents and groups through this snapshot only", () => {
    const group = makeNode("group", CanvasNodeType.Group);
    const grid = makeGrid("grid", 3, "group");
    const shot = makeShot("shot-0", grid.id, 0, "old-0", { groupId: "group" });
    const snapshot = { nodes: [group, grid, shot], connections: [makeConnection("edge-grid", grid.id, shot.id)] };

    const result = planWorkflowInsertion(snapshot, {
        titleSuffix: " Copy",
        offsetX: 10,
        offsetY: 20,
        createNodeId: (node) => `new-${node.id}`,
        createConnectionId: (connection) => `new-${connection.id}`,
    });

    const insertedShot = result.nodes.find((node) => node.id === "new-shot-0")!;
    expect(insertedShot.metadata?.storyboardParentId).toBe("new-grid");
    expect(insertedShot.metadata?.storyboardCellIndex).toBe(0);
    expect(insertedShot.metadata?.groupId).toBe("new-group");
    expect(insertedShot.metadata?.storyboardAutoStorageKey).toBe(insertedShot.metadata?.storageKey);
    expect(result.connections).toEqual([{ id: "new-edge-grid", fromNodeId: "new-grid", toNodeId: "new-shot-0" }]);
    expect(snapshot.nodes.find((node) => node.id === "shot-0")?.metadata?.storyboardParentId).toBe("grid");
});

test("partial paste releases ownership but keeps the ordinary reference edge to the original parent", () => {
    const grid = makeGrid();
    const shot = makeShot("shot-0", grid.id, 0, "old-0");
    const snapshot = { nodes: [shot], connections: [makeConnection("edge-grid", grid.id, shot.id)] };

    const result = planWorkflowInsertion(snapshot, {
        titleSuffix: "",
        offsetX: 0,
        offsetY: 0,
        createNodeId: (node) => `new-${node.id}`,
        createConnectionId: (connection) => `new-${connection.id}`,
    });

    expect(result.nodes[0].metadata?.storyboardParentId).toBeUndefined();
    expect(result.nodes[0].metadata?.storyboardCellIndex).toBeUndefined();
    expect(result.connections).toEqual([{ id: "new-edge-grid", fromNodeId: grid.id, toNodeId: "new-shot-0" }]);
});

test("templates create a new parent even when the source canvas parent is gone, and repeated pastes stay isolated", () => {
    const group = makeNode("group", CanvasNodeType.Group);
    const grid = makeGrid("grid", 3, "group");
    const shot = makeShot("shot-0", grid.id, 0, "old-0", { groupId: "group" });
    const snapshot = { nodes: [group, grid, shot], connections: [makeConnection("edge-grid", grid.id, shot.id)] };
    const before = JSON.parse(JSON.stringify(snapshot));

    const first = planWorkflowInsertion(snapshot, {
        titleSuffix: "",
        offsetX: 0,
        offsetY: 0,
        createNodeId: (node) => `a-${node.id}`,
        createConnectionId: (connection) => `a-${connection.id}`,
    });
    const second = planWorkflowInsertion(snapshot, {
        titleSuffix: "",
        offsetX: 0,
        offsetY: 0,
        createNodeId: (node) => `b-${node.id}`,
        createConnectionId: (connection) => `b-${connection.id}`,
    });

    expect(first.nodes.find((node) => node.id === "a-shot-0")?.metadata?.storyboardParentId).toBe("a-grid");
    expect(second.nodes.find((node) => node.id === "b-shot-0")?.metadata?.storyboardParentId).toBe("b-grid");
    expect(second.nodes.find((node) => node.id === "b-shot-0")?.metadata?.storyboardParentId).not.toBe("a-grid");
    expect(snapshot).toEqual(before);
});

type Deferred<T> = { promise: Promise<T>; resolve: (value: T) => void; reject: (error: unknown) => void };

function defer<T>(): Deferred<T> {
    let resolve!: (value: T) => void;
    let reject!: (error: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}

function uploadedImage(index: number): StoryboardUploadedImage {
    return { url: `blob:${index}`, storageKey: `key-${index}`, width: 100, height: 100, bytes: 10, mimeType: "image/png" };
}

function splitPieces(count: number): StoryboardSplitPiece[] {
    return Array.from({ length: count }, (_, index) => ({ row: Math.floor(index / 3), column: index % 3, dataUrl: `data-${index}` }));
}

function buildChild(piece: StoryboardSplitPiece, index: number, image: StoryboardUploadedImage): CanvasNodeData {
    return makeNode(`child-${index}`, CanvasNodeType.Image, {
        content: image.url,
        storageKey: image.storageKey,
        status: "success",
        prompt: "grid prompt",
        storyboardParentId: "grid",
        storyboardCellIndex: index,
    });
}

test("async split commits against the latest state and adds missing shots to the current group", async () => {
    const uploads = Array.from({ length: 9 }, () => defer<StoryboardUploadedImage>());
    let state = { nodes: [makeGrid("grid", 3, "group-1")], connections: [] as CanvasConnection[] };
    let committed = null;

    const running = runStoryboardSplit({
        parentId: "grid",
        split: async () => splitPieces(9),
        upload: () => uploads.shift()!.promise,
        buildChild,
        getNodes: () => state.nodes,
        getConnections: () => state.connections,
    });

    // User regroups the grid while uploads are still in flight.
    state = { nodes: [makeGrid("grid", 3, "group-2")], connections: [] };
    uploads.forEach((upload, index) => upload.resolve(uploadedImage(index)));
    committed = await running;

    expect(committed).not.toBeNull();
    expect(committed!.additions).toHaveLength(9);
    expect(committed!.additions.every((node) => node.metadata?.groupId === "group-2")).toBe(true);
});

test("async split refuses an absent grid, cancellation before upload, or replacement during splitting", async () => {
    {
        const uploads = Array.from({ length: 9 }, () => defer<StoryboardUploadedImage>());
        const running = runStoryboardSplit({
            parentId: "grid",
            split: async () => splitPieces(9),
            upload: () => uploads.shift()!.promise,
            buildChild,
            getNodes: () => ({ nodes: [], connections: [] }).nodes,
            getConnections: () => [],
        });
        uploads.forEach((upload, index) => upload.resolve(uploadedImage(index)));
        expect(await running).toBeNull();
    }

    {
        const uploads = Array.from({ length: 9 }, () => defer<StoryboardUploadedImage>());
        const controller = new AbortController();
        const running = runStoryboardSplit({
            parentId: "grid",
            signal: controller.signal,
            split: async () => splitPieces(9),
            upload: () => uploads.shift()!.promise,
            buildChild,
            getNodes: () => [makeGrid("grid")],
            getConnections: () => [],
        });
        controller.abort();
        uploads.forEach((upload, index) => upload.resolve(uploadedImage(index)));
        expect(await running).toBeNull();
    }

    {
        let current = true;
        let splitCalled = false;
        const gate = defer<void>();
        const running = runStoryboardSplit({
            parentId: "grid",
            isCurrent: () => current,
            split: async () => {
                splitCalled = true;
                await gate.promise;
                return splitPieces(9);
            },
            upload: async (dataUrl) => uploadedImage(Number(dataUrl.slice(5))),
            buildChild,
            getNodes: () => [makeGrid("grid")],
            getConnections: () => [],
        });
        current = false;
        gate.resolve();
        expect(await running).toBeNull();
        expect(splitCalled).toBe(true);
    }
});

test("async split releases an edited shot and refreshes the rest using the latest state", async () => {
    const grid = makeGrid("grid", 3, "group-1");
    const edited = makeShot("edited", grid.id, 0, "edited", { storageKey: "user-key", groupId: "group-1" });
    const rest = Array.from({ length: 8 }, (_, offset) => makeShot(`shot-${offset + 1}`, grid.id, offset + 1, `old-${offset + 1}`, { groupId: "group-1" }));

    const plan = await runStoryboardSplit({
        parentId: "grid",
        split: async () => splitPieces(9),
        upload: async (dataUrl) => uploadedImage(Number(dataUrl.slice(5))),
        buildChild,
        getNodes: () => [grid, edited, ...rest],
        getConnections: () => [],
    });

    expect(plan).not.toBeNull();
    expect(plan!.releases.map((node) => node.id)).toEqual(["edited"]);
    expect(plan!.releases[0].metadata?.storyboardParentId).toBeUndefined();
    expect(plan!.updates.map((node) => node.id)).toEqual(rest.map((node) => node.id));
    expect(plan!.additions.map((node) => storyboardCellIndexOf(node.metadata))).toEqual([0]);
});

test("released shots are excluded from the current group rerun but become normal candidates later", () => {
    const group = makeNode("group", CanvasNodeType.Group);
    const config = makeNode("config", CanvasNodeType.Config, { groupId: group.id });
    const grid = makeGrid("grid", 3, group.id);
    const shot = makeShot("shot-0", grid.id, 0, "old-0", { groupId: group.id, generationType: "edit" });
    const connections = [makeConnection("c1", config.id, grid.id), makeConnection("c2", grid.id, shot.id)];

    expect(collectGroupRerunNodes(group.id, [group, config, grid, shot], connections).map((node) => node.id)).toEqual(["grid"]);

    const released = releaseStoryboardNode(shot);
    expect(collectGroupRerunNodes(group.id, [group, config, grid, released], connections).map((node) => node.id)).toEqual(["grid", "shot-0"]);
});
