import { useEffect, useState } from "react";
import { Button, Modal, Segmented } from "antd";
import { LayoutGrid } from "lucide-react";
import { useTranslation } from "react-i18next";

import { canvasThemes } from "@/lib/canvas-theme";
import { useThemeStore } from "@/stores/use-theme-store";

export type CanvasStoryboardSize = 3 | 5;

const defaultSize: CanvasStoryboardSize = 3;

export function CanvasStoryboardDialog({ open, onClose, onConfirm }: { open: boolean; onClose: () => void; onConfirm: (size: CanvasStoryboardSize) => void }) {
    const { t } = useTranslation();
    const theme = canvasThemes[useThemeStore((state) => state.theme)];
    const [size, setSize] = useState<CanvasStoryboardSize>(defaultSize);

    useEffect(() => {
        if (open) setSize(defaultSize);
    }, [open]);

    return (
        <Modal title={null} open={open} onCancel={onClose} footer={null} width={460} centered destroyOnHidden>
            <div className="space-y-5" data-canvas-no-zoom>
                <div>
                    <h2 className="text-xl font-semibold">{t("canvas.storyboard.title")}</h2>
                    <p className="mt-1 text-sm opacity-60">{t("canvas.storyboard.label")}</p>
                </div>
                <Segmented
                    block
                    value={size}
                    options={[
                        { label: t("canvas.storyboard.nine"), value: 3 },
                        { label: t("canvas.storyboard.twentyFive"), value: 5 },
                    ]}
                    onChange={(value) => setSize(value as CanvasStoryboardSize)}
                />
                <div className="rounded-xl border px-4 py-3 text-sm leading-5" style={{ borderColor: theme.node.stroke, color: theme.node.muted }}>
                    {t("canvas.storyboard.hint", { count: size * size })}
                </div>
                <div className="flex justify-end">
                    <Button type="primary" size="large" icon={<LayoutGrid className="size-4" />} onClick={() => onConfirm(size)}>
                        {t("canvas.storyboard.confirm")}
                    </Button>
                </div>
            </div>
        </Modal>
    );
}
