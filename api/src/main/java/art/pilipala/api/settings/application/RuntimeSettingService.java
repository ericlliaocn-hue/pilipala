package art.pilipala.api.settings.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.settings.domain.AuthRuntimeKeys;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper.AppRuntimeSettingRow;
import org.springframework.stereotype.Service;

@Service
public class RuntimeSettingService {

    private final AppRuntimeSettingMapper mapper;

    public RuntimeSettingService(AppRuntimeSettingMapper mapper) {
        this.mapper = mapper;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        AppRuntimeSettingRow row = mapper.findByKey(key);
        if (row == null || row.value() == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(row.value().trim());
    }

    /**
     * 读字符串配置。缺行或 value 为 null 时回退默认值；空字符串是合法存值（例如隐藏角标）。
     */
    public String getString(String key, String defaultValue) {
        AppRuntimeSettingRow row = mapper.findByKey(key);
        if (row == null || row.value() == null) {
            return defaultValue;
        }
        return row.value();
    }

    public Map<String, String> listAll() {
        List<AppRuntimeSettingRow> rows = mapper.findAll();
        Map<String, String> out = new LinkedHashMap<>();
        for (AppRuntimeSettingRow row : rows) {
            out.put(row.key(), row.value());
        }
        return out;
    }

    /**
     * 后台运行时开关的唯一写入口，收窄到 brief 第 2 节的两个 auth 键：
     * 其它键、以及不是 {@code true} / {@code false} 的值，一律
     * {@code VALIDATION_ERROR}，不再静默跳过或把任意字符串压成 false。
     * 先全量校验再落库，避免一半写进去了才发现有非法键。
     */
    public Map<String, String> patch(Map<String, String> updates) {
        if (updates == null) {
            throw JobException.validation();
        }
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            if (!AuthRuntimeKeys.KNOWN_KEYS.contains(key)) {
                throw JobException.validation();
            }
            changed.put(key, normalizeBoolean(entry.getValue()));
        }
        changed.forEach(mapper::upsert);
        return changed;
    }

    private static String normalizeBoolean(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) {
            throw JobException.validation();
        }
        return value;
    }
}
