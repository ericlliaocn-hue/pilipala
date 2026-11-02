package art.pilipala.api.job.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.job.application.CancelJob;
import art.pilipala.api.job.application.GetJob;
import art.pilipala.api.job.application.GetJobResult;
import art.pilipala.api.job.application.JobOutput;
import art.pilipala.api.job.application.JobResultView;
import art.pilipala.api.job.application.JobView;
import art.pilipala.api.job.application.ListUserJobs;
import art.pilipala.api.job.application.SubmitJob;
import art.pilipala.api.job.application.SubmitJobView;
import art.pilipala.api.job.infra.JobException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C 端任务接口的切片测试：信封、snake_case 字段、失败时的 error_code。
 *
 * <p>鉴权闸门（SaTokenConfig）在这个切片里被排除掉：会话由 mock 的
 * {@link AuthSessionService} 提供，只测控制器与信封这一层。
 */
@WebMvcTest(controllers = JobController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
class JobControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID HOLD_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private CurrentTenant currentTenant;

    @MockitoBean
    private SubmitJob submitJob;

    @MockitoBean
    private GetJob getJob;

    @MockitoBean
    private GetJobResult getJobResult;

    @MockitoBean
    private CancelJob cancelJob;

    @MockitoBean
    private ListUserJobs listUserJobs;

    @Test
    void submitReturnsTheQueuedJob() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(submitJob.submit(eq(USER), isNull(), eq("k1"), eq("image.generate"), eq("demo"), any(JsonNode.class),
                isNull()))
                .thenReturn(new SubmitJobView(JOB_ID.toString(), "queued", HOLD_ID.toString()));

        mockMvc.perform(post("/api/v1/jobs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.error_code").doesNotExist())
                .andExpect(jsonPath("$.data.job_id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.hold_id").value(HOLD_ID.toString()));
    }

    @Test
    void submitWithAnInsufficientWalletUsesTheEnvelope() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(submitJob.submit(eq(USER), isNull(), eq("k1"), any(), any(), any(JsonNode.class), any()))
                .thenThrow(JobException.walletInsufficient());

        mockMvc.perform(post("/api/v1/jobs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error_code").value("WALLET_INSUFFICIENT"))
                .andExpect(jsonPath("$.message").value("账户余额不足，请先充值。"));
    }

    @Test
    void submitFailureUsesTheEnvelopeWithAStableCode() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(submitJob.submit(eq(USER), isNull(), eq("k1"), any(), any(), any(JsonNode.class), any()))
                .thenThrow(JobException.idempotencyConflict());

        mockMvc.perform(post("/api/v1/jobs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error_code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("同一个幂等键对应了不同的请求内容。"));
    }

    @Test
    void queryOfAnotherTenantJobIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);
        when(getJob.get(eq(TENANT), eq(JOB_ID), any())).thenThrow(JobException.notFound());

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("任务不存在。"));
    }

    @Test
    void queryReturnsTheJobState() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);
        when(getJob.get(eq(TENANT), eq(JOB_ID), any()))
                .thenReturn(new JobView(JOB_ID.toString(), "queued", 1, 3, null, null, "2026-10-24T00:00:00Z"));

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.attempt").value(1))
                .andExpect(jsonPath("$.data.max_attempts").value(3))
                .andExpect(jsonPath("$.data.updated_at").value("2026-10-24T00:00:00Z"));
    }

    @Test
    void resultExposesObjectKeysWithASignedUrl() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);
        when(getJobResult.result(eq(TENANT), eq(JOB_ID), any()))
                .thenReturn(new JobResultView(JOB_ID.toString(), "succeeded",
                        List.of(new JobOutput("image", "img/a.png",
                                "http://127.0.0.1:18080/pilipala-dev/img/a.png?X-Tos-Algorithm=TOS4-HMAC-SHA256&X-Tos-Signature=abc")),
                        null, null));

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.outputs[0].type").value("image"))
                .andExpect(jsonPath("$.data.outputs[0].object_key").value("img/a.png"))
                .andExpect(jsonPath("$.data.outputs[0].url").value(
                        "http://127.0.0.1:18080/pilipala-dev/img/a.png?X-Tos-Algorithm=TOS4-HMAC-SHA256&X-Tos-Signature=abc"));
    }

    @Test
    void cancelReturnsTheCurrentState() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);
        when(cancelJob.cancel(eq(TENANT), eq(JOB_ID), any()))
                .thenReturn(new JobView(JOB_ID.toString(), "cancelled", 0, 3, "JOB_CANCELLED", "任务已取消。",
                        "2026-10-24T00:00:00Z"));

        mockMvc.perform(post("/api/v1/jobs/" + JOB_ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("cancelled"))
                .andExpect(jsonPath("$.data.error_code").value("JOB_CANCELLED"));
    }

    @Test
    void malformedJobIdIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);

        mockMvc.perform(get("/api/v1/jobs/not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("JOB_NOT_FOUND"));
    }

    /** WP9：Accept-Language 决定失败短句的语言。 */
    @Test
    void theMessageFollowsTheAcceptLanguageHeader() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);
        when(getJob.get(eq(TENANT), eq(JOB_ID), any())).thenThrow(JobException.notFound());

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID).header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The task does not exist."));
    }

    /** WP9：未捕获异常兜底成 PROVIDER_UNAVAILABLE，原文与 SQL 一律不外泄。 */
    @Test
    void anUncaughtRuntimeExceptionIsSanitized() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER)).thenReturn(TENANT);
        when(getJob.get(eq(TENANT), eq(JOB_ID), any()))
                .thenThrow(new IllegalStateException("relation \"users\" does not exist"));

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error_code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("生成服务暂时不可用，请稍后重试。"))
                .andExpect(content().string(not(containsString("does not exist"))));
    }
}
