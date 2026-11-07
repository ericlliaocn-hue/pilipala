import { CanvasNodeType, type CanvasConnection, type CanvasNodeData, type CanvasNodeMetadata } from "@/types/canvas";

export type StoryboardGridSize = 3 | 5;

export type StoryboardUploadedImage = {
    url: string;
    storageKey?: string;
    width: number;
    height: number;
    bytes: number;
    mimeType: string;
};

export type StoryboardSplitPiece = {
    row: number;
    column: number;
    dataUrl: string;
};

export type StoryboardSplitCommit = {
    updates: CanvasNodeData[];
    additions: CanvasNodeData[];
    connections: Array<Pick<CanvasConnection, "fromNodeId" | "toNodeId">>;
    releases: CanvasNodeData[];
    refreshCount: number;
    preserveCount: number;
    addCount: number;
};

export function storyboardGridSizeOf(metadata?: CanvasNodeMetadata): StoryboardGridSize | null {
    const size = metadata?.storyboardGridSize;
    return size === 3 || size === 5 ? size : null;
}

export function storyboardCellIndexOf(metadata?: CanvasNodeMetadata): number | null {
    const index = metadata?.storyboardCellIndex;
    return typeof index === "number" && Number.isInteger(index) && index >= 0 ? index : null;
}

/** Only nodes explicitly marked as this grid's shots belong to it; ordinary downstream edges do not. */
export function storyboardChildNodes(parentId: string, nodes: CanvasNodeData[]): CanvasNodeData[] {
    return nodes
        .filter((node) => node.metadata?.storyboardParentId === parentId && storyboardCellIndexOf(node.metadata) !== null)
        .sort((left, right) => storyboardCellIndexOf(left.metadata)! - storyboardCellIndexOf(right.metadata)!);
}

export function hasStoryboardChildren(parentId: string, nodes: CanvasNodeData[]): boolean {
    return storyboardChildNodes(parentId, nodes).length > 0;
}

export function storyboardScopeMatches(grid: CanvasNodeData, shot: CanvasNodeData): boolean {
    return (grid.metadata?.groupId ?? null) === (shot.metadata?.groupId ?? null);
}

/**
 * An owned shot keeps being auto-managed only while it still points at the same grid, stays in the same
 * group scope, is still an image, and its image (stable storage key, not the refreshable blob URL) and
 * generation prompt match what the last automatic split wrote. Anything else is treated as user content.
 */
export function isAutoManagedStoryboardShot(grid: CanvasNodeData, shot: CanvasNodeData): boolean {
    const metadata = shot.metadata;
    if (shot.type !== CanvasNodeType.Image || !metadata) return false;
    if (metadata.storyboardParentId !== grid.id || storyboardCellIndexOf(metadata) === null) return false;
    if (!metadata.storyboardAutoStorageKey || metadata.storageKey !== metadata.storyboardAutoStorageKey) return false;
    if ((metadata.prompt ?? "") !== (metadata.storyboardAutoPrompt ?? "")) return false;
    return storyboardScopeMatches(grid, shot);
}

export type StoryboardShotAssessment = {
    refreshByIndex: Map<number, CanvasNodeData>;
    releases: CanvasNodeData[];
};

export function assessStoryboardShots(grid: CanvasNodeData, nodes: CanvasNodeData[]): StoryboardShotAssessment {
    const size = storyboardGridSizeOf(grid.metadata);
    const total = size ? size * size : 0;
    const owned = nodes.filter((node) => node.id !== grid.id && node.metadata?.storyboardParentId === grid.id);
    const byIndex = new Map<number, CanvasNodeData[]>();
    const releases: CanvasNodeData[] = [];

    owned.forEach((node) => {
        const index = storyboardCellIndexOf(node.metadata);
        if (index === null || index >= total) {
            releases.push(node);
            return;
        }
        byIndex.set(index, [...(byIndex.get(index) || []), node]);
    });

    const refreshByIndex = new Map<number, CanvasNodeData>();
    byIndex.forEach((group, index) => {
        if (group.length !== 1 || !isAutoManagedStoryboardShot(grid, group[0])) {
            releases.push(...group);
            return;
        }
        refreshByIndex.set(index, group[0]);
    });
    return { refreshByIndex, releases };
}

export type StoryboardRerunPreview = {
    refresh: number;
    preserve: number;
    add: number;
};

export function previewStoryboardRerun(grid: CanvasNodeData, nodes: CanvasNodeData[]): StoryboardRerunPreview {
    const size = storyboardGridSizeOf(grid.metadata);
    if (!size) return { refresh: 0, preserve: 0, add: 0 };
    const { refreshByIndex, releases } = assessStoryboardShots(grid, nodes);
    const total = size * size;
    const refresh = [...refreshByIndex.keys()].filter((index) => index < total).length;
    return { refresh, preserve: releases.length, add: total - refresh };
}

