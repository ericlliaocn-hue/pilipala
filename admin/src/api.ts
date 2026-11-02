export const TOKEN_KEY = "pilipala:admin_token";
export const TOKEN_HEADER = "pilipala-token";

export type Envelope<T> = { ok: boolean; data: T | null; error_code: string | null; message: string | null };

export class ApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ApiError";
  }
}

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* Private browsing can disable localStorage. */ }
}

const mutationListeners = new Set<() => void>();

export function subscribeMutations(listener: () => void) {
  mutationListeners.add(listener);
  return () => { mutationListeners.delete(listener); };
}

export async function request<T>(path: string, init: RequestInit = {}, token = getToken()): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path.startsWith("/") ? path : `/${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { [TOKEN_HEADER]: token } : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new ApiError("请求未能完成，请检查网络或服务状态");
  }
  let payload: unknown;
  try { payload = await response.json(); } catch { throw new ApiError("服务暂时不可用，请稍后重试"); }
  if (payload && typeof payload === "object" && "ok" in payload && typeof payload.ok === "boolean") {
    const envelope = payload as Envelope<T>;
    if (response.ok && envelope.ok) {
      const method = (init.method ?? "GET").toUpperCase();
      if (method !== "GET" && method !== "HEAD" && path.startsWith("/api/v1/admin")) {
        mutationListeners.forEach((listener) => listener());
      }
      return envelope.data as T;
    }
    throw new ApiError(envelope.message || "操作失败，请稍后重试");
  }
  throw new ApiError("服务暂时不可用，请稍后重试");
}

export function post<T>(path: string, body?: unknown) {
  return request<T>(path, { method: "POST", ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
}

export function put<T>(path: string, body: unknown) {
  return request<T>(path, { method: "PUT", body: JSON.stringify(body) });
}

export function patch<T>(path: string, body: unknown) {
  return request<T>(path, { method: "PATCH", body: JSON.stringify(body) });
}

export async function downloadCsv(path: string, filename: string) {
  let response: Response;
  try {
    response = await fetch(path, { headers: { Accept: "text/csv, application/json", ...(getToken() ? { [TOKEN_HEADER]: getToken()! } : {}) } });
  } catch { throw new ApiError("导出请求未能完成，请检查网络或服务状态"); }
  const text = await response.text();
  if (response.ok && response.headers.get("content-type")?.includes("text/csv")) {
    const url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
    return;
  }
  try {
    const payload = JSON.parse(text) as Envelope<unknown>;
    throw new ApiError(payload.message || "导出失败，请稍后重试");
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError("导出失败，请稍后重试");
  }
}

export const qs = (params: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== "") query.set(key, String(value)); });
  return query.size ? `?${query.toString()}` : "";
};
