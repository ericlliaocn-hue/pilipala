import { findRetrySourceNode } from "@/lib/canvas/canvas-generation-helpers";
import { CanvasNodeType, type CanvasConnection, type CanvasNodeData, type CanvasNodeTypeId } from "@/types/canvas";

export type CanvasWorkflowSnapshot = {
    nodes: CanvasNodeData[];
    connections: CanvasConnection[];
};

const RERUN_TYPES = new Set<CanvasNodeTypeId>([CanvasNodeType.Image, CanvasNodeType.Video, CanvasNodeType.Audio, CanvasNodeType.Text]);

function cloneWorkflowNode(node: CanvasNodeData): CanvasNodeData {
    const metadata = node.metadata ? { ...node.metadata } : undefined;
    if (metadata) {
        if (metadata.status === "loading") metadata.status = "idle";
        delete metadata.errorDetails;
        delete metadata.videoTaskId;
        if (metadata.images) {
            metadata.images = metadata.images.map((image) => {
                const next = { ...image };
                if (next.status === "loading") next.status = "idle";
                delete next.errorDetails;
                return next;
            });
        }
    }
    return { ...node, position: { ...node.position }, metadata };
}

export function snapshotGroupWorkflow(groupId: string, nodes: CanvasNodeData[], connections: CanvasConnection[]): CanvasWorkflowSnapshot | null {
    const group = nodes.find((node) => node.id === groupId);
    if (!group || group.type !== CanvasNodeType.Group) return null;
    const ids = new Set(nodes.filter((node) => node.id === groupId || node.metadata?.groupId === groupId).map((node) => node.id));
    return {
        nodes: nodes.filter((node) => ids.has(node.id)).map(cloneWorkflowNode),
        connections: connections.filter((connection) => ids.has(connection.fromNodeId) && ids.has(connection.toNodeId)).map((connection) => ({ ...connection })),
    };
}

export function collectGroupRerunNodes(groupId: string, nodes: CanvasNodeData[], connections: CanvasConnection[]): CanvasNodeData[] {
    const candidates = nodes.filter(
        (node) => node.metadata?.groupId === groupId && RERUN_TYPES.has(node.type) && (Boolean(node.metadata?.generationType) || Boolean(findRetrySourceNode(node.id, nodes, connections))),
    );
    const candidateIds = new Set(candidates.map((node) => node.id));
    const indegree = new Map(candidates.map((node) => [node.id, 0]));
    const outgoing = new Map<string, string[]>();

    connections.forEach((connection) => {
        if (!candidateIds.has(connection.fromNodeId) || !candidateIds.has(connection.toNodeId) || connection.fromNodeId === connection.toNodeId) return;
        outgoing.set(connection.fromNodeId, [...(outgoing.get(connection.fromNodeId) || []), connection.toNodeId]);
        indegree.set(connection.toNodeId, (indegree.get(connection.toNodeId) || 0) + 1);
    });

    const sorted: CanvasNodeData[] = [];
    const emitted = new Set<string>();
    while (sorted.length < candidates.length) {
        const next = candidates.find((node) => !emitted.has(node.id) && (indegree.get(node.id) || 0) === 0);
        if (!next) {
            candidates.forEach((node) => {
                if (!emitted.has(node.id)) sorted.push(node);
            });
            break;
        }
        emitted.add(next.id);
        sorted.push(next);
        (outgoing.get(next.id) || []).forEach((toNodeId) => indegree.set(toNodeId, (indegree.get(toNodeId) || 0) - 1));
    }
    return sorted;
}