/**
 * Plans a storyboard split commit. Unedited auto shots in the same group are refreshed in place so their ids,
 * positions, titles, sizes, groups and connections survive; edited, edited-prompt, moved-out, type-changed,
 * duplicate or unidentifiable shots are released back to independent images; missing cells are appended and
 * join the grid's current group. Returns null when the parent is gone or the request was canceled.
 */
export function planStoryboardSplit(
    parentId: string,
    freshChildren: CanvasNodeData[],
    nodes: CanvasNodeData[],
    connections: CanvasConnection[],
    signal?: AbortSignal,
): StoryboardSplitCommit | null {
    if (signal?.aborted) return null;
    const parent = nodes.find((node) => node.id === parentId);
    if (!parent || parent.type !== CanvasNodeType.Image || !storyboardGridSizeOf(parent.metadata)) return null;

    const { refreshByIndex, releases } = assessStoryboardShots(parent, nodes);
    const updates: CanvasNodeData[] = [];
    const additions: CanvasNodeData[] = [];
    const used = new Set<number>();
    const total = storyboardGridSizeOf(parent.metadata)! ** 2;

    freshChildren.forEach((fresh) => {
        const index = storyboardCellIndexOf(fresh.metadata);
        if (index === null || index >= total || used.has(index)) return;
        used.add(index);
        const existing = refreshByIndex.get(index);
        if (existing) updates.push(refreshStoryboardChild(existing, fresh, parent));
        else additions.push(joinParentGroup(fresh, parent));
    });

    const connected = new Set(connections.filter((connection) => connection.fromNodeId === parentId).map((connection) => connection.toNodeId));
    return {
        updates,
        additions,
        connections: additions.filter((child) => !connected.has(child.id)).map((child) => ({ fromNodeId: parentId, toNodeId: child.id })),
        releases: releases.map(releaseStoryboardNode),
        refreshCount: updates.length,
        preserveCount: releases.length,
        addCount: additions.length,
    };
}

function refreshStoryboardChild(existing: CanvasNodeData, fresh: CanvasNodeData, parent: CanvasNodeData): CanvasNodeData {
    const freshMetadata = fresh.metadata || {};
    return {
        ...existing,
        metadata: {
            ...existing.metadata,
            content: freshMetadata.content,
            storageKey: freshMetadata.storageKey,
            status: freshMetadata.status,
            errorDetails: undefined,
            naturalWidth: freshMetadata.naturalWidth,
            naturalHeight: freshMetadata.naturalHeight,
            bytes: freshMetadata.bytes,
            mimeType: freshMetadata.mimeType,
            prompt: freshMetadata.prompt,
            storyboardParentId: parent.id,
            storyboardCellIndex: freshMetadata.storyboardCellIndex,
            storyboardAutoStorageKey: freshMetadata.storageKey,
            storyboardAutoPrompt: freshMetadata.prompt,
        },
    };
}

function joinParentGroup(fresh: CanvasNodeData, parent: CanvasNodeData): CanvasNodeData {
    const metadata = { ...fresh.metadata };
    if (parent.metadata?.groupId) metadata.groupId = parent.metadata.groupId;
    else delete metadata.groupId;
    metadata.storyboardParentId = parent.id;
    metadata.storyboardAutoStorageKey = metadata.storageKey;
    metadata.storyboardAutoPrompt = metadata.prompt;
    return { ...fresh, metadata };
}

/** Removes the automatic storyboard ownership while keeping every other user-visible field intact. */
export function releaseStoryboardOwnership(metadata?: CanvasNodeMetadata): CanvasNodeMetadata | undefined {
    if (!metadata) return undefined;
    const next = { ...metadata };
    delete next.storyboardParentId;
    delete next.storyboardCellIndex;
    delete next.storyboardAutoStorageKey;
    delete next.storyboardAutoPrompt;
    return next;
}

export function releaseStoryboardNode(node: CanvasNodeData): CanvasNodeData {
    return { ...node, metadata: releaseStoryboardOwnership(node.metadata) };
}

function remapStoryboardOwnership(metadata: CanvasNodeMetadata, idMap: Map<string, string>, parents: Map<string, CanvasNodeData>) {
    const parentId = metadata.storyboardParentId;
    const index = storyboardCellIndexOf(metadata);
    const mappedParentId = parentId ? idMap.get(parentId) : undefined;
    const parent = parentId ? parents.get(parentId) : undefined;
    const size = parent?.type === CanvasNodeType.Image ? storyboardGridSizeOf(parent.metadata) : null;
    if (size && index !== null && index < size * size && mappedParentId) {
        metadata.storyboardParentId = mappedParentId;
        metadata.storyboardCellIndex = index;
        return;
    }
    delete metadata.storyboardParentId;
    delete metadata.storyboardCellIndex;
    delete metadata.storyboardAutoStorageKey;
    delete metadata.storyboardAutoPrompt;
}

