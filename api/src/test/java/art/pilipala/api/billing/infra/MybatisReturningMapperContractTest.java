package art.pilipala.api.billing.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import art.pilipala.api.admin.infra.AdminJobMapper;
import art.pilipala.api.admin.infra.AdminUserMapper;
import art.pilipala.api.admin.infra.ModelDisplayConfigMapper;
import art.pilipala.api.auth.infra.UserMapper;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.i18n.infra.I18nMessageMapper;
import art.pilipala.api.job.infra.IdempotencyMapper;
import art.pilipala.api.job.infra.JobMapper;
import art.pilipala.api.job.infra.JobStepMapper;
import art.pilipala.api.job.infra.OutboxMapper;
import art.pilipala.api.job.infra.ProviderPollMapper;
import art.pilipala.api.job.infra.ProviderTaskMapper;
import art.pilipala.api.project.infra.CanvasProjectMapper;
import art.pilipala.api.project.infra.UserAssetMapper;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper;

class MybatisReturningMapperContractTest {

    private static final List<Class<?>> MAPPERS = List.of(
            RechargeOrderMapper.class,
            WalletHoldMapper.class,
            WalletLedgerMapper.class,
            WalletMapper.class,
            UserMapper.class,
            ModelDisplayConfigMapper.class,
            AppRuntimeSettingMapper.class,
            AdminJobMapper.class,
            AdminUserMapper.class,
            IdempotencyMapper.class,
            JobMapper.class,
            JobStepMapper.class,
            OutboxMapper.class,
            ProviderTaskMapper.class,
            ProviderPollMapper.class,
            CanvasProjectMapper.class,
            UserAssetMapper.class,
            I18nMessageMapper.class);

    @Test
    void insertCreatedUsesSelectSoReturningCanMapRechargeOrder() throws Exception {
        Method method = RechargeOrderMapper.class.getMethod(
                "insertCreated",
                java.util.UUID.class,
                java.util.UUID.class,
                String.class,
                String.class,
                long.class,
                long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Long.class,
                String.class,
                long.class,
                String.class,
                Long.class);
        assertThat(method.getReturnType()).isEqualTo(RechargeOrder.class);
        assertThat(method.isAnnotationPresent(Select.class)).isTrue();
        assertThat(method.isAnnotationPresent(Insert.class)).isFalse();
        assertThat(sqlOf(method)).containsIgnoringCase("RETURNING");
    }

    @Test
    void returningStatementsDoNotUseInsertAnnotation() {
        int returningCount = 0;
        for (Class<?> mapper : MAPPERS) {
            for (Method method : mapper.getDeclaredMethods()) {
                String sql = sqlOf(method);
                if (sql == null || !sql.toUpperCase().contains("RETURNING")) {
                    continue;
                }
                returningCount += 1;
                assertThat(method.isAnnotationPresent(Insert.class))
                        .as("%s.%s must not use @Insert with RETURNING", mapper.getSimpleName(), method.getName())
                        .isFalse();
                assertThat(method.isAnnotationPresent(Select.class) || method.isAnnotationPresent(Update.class))
                        .as("%s.%s RETURNING must use @Select or @Update", mapper.getSimpleName(), method.getName())
                        .isTrue();
                Class<?> returnType = method.getReturnType();
                assertThat(returnType == int.class || returnType == void.class || returnType == boolean.class)
                        .as("%s.%s RETURNING must map a row, not %s", mapper.getSimpleName(), method.getName(), returnType.getSimpleName())
                        .isFalse();
            }
        }
        assertThat(returningCount).isGreaterThanOrEqualTo(10);
    }

    /**
     * 步骤 3 的红线：钱包只按租户定位。
     *
     * <p>{@code wallet.user_id} 已经不在表里，所以任何一条 wallet SQL 都不该再出现它；
     * 冻结单则要同时带 {@code tenant_id}（钱是谁的）和 {@code user_id}（谁按的操作）。
     */
    @Test
    void walletStatementsAreTenantKeyed() throws Exception {
        assertThat(WalletRepository.class.getMethods())
                .noneMatch(method -> method.getName().equals("findByUserId") || method.getName().equals("insertIfAbsent"));
        assertThat(WalletRepository.class.getMethods()).extracting(Method::getName)
                .contains("findByTenantId", "openForTenant");

        for (Method method : WalletMapper.class.getDeclaredMethods()) {
            assertThat(sqlOf(method))
                    .as("WalletMapper.%s 不该再按 wallet.user_id 读写", method.getName())
                    .doesNotContainIgnoringCase("user_id");
        }

        String holdInsert = sqlOf(WalletHoldMapper.class.getMethod(
                "insert", java.util.UUID.class, java.util.UUID.class, java.util.UUID.class, long.class));
        assertThat(holdInsert).contains("tenant_id").contains("user_id");
    }

    private static String sqlOf(Method method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return String.join("", select.value());
        }
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            return String.join("", insert.value());
        }
        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            return String.join("", update.value());
        }
        return null;
    }
}
