// __APP_VERSION__ 由 vite 的 define 注入；单测环境没有 vite，用 typeof 兜底。
export const APP_VERSION = typeof __APP_VERSION__ === "string" ? __APP_VERSION__ : "dev";

export const DOCS_URL = import.meta.env.VITE_DOC_URL || "https://docs.canvas.best";

// 独立运维后台：创作站不再内嵌后台页面，只给有平台权限的人一个入口。
export const ADMIN_CONSOLE_URL = import.meta.env.VITE_ADMIN_CONSOLE_URL || "https://admin.pilipala.art";

// pilipala 后端网关根路径。默认空串 = 同源，开发时由 vite 的 /api 代理转发到本地 api。
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");

// Official plugin registry URL: CI publishes to plugins-dist for jsDelivr delivery; an environment variable may override it for self-hosting.
export const PLUGIN_REGISTRY_URL = import.meta.env.VITE_PLUGIN_REGISTRY_URL || "https://cdn.jsdelivr.net/gh/basketikun/infinite-canvas@plugins-dist/official-plugins.json";
