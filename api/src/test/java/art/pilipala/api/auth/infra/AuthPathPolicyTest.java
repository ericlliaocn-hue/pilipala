package art.pilipala.api.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 鉴权闸门的路径规则。
 *
 * <p>其中 {@code /api/v1/jobs/**} 是 WP4 的 submit 等接口所在地，
 * 断言它落在闸门内，避免 WP4 另开一套鉴权。
 */
class AuthPathPolicyTest {

    @Test
    void protectsEverythingUnderApiV1() {
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/auth/me")).isTrue();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/auth/logout")).isTrue();
    }

    @Test
    void protectsJobPathsForWp4() {
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/jobs/submit")).isTrue();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/jobs/query")).isTrue();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/jobs/result")).isTrue();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/jobs/cancel")).isTrue();
    }

    @Test
    void allowsRegisterAndLogin() {
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.REGISTER_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.LOGIN_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.OTP_SEND_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.PASSWORD_RESET_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.AUTH_FEATURES_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.STUDIO_FEATURES_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.ZPAY_RECHARGE_WEBHOOK_PATH)).isFalse();
    }

    /** 步骤 4 唯一的免登录新路由是当前 Banner；后台 Banner 与其它 banners 路径仍要登录。 */
    @Test
    void allowsOnlyTheCurrentBannerPath() {
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.BANNERS_CURRENT_PATH)).isFalse();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/banners")).isTrue();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/admin/banners")).isTrue();
    }

    @Test
    void leavesActuatorAndOtherPathsAlone() {
        assertThat(AuthPathPolicy.requiresLogin("/actuator/health")).isFalse();
        assertThat(AuthPathPolicy.requiresLogin("/")).isFalse();
        assertThat(AuthPathPolicy.requiresLogin(null)).isFalse();
    }

    @Test
    void normalizesTrailingSlash() {
        assertThat(AuthPathPolicy.requiresLogin(AuthPathPolicy.LOGIN_PATH + "/")).isFalse();
        assertThat(AuthPathPolicy.requiresLogin("/api/v1/jobs/submit/")).isTrue();
    }
}
