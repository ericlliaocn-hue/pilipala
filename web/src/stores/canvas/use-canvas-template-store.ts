import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { nanoid } from "nanoid";

import type { CanvasWorkflowSnapshot } from "@/lib/canvas/canvas-group-workflow";
import { localForageStorage } from "@/lib/localforage-storage";
import type { CanvasConnection, CanvasNodeData } from "@/types/canvas";

export type CanvasTemplate = {
    id: string;
    title: string;
    nodes: CanvasNodeData[];
    connections: CanvasConnection[];
    createdAt: string;
};

type CanvasTemplateStore = {
    templates: CanvasTemplate[];
    addTemplate: (title: string, snapshot: CanvasWorkflowSnapshot) => string;
    removeTemplate: (id: string) => void;
};

export const useCanvasTemplateStore = create<CanvasTemplateStore>()(
    persist(
        (set) => ({
            templates: [],
            addTemplate: (title, snapshot) => {
                const id = nanoid();
                const template: CanvasTemplate = { id, title, nodes: snapshot.nodes, connections: snapshot.connections, createdAt: new Date().toISOString() };
                set((state) => ({ templates: [template, ...state.templates] }));
                return id;
            },
            removeTemplate: (id) => set((state) => ({ templates: state.templates.filter((template) => template.id !== id) })),
        }),
        {
            name: "pilipala:canvas_template_store",
            storage: createJSONStorage(() => localForageStorage),
        },
    ),
);
