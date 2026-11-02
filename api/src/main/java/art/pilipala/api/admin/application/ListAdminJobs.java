package art.pilipala.api.admin.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminJobRepository;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

/**
 * 用例：查任务列表（含步骤与上游留痕），供运维排障。
 *
 * <p>上游原文（raw_status / raw_body）只在这里出现（ADR 0005），
 * 而且只在调用者还持有 {@code job.provider_raw} 时给。
 */
@Service
public class ListAdminJobs {

    private final AdminJobRepository jobs;
    private final AdminJobViewAssembler assembler;

    public ListAdminJobs(AdminJobRepository jobs, AdminJobViewAssembler assembler) {
        this.jobs = jobs;
        this.assembler = assembler;
    }

    /**
     * @param status 任务状态；为空时不过滤，非法值按参数不合法处理
     * @param userId 用户；为空时不过滤
     * @param limit 单页上限，内部收敛到 1..200
     * @param includeProviderRaw 调用者是否还有 {@code job.provider_raw}
     */
    public List<AdminJobView> list(String status, UUID userId, int limit, boolean includeProviderRaw) {
        return assembler.assembleAll(
                jobs.findJobs(normalizeStatus(status), userId, AdminLimits.limit(limit)), includeProviderRaw);
    }

    /** 状态过滤值必须是合法的 job 状态，否则 {@code VALIDATION_ERROR}。 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return JobStatus.of(status).value();
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }
}
