package art.pilipala.api.admin.application;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

import art.pilipala.api.admin.application.AdminConsoleViews.SystemStatusView;
import art.pilipala.api.admin.infra.AdminConsoleMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 系统状态（GET /api/v1/admin/status）。
 *
 * <p>口径：API 自身能答这条请求就是 {@code up}。Worker 健康检查地址仓库里目前
 * 没有配置项 —— 不新加端口 / 地址约定，直接返回 {@code unknown}。NewAPI 只对
 * 已配置的 {@code pilipala.gateway.base-url} 做一次短连接：连不上是 {@code down}，
 * 没配置是 {@code unknown}，绝不回退去连 127.0.0.1。这里不重启任何进程。
 */
@Service
public class AdminSystemStatus {

    /** 健康探针的短连接超时（毫秒），只用于这一次探针，不是业务超时配置。 */
    private static final int PROBE_TIMEOUT_MS = 2000;

    private final AdminConsoleMapper mapper;
    private final String gatewayBaseUrl;

    public AdminSystemStatus(AdminConsoleMapper mapper,
            @Value("${pilipala.gateway.base-url:}") String gatewayBaseUrl) {
        this.mapper = mapper;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public SystemStatusView status() {
        return new SystemStatusView("up", "unknown", probeNewApi(),
                mapper.countPendingOutbox(), mapper.countRunningJobs());
    }

    /** 已配置才探：任意 HTTP 响应都算连通（网关活着），IO 失败算 down。 */
    private String probeNewApi() {
        if (gatewayBaseUrl == null || gatewayBaseUrl.isBlank()) {
            return "unknown";
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(gatewayBaseUrl.trim()).toURL().openConnection();
            connection.setConnectTimeout(PROBE_TIMEOUT_MS);
            connection.setReadTimeout(PROBE_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            return "up";
        } catch (IOException | IllegalArgumentException e) {
            return "down";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
