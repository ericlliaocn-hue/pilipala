# 竞争路线进度（M5–M8）

本页是 [MILESTONES.md](./MILESTONES.md) M5–M8 的逐步执行清单与进度，方向见 [VISION.md](./VISION.md)（AI 短剧 / 漫剧）。每完成一步就更新本页，不依赖聊天记录。

## 执行流程

1. Claude 为该步写 brief（`feature-briefs/`，从 `main` 拉分支）。
2. MiniMax（M3.1-Flash）在独立 worktree 按 brief 实现。
3. Claude 验收：读 diff、跑 api / worker / web / admin 测试与构建、必要时起本地服务并真实调用上游。
4. 通过后合并进 `main` 并推送；不通过则带具体问题返工。
5. 更新本页状态，进入下一步。

遇到以下情况必须停下来问人：超时 / 重试 / 大小上限等边界值、定价与积分、需要开通的 Key 或账号、资质备案、生产部署。

## 状态

| 步骤 | 里程碑 | 内容 | 实现 | 状态 |
| --- | --- | --- | --- | --- |
| 0.1 | — | 理顺分支：wp21 + admin-console 快进进 `main`，newapi 分支确认已推送，关闭过时 PR #17 | Claude | ✅ 完成 |
| 0.2 | — | 重写 VISION / MILESTONES，新增本页 | Claude | ✅ 完成 |
| 0.3 | M5 | 生成图片 / 视频的 AI 显式标识 + 隐式元数据标识 | MiniMax | 待开始 |
| 0.4 | M5 | 提示词与生成结果内容审核（需开通审核服务账号） | MiniMax | 待开始 |
| 0.5 | M5 | CI 发布替代手工 tgz；修 web 类型检查 | MiniMax | 待开始 |
| 1.1 | M6 | 网关扩模型：Seedream / 可灵 / Wan / TTS / 配乐（需渠道 Key） | MiniMax | 待开始 |
| 1.2 | M6 | 画布剧本 / 分镜节点 | MiniMax | 待开始 |
| 1.3 | M6 | 9 / 25 宫格分镜批量出图 | MiniMax | 待开始 |
| 1.4 | M6 | 角色 / 主体一致性：角色卡、三视图、参考注入 | MiniMax | 待开始 |
| 1.5 | M6 | 首尾帧、画布内视频裁剪拼接 | MiniMax | 待开始 |
| 1.6 | M6 | 节点打组、保存、一键重跑 | MiniMax | 待开始 |
| 2.1 | M7 | 托管 Agent：剧本 → 分镜 → 镜头自动上画布（分多步） | MiniMax | 待开始 |
| 2.2 | M7 | 工作流模板与「克隆同款」 | MiniMax | 待开始 |
| 3.x | M8 | 模板广场 / 团队协作 / 开放 API 与 Skill（进入前重新拆分） | — | 未拆分 |

## 已知遗留

- web `bun run typecheck` 有 4 处 antd `styles` 类型错误（`auth-modal.tsx`、`auth-session-menus.tsx`、`model-script-editor.tsx`），构建不受影响；在 0.5 修复。
- worker 仍保留 Anytoken 直连视频轮询路径（`AnytokenGatewayAdapter`，2s × 90 次上限）；已上架 Seedance 产品走 NewAPI，不经过该路径。后续确认无用后清理。
- 公开文档 `docs/content/docs/overview/features.mdx` 仍按上游「浏览器直连」口径描述，需在合适步骤按托管产品改写。