/** Duplicates one node: a standalone shot copy never inherits automatic storyboard ownership. */
export function planNodeDuplication(
    source: CanvasNodeData,
    id: string,
    connections: CanvasConnection[],
    createConnectionId: (connection: CanvasConnection) => string,
    titleSuffix = " Copy",
): { node: CanvasNodeData; connections: CanvasConnection[] } {
    const node: CanvasNodeData = {
        ...source,
        id,
        title: `${source.title}${titleSuffix}`,
        position: { x: source.position.x + 36, y: source.position.y + 36 },
        metadata: releaseStoryboardOwnership(source.metadata),
    };
    return {
        node,
        connections: connections.filter((connection) => connection.toNodeId === source.id).map((connection) => ({ ...connection, id: createConnectionId(connection), toNodeId: id })),
    };
}

export type WorkflowSnapshot = {
    nodes: CanvasNodeData[];
    connections: CanvasConnection[];
};

export type WorkflowInsertionOptions = {
    titleSuffix: string;
    offsetX: number;
    offsetY: number;
    createNodeId: (node: CanvasNodeData, index: number) => string;
    createConnectionId: (connection: CanvasConnection, index: number) => string;
};

export type WorkflowInsertion = {
    idMap: Map<string, string>;
    nodes: CanvasNodeData[];
    connections: CanvasConnection[];
};

/**
 * Inserts a copied/template snapshot without touching the source snapshot. Storyboard parents are remapped only
 * through this snapshot's own id map; when the parent is absent the copy becomes independent while its ordinary
 * reference edges (including edges to nodes outside the snapshot) are kept.
 */
export function planWorkflowInsertion(snapshot: WorkflowSnapshot, options: WorkflowInsertionOptions): WorkflowInsertion {
    const idMap = new Map<string, string>();
    snapshot.nodes.forEach((node, index) => idMap.set(node.id, options.createNodeId(node, index)));
    const parents = new Map(snapshot.nodes.map((node) => [node.id, node]));

    const nodes = snapshot.nodes.map((node, index) => {
        const id = idMap.get(node.id)!;
        const metadata = node.metadata ? { ...node.metadata } : undefined;
        if (metadata) {
            metadata.groupId = metadata.groupId ? idMap.get(metadata.groupId) : undefined;
            remapStoryboardOwnership(metadata, idMap, parents);
        }
        return {
            ...node,
            id,
            title: node.title.endsWith(options.titleSuffix) ? node.title : `${node.title}${options.titleSuffix}`,
            position: { x: node.position.x + options.offsetX, y: node.position.y + options.offsetY },
            metadata,
        } satisfies CanvasNodeData;
    });

    const connections = snapshot.connections.flatMap((connection, index) => {
        const toNodeId = idMap.get(connection.toNodeId);
        if (!toNodeId) return [];
        return [
            {
                ...connection,
                id: options.createConnectionId(connection, index),
                fromNodeId: idMap.get(connection.fromNodeId) ?? connection.fromNodeId,
                toNodeId,
            },
        ];
    });
    return { idMap, nodes, connections };
}

export type StoryboardSplitRuntime = {
    parentId: string;
    signal?: AbortSignal;
    isCurrent?: () => boolean;
    split: () => Promise<StoryboardSplitPiece[]>;
    upload: (dataUrl: string) => Promise<StoryboardUploadedImage>;
    buildChild: (piece: StoryboardSplitPiece, index: number, image: StoryboardUploadedImage, parent: CanvasNodeData) => CanvasNodeData;
    getNodes: () => CanvasNodeData[];
    getConnections: () => CanvasConnection[];
};

/**
 * Runs the async split pipeline and only plans a commit against the latest state. Cancellation, a replaced
 * request, or a deleted parent between awaits yields null so no orphan shots or edges are committed.
 */
export async function runStoryboardSplit(runtime: StoryboardSplitRuntime): Promise<StoryboardSplitCommit | null> {
    const alive = () => !runtime.signal?.aborted && (runtime.isCurrent?.() ?? true);
    if (!alive()) return null;
    const pieces = await runtime.split();
    if (!alive()) return null;
    const images = await Promise.all(pieces.map((piece) => runtime.upload(piece.dataUrl)));
    if (!alive()) return null;
    const nodes = runtime.getNodes();
    const parent = nodes.find((node) => node.id === runtime.parentId);
    if (!parent || parent.type !== CanvasNodeType.Image || !storyboardGridSizeOf(parent.metadata)) return null;
    // All uploads have finished: every new shot uses the same current parent layout.
    const children = pieces.map((piece, index) => runtime.buildChild(piece, index, images[index], parent));
    return planStoryboardSplit(runtime.parentId, children, nodes, runtime.getConnections(), runtime.signal);
}

export function buildStoryboardPrompt(script: string, size: StoryboardGridSize): string {
    const total = size * size;
    return `生成一张 ${size}×${size} 的电影分镜宫格图，共 ${total} 格，所有格子等宽等高、紧密排列成正方形网格，格子之间不要留白边、不要分隔线、不要编号和文字。
从左到右、从上到下依次表现下面剧情中的连续镜头，景别和机位要有变化（远景、中景、近景、特写交替），同一角色在所有格子中的外貌、服装和画风保持一致。

剧情：
${script.trim()}`;
}
