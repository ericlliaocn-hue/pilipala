package art.pilipala.api.job.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobOutputs;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.storage.TosUrlSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 查询任务结果。
 *
 * <p>outputs 来自 {@code job.result_object_keys}；每个对象键在读取时现签一个
 * 火山 TOS 的短期预签名读地址（默认 TTL 900 秒），签名地址不落库、不进 MQ 消息。
 *
 * <p>任务没成功时 outputs 为空数组：不给未完成或失败的任务签发读地址。
 */
@Service
public class GetJobResult {

    private static final Logger log = LoggerFactory.getLogger(GetJobResult.class);

    private final JobRepository jobs;
    private final ObjectMapper mapper;
    private final HoldSettlement holds;
    private final TosUrlSigner tosUrlSigner;
    private final I18nMessageService i18n;

    public GetJobResult(JobRepository jobs, ObjectMapper mapper, HoldSettlement holds, TosUrlSigner tosUrlSigner,
            I18nMessageService i18n) {
        this.jobs = jobs;
        this.mapper = mapper;
        this.holds = holds;
        this.tosUrlSigner = tosUrlSigner;
        this.i18n = i18n;
    }

    public JobResultView result(UUID tenantId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndTenant(jobId, tenantId).orElseThrow(JobException::notFound);
        holds.settle(job.holdId(), job.status());
        List<JobOutput> outputs = job.status() == JobStatus.SUCCEEDED ? outputs(job) : List.of();
        String errorCode = job.errorCode();
        return new JobResultView(
                job.id().toString(),
                job.status().value(),
                outputs,
                errorCode,
                errorCode == null ? null : i18n.resolve(errorCode, locale));
    }

    private List<JobOutput> outputs(Job job) {
        String json = job.resultObjectKeysJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<JobOutput> outputs = new ArrayList<>();
            mapper.readTree(json).forEach(item -> {
                String objectKey = item.asText();
                if (objectKey == null || objectKey.isBlank()) {
                    return;
                }
                try {
                    // 视频任务的产物类型是 "video"，图片任务仍是 "image"（WP12）。
                    outputs.add(new JobOutput(JobOutputs.outputTypeOf(job.capability(), objectKey), objectKey,
                            tosUrlSigner.signGetUrl(objectKey)));
                } catch (IllegalArgumentException e) {
                    // 非法对象键只跳过这一条：不让一条脏数据打挂整个 result 响应。
                    log.warn("跳过非法 TOS 对象键 job={} key={}", job.id(), objectKey);
                }
            });
            return outputs;
        } catch (JsonProcessingException e) {
            log.warn("result_object_keys 解析失败 job={}", job.id());
            return List.of();
        }
    }
}
