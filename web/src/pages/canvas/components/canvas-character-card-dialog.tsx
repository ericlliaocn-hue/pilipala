import { useEffect } from "react";
import { Button, Form, Input, Modal } from "antd";
import { UserRound } from "lucide-react";
import { useTranslation } from "react-i18next";

import { canvasThemes } from "@/lib/canvas-theme";
import { resolveCharacterCardImage, type CharacterCardInput } from "@/lib/canvas/canvas-character-card";
import { useThemeStore } from "@/stores/use-theme-store";
import type { CanvasNodeData } from "@/types/canvas";

export function CanvasCharacterCardDialog({ open, sourceNode, onClose, onSubmit }: { open: boolean; sourceNode: CanvasNodeData | null; onClose: () => void; onSubmit: (input: CharacterCardInput) => void }) {
    const { t } = useTranslation();
    const theme = canvasThemes[useThemeStore((state) => state.theme)];
    const [form] = Form.useForm<CharacterCardInput>();
    const preview = resolveCharacterCardImage(sourceNode);

    useEffect(() => {
        if (open) form.resetFields();
    }, [form, open, sourceNode?.id]);

    return (
        <Modal title={null} open={open} onCancel={onClose} footer={null} width={500} centered destroyOnHidden>
            <div className="space-y-5" data-canvas-no-zoom>
                <div className="flex items-start gap-4">
                    {preview ? <img src={preview.content} alt="" className="size-16 shrink-0 rounded-xl border object-cover" style={{ borderColor: theme.node.stroke }} /> : null}
                    <div className="min-w-0">
                        <h2 className="text-xl font-semibold">{t("canvas.characterCard.title")}</h2>
                        <p className="mt-1 text-sm leading-5 opacity-60">{t("canvas.characterCard.description")}</p>
                    </div>
                </div>
                <Form form={form} layout="vertical" requiredMark={false} onFinish={(values) => onSubmit({ name: values.name, description: values.description })}>
                    <Form.Item name="name" label={t("canvas.characterCard.name")} rules={[{ required: true, whitespace: true, message: t("canvas.characterCard.nameRequired") }]}>
                        <Input autoFocus placeholder={t("canvas.characterCard.namePlaceholder")} />
                    </Form.Item>
                    <Form.Item name="description" label={t("canvas.characterCard.descriptionLabel")}>
                        <Input.TextArea rows={4} placeholder={t("canvas.characterCard.descriptionPlaceholder")} />
                    </Form.Item>
                    <div className="rounded-xl border px-4 py-3 text-sm leading-5" style={{ borderColor: theme.node.stroke, color: theme.node.muted }}>
                        {t("canvas.characterCard.hint")}
                    </div>
                    <div className="mt-5 flex justify-end gap-2">
                        <Button onClick={onClose}>{t("common.cancel")}</Button>
                        <Button type="primary" htmlType="submit" icon={<UserRound className="size-4" />}>
                            {t("canvas.characterCard.submit")}
                        </Button>
                    </div>
                </Form>
            </div>
        </Modal>
    );
}
