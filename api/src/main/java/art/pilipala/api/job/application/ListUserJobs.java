package art.pilipala.api.job.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.billing.application.JobCostPolicy;
import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.storage.TosUrlSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * C 端生图历史：按创建时间倒序返回当前租户的任务。
 *
 * <p>WP10 起换浏览器登录也能看到之前生成过的图片：结果读地址在这里现签
 * （ADR 0003），错误文案按当前 locale 解析（ADR 0005）。
 */
@Service
public class ListUserJobs {

    private static final Logger log = LoggerFactory.getLogger(ListUserJobs.class);

    /** 列表最多返回多少条。 */
    public static final int MAX_LIMIT = 100;

    private final JobRepository jobs;
    private final ObjectMapper mapper;
    private final HoldSettlement holds;
    private final JobCostPolicy costPolicy;
    private final TosUrlSigner tosUrlSigner;
    private final I18nMessageService i18n;

    public ListUserJobs(JobRepository jobs, ObjectMapper mapper, HoldSettlement holds, JobCostPolicy costPolicy,
            TosUrlSigner tosUrlSigner, I18nMessageService i18n) {
        this.jobs = jobs;
        this.mapper = mapper;
        this.holds = holds;
        this.costPolicy = costPolicy;
        this.tosUrlSigner = tosUrlSigner;
        this.i18n = i18n;
    }

    /**
     * 当前租户的生图历史。
     *
     * @param limit 最多返回多少条（1..100）
     * @param locale 当前语言，用于解析友好错误文案
     */
    public List<JobHistoryItemView> list(UUID tenantId, int limit, String locale) {
        int clamped = Math.max(1, Math.min(MAX_LIMIT, limit));
        List<JobHistoryItemView> items = new ArrayList<>();
        for (Job job : jobs.findByTenantOrderByCreatedAtDesc(tenantId, clamped)) {
            items.add(toItem(job, locale));
        }
        return items;
    }

    private JobHistoryItemView toItem(Job job, String locale) {
        holds.settle(job.holdId(), job.status());
        String errorCode = job.errorCode();
        return new JobHistoryItemView(
                job.id().toString(),
                job.status().value(),
                job.capability(),
                job.model(),
                promptOf(job.paramsJson()),
                costPolicy.costOf(job.capability(), job.model()),
                job.status() == JobStatus.SUCCEEDED ? resultUrlsOf(job) : List.of(),
                errorCode,
                errorCode == null ? null : i18n.resolve(errorCode, locale),
                job.createdAt(),
                job.updatedAt(),
                job.status().isTerminal() ? job.updatedAt() : null);
    }

    /** 提示词从已物化的 params 里取；取不到就是空串，不回显原始请求体。 */
    private String promptOf(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = mapper.readTree(paramsJson);
            return node.isObject() ? node.path("prompt").asText("") : "";
        } catch (JsonProcessingException e) {
            log.warn("params 解析失败");
            return "";
        }
    }

    /** 每个对象键现签一个短期读地址；非法对象键只跳过这一条。 */
    private List<String> resultUrlsOf(Job job) {
        String json = job.resultObjectKeysJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> urls = new ArrayList<>();
            mapper.readTree(json).forEach(item -> {
                String objectKey = item.asText();
                if (objectKey == null || objectKey.isBlank()) {
                    return;
                }
                try {
                    urls.add(tosUrlSigner.signGetUrl(objectKey));
                } catch (IllegalArgumentException e) {
                    log.warn("跳过非法 TOS 对象键 job={} key={}", job.id(), objectKey);
                }
            });
            return urls;
        } catch (JsonProcessingException e) {
            log.warn("result_object_keys 解析失败 job={}", job.id());
            return List.of();
        }
    }
}
