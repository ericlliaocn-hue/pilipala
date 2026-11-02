/**
 * pilipala 后端 API 的统一出口：认证、任务、云端持久化都从这里取。
 *
 * <p>具体实现分散在 ./api/ 下按领域拆分，这里只做再导出，避免业务代码到处找文件。
 */
export * from "./api/pilipala-client";
export * from "./api/pilipala-auth";
export * from "./api/pilipala-jobs";
export * from "./api/pilipala-cloud";
