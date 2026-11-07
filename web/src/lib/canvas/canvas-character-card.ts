import { NODE_DEFAULT_SIZE } from "@/constant/canvas";
import { getGroupWrapRect } from "@/lib/canvas/canvas-node-geometry";
import { createCanvasNode } from "@/lib/canvas/canvas-node-factory";
import { fitNodeSize } from "@/lib/canvas/canvas-node-size";
import { CanvasNodeType, type CanvasNodeData, type CanvasNodeMetadata } from "@/types/canvas";

export type CharacterCardInput = {
    name: string;
    description?: string;
};

export type CharacterCardImageSource = {
    content: string;
    storageKey?: string;
    naturalWidth: number;
    naturalHeight: number;
    bytes: number;
    mimeType: string;
};

export type CharacterCardNodes = {
    group: CanvasNodeData;
    image: CanvasNodeData;
    text: CanvasNodeData;
};

const CARD_GAP = 96;
const CARD_IMAGE_MAX = 340;
const CARD_TEXT_GAP = 24;

export function normalizeCharacterCardInput(input: CharacterCardInput) {
    const name = input.name.trim();
    if (!name) return null;
    return { name, description: (input.description || "").trim() };
}

export function resolveCharacterCardImage(node: CanvasNodeData | null | undefined): CharacterCardImageSource | null {
    if (!node || node.type !== CanvasNodeType.Image) return null;
    const metadata = node.metadata;
    const images = metadata?.images || [];
    const primaryImageId = metadata?.primaryImageId || images[0]?.id;
    const primary = images.find((image) => image.id === primaryImageId);
    const primaryContent = primary?.content;
    const content = primaryContent || metadata?.content;
    if (!content) return null;
    return {
        content,
        storageKey: (primaryContent ? primary?.storageKey : undefined) || metadata?.storageKey,
        naturalWidth: (primaryContent ? primary?.naturalWidth : 0) || metadata?.naturalWidth || node.width,
        naturalHeight: (primaryContent ? primary?.naturalHeight : 0) || metadata?.naturalHeight || node.height,
        bytes: (primaryContent ? primary?.bytes : 0) || metadata?.bytes || 0,
        mimeType: (primaryContent ? primary?.mimeType : undefined) || metadata?.mimeType || "image/png",
    };
}

export function buildCharacterCardNodes(sourceNode: CanvasNodeData | null | undefined, input: CharacterCardInput): CharacterCardNodes | null {
    const normalized = normalizeCharacterCardInput(input);
    const source = resolveCharacterCardImage(sourceNode);
    if (!normalized || !sourceNode || !source) return null;

    const size = fitNodeSize(source.naturalWidth, source.naturalHeight, CARD_IMAGE_MAX, CARD_IMAGE_MAX);
    const textSpec = NODE_DEFAULT_SIZE[CanvasNodeType.Text];
    const left = sourceNode.position.x + sourceNode.width + CARD_GAP;
    const top = sourceNode.position.y;
    const imageMetadata: CanvasNodeMetadata = {
        content: source.content,
        ...(source.storageKey ? { storageKey: source.storageKey } : {}),
        status: "success",
        naturalWidth: source.naturalWidth,
        naturalHeight: source.naturalHeight,
        bytes: source.bytes,
        mimeType: source.mimeType,
    };
    const image: CanvasNodeData = {
        ...createCanvasNode(CanvasNodeType.Image, { x: left + size.width / 2, y: top + size.height / 2 }, imageMetadata),
        title: normalized.name,
        position: { x: left, y: top },
        width: size.width,
        height: size.height,
    };
    const content = normalized.description ? `${normalized.name}\n\n${normalized.description}` : normalized.name;
    const text: CanvasNodeData = {
        ...createCanvasNode(CanvasNodeType.Text, { x: left + size.width + CARD_TEXT_GAP + textSpec.width / 2, y: top + textSpec.height / 2 }, { content, status: "success", fontSize: 14 }),
        title: normalized.name,
    };
    const rect = getGroupWrapRect([image, text]);
    const group: CanvasNodeData = {
        ...createCanvasNode(CanvasNodeType.Group, { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }),
        title: normalized.name,
        position: { x: rect.x, y: rect.y },
        width: rect.width,
        height: rect.height,
    };

    return {
        group,
        image: { ...image, metadata: { ...image.metadata, groupId: group.id } },
        text: { ...text, metadata: { ...text.metadata, groupId: group.id } },
    };
}
