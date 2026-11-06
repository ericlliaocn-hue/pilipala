# Feature brief：CI 校验流水线 + web 类型检查归零（ROADMAP 0.5）

- 日期：2026-11-06
- 作者：pilipala engineering（Claude）
- 对应 WP / 里程碑：M5 / ROADMAP 0.5
- 状态：已验收

## 背景

`.github/workflows/` 现有 4 个工作流都只在推 `v*` tag 时运行，`main` 与 PR 没有任何自动校验；合并前全靠人工在本地跑测试。另外 `web` 的 `bun run typecheck` 当前有 4 处报错，都是 antd 6 Modal / Popover 的 `styles` 语义键类型不匹配：

- `web/src/components/auth/auth-modal.tsx`（Modal `styles.content`）
- `web/src/components/layout/auth-session-menus.tsx` 两处（Popover `styles={popoverStyles}`，对象里用了 `body`）
- `web/src/components/layout/model-script-editor.tsx`（Modal `styles.content`）

注意：`web/bun.lock` 锁定 `antd@6.4.2`，本地 `node_modules` 可能是别的版本。**以 `bun install --frozen-lockfile` 之后的类型为准**修复。

## 目标

1. 每个指向 `main` 的 PR 和每次推送 `main`，GitHub Actions 自动跑 api / worker / web / admin 的测试、web 类型检查与两个前端的构建，任一失败则工作流失败。
2. 在 frozen lockfile 安装后，`cd web && bun run typecheck` 0 错误。
3. 修类型时视觉效果不变：原来写在 `content` / `body` 上的样式，迁到 antd 6 对应的语义键上（在 `node_modules/antd/es/modal/*.d.ts`、`popover/*.d.ts` 里查准确键名），不删样式。

## 范围

- 新增 `.github/workflows/ci.yml`。
- 修改上面列出的 3 个 tsx 文件，只改 `styles` 相关的键。
- 如有必要，修改 `docs/engineering/ROADMAP.md` 中 0.5 的状态与「已知遗留」第一条（类型错误）。

## 非范围

- 不改已有的 4 个 tag 工作流，不做部署、不打包产物、不推镜像、不碰生产机。
- 不升级或降级任何依赖，不改 `bun.lock`、`pom.xml`。
- 不顺手修 lint、格式、其他 warning，不重构这 3 个组件。
- 不改 `docs/content/**`。

## ci.yml 要求

- 名称 `ci`；触发：`pull_request`（分支 `main`）与 `push`（分支 `main`）。
- `permissions: contents: read`。
- 不设置 `timeout-minutes`、不加重试（沿用 GitHub 默认）。
- 三个并行 job：
  1. `java`：`ubuntu-latest`；`actions/checkout@v4`；`actions/setup-java@v4`（`distribution: temurin`，`java-version: '21'`，`cache: maven`）；运行 `./mvnw -B test`（根目录聚合 api + worker）。
  2. `web`：`actions/checkout@v4`；`oven-sh/setup-bun@v2`（`bun-version: 1.4.4`；`web/bun.lock` 是 lockfileVersion 2，bun 1.3.x 无法解析，frozen 安装会失败）；在 `web/` 下依次 `bun install --frozen-lockfile`、`bun run typecheck`、`bun test`、`bun run build`。
  3. `admin`：同上 setup；在 `admin/` 下 `bun install --frozen-lockfile`、`bun test`、`bun run build`。
- 若 `web` 构建依赖 Node（例如 `tsx` 脚本），加 `actions/setup-node@v4`（`node-version: '22'`）；不需要就不加。
- 不写任何密钥、不引用 `secrets.*`。

## 数据表字段

无。

## API

无。

## 状态机

无。

## 验收

- [ ] `.github/workflows/ci.yml` 符合上面要求，YAML 合法。
- [ ] 本地：`cd web && rm -rf node_modules && bun install --frozen-lockfile && bun run typecheck` 0 错误；`bun test`、`bun run build` 通过。
- [ ] 本地：`cd admin && bun install --frozen-lockfile && bun test && bun run build` 通过。
- [ ] 本地：根目录 `./mvnw -B -q test` 通过。
- [ ] `git diff` 只包含范围内文件。
- [ ] 3 个组件的样式没有丢：被迁移的样式属性在新键下逐项保留。

## 非目标

- CI 不做部署，不替代人工发布流程（发布自动化另开步骤，需先确认生产部署方式）。

## 给 MiniMax 的额外约束

- 先读 `AGENTS.md` 与本文件；遵守 `docs/engineering/TECH_STACK.md`。
- 本任务**要求**你在本地跑上面验收里的命令自检（覆盖 AGENTS.md「不跑构建」的默认），并把每条命令的结果贴进最终汇报。
- 在你的工作分支上提交（一个 commit 即可，提交信息用中文，形如 `ci: 新增 main/PR 校验流水线并修复 web 类型检查`），**不要 push，不要 merge，不要开 PR**。
- 最终汇报包含：分支名、commit SHA、改动文件清单、每个样式键从什么改成什么、自检命令结果。
