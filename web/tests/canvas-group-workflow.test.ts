import { expect, test } from "bun:test";

import { collectGroupRerunNodes, snapshotGroupWorkflow } from "../src/lib/canvas/canvas-group-workflow";
import { CanvasNodeType, type CanvasConnection, type CanvasNodeData, type CanvasNodeMetadata } from "../src/types/canvas";

function makeNode(id: string, type: CanvasNodeData["type"], metadata?: CanvasNodeMetadata): CanvasNodeData {
    return { id, type, title: id, position: { x: 0, y: 0 }, width: 200, height: 200, metadata };
}

function makeConnection(id: string, fromNodeId: string, toNodeId: string): CanvasConnection {
    return { id, fromNodeId, toNodeId };
}

test("snapshots only the group and its members, dropping cross-group connections", () => {
    const group = makeNode("group-1", CanvasNodeType.Group);
    const loading = makeNode("image-1", CanvasNodeType.Image, {
        groupId: "group-1",
        status: "loading",
        errorDetails: "failed",
        videoTaskId: "task-1",
        images: [{ id: "inner-1", status: "loading", errorDetails: "inner failed", content: "", naturalWidth: 1, naturalHeight: 1, bytes: 1, mimeType: "image/png" }],
    });
    const text = makeNode("text-1", CanvasNodeType.Text, { groupId: "group-1", content: "hello" });
    const outsider = makeNode("image-2", CanvasNodeType.Image, { groupId: "group-2", status: "loading" });
    const nodes = [group, loading, text, outsider];
    const connections = [makeConnection("c1", "image-1", "text-1"), makeConnection("c2", "image-1", "image-2"), makeConnection("c3", "image-2", "text-1")];

    const snapshot = snapshotGroupWorkflow("group-1", nodes, connections);

    expect(snapshot).not.toBeNull();
    expect(snapshot!.nodes.map((node) => node.id)).toEqual(["group-1", "image-1", "text-1"]);
    expect(snapshot!.connections.map((connection) => connection.id)).toEqual(["c1"]);

    const cloned = snapshot!.nodes.find((node) => node.id === "image-1")!;
    expect(cloned).not.toBe(loading);
    expect(cloned.metadata?.status).toBe("idle");
    expect(cloned.metadata?.errorDetails).toBeUndefined();
    expect(cloned.metadata?.videoTaskId).toBeUndefined();
    expect(cloned.metadata?.images?.[0].status).toBe("idle");
    expect(cloned.metadata?.images?.[0].errorDetails).toBeUndefined();
});

test("returns null when the id is missing or not a group node", () => {
    const nodes = [makeNode("group-1", CanvasNodeType.Group), makeNode("text-1", CanvasNodeType.Text, { groupId: "group-1" })];
    expect(snapshotGroupWorkflow("missing", nodes, [])).toBeNull();
    expect(snapshotGroupWorkflow("text-1", nodes, [])).toBeNull();
});

test("collects rerunnable members in dependency order, skipping config and plain text", () => {
    const nodes = [
        makeNode("group-1", CanvasNodeType.Group),
        makeNode("text-hand", CanvasNodeType.Text, { groupId: "group-1", content: "handwritten" }),
        makeNode("config-1", CanvasNodeType.Config, { groupId: "group-1", prompt: "prompt" }),
        makeNode("text-gen", CanvasNodeType.Text, { groupId: "group-1", generationType: "generation" }),
        makeNode("image-1", CanvasNodeType.Image, { groupId: "group-1" }),
        makeNode("image-2", CanvasNodeType.Image, { groupId: "group-1", generationType: "generation" }),
        makeNode("video-1", CanvasNodeType.Video, { groupId: "group-1", generationType: "generation" }),
    ];
    const connections = [makeConnection("c1", "config-1", "image-1"), makeConnection("c2", "image-1", "video-1"), makeConnection("c3", "image-2", "video-1")];

    const result = collectGroupRerunNodes("group-1", nodes, connections).map((node) => node.id);

    expect(result).toEqual(["text-gen", "image-1", "image-2", "video-1"]);
});

test("collectGroupRerunNodes terminates on cycles and keeps original order", () => {
    const nodes = [makeNode("group-1", CanvasNodeType.Group), makeNode("image-1", CanvasNodeType.Image, { groupId: "group-1", generationType: "generation" }), makeNode("image-2", CanvasNodeType.Image, { groupId: "group-1", generationType: "generation" })];
    const connections = [makeConnection("c1", "image-1", "image-2"), makeConnection("c2", "image-2", "image-1")];

    const result = collectGroupRerunNodes("group-1", nodes, connections).map((node) => node.id);

    expect(result).toEqual(["image-1", "image-2"]);
});
