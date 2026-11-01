package art.pilipala.api.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.i18n.application.RequestLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

/**
 * 语言选择优先级：登录用户偏好 &gt; Accept-Language &gt; zh。
 */
class RequestLocaleResolverTest {

    private static final UUID USER = UUID.randomUUID();

    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RequestLocaleResolver resolver = new RequestLocaleResolver(sessions, users);

    @Test
    void theUserPreferenceWinsOverTheAcceptLanguageHeader() {
        givenUser("en");

        assertThat(resolver.resolve(USER, "zh-CN")).isEqualTo("en");
    }

    @Test
    void withoutPreferenceTheAcceptLanguageHeaderDecides() {
        givenUser(null);

        assertThat(resolver.resolve(USER, "en-US,en;q=0.9")).isEqualTo("en");
        assertThat(resolver.resolve(USER, "zh-CN,zh;q=0.9")).isEqualTo("zh");
    }

    @Test
    void anUnsupportedPreferenceFallsBackToTheHeader() {
        givenUser("fr");

        assertThat(resolver.resolve(USER, "en-US")).isEqualTo("en");
    }

    @Test
    void anonymousRequestsUseTheHeader() {
        assertThat(resolver.resolve((UUID) null, "en-US")).isEqualTo("en");
        assertThat(resolver.resolve((UUID) null, null)).isEqualTo("zh");
    }

    @Test
    void databaseFailuresFallBackToTheHeader() {
        when(users.findById(USER)).thenThrow(new IllegalStateException("relation \"users\" does not exist"));

        assertThat(resolver.resolve(USER, "en-US")).isEqualTo("en");
    }

    @Test
    void theRequestOverloadPrefersTheLoggedInUser() {
        givenUser("en");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Accept-Language")).thenReturn("zh-CN");
        when(sessions.currentUserId()).thenReturn(USER.toString());

        assertThat(resolver.resolve(request)).isEqualTo("en");
    }

    @Test
    void anInvalidSessionFallsBackToTheHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Accept-Language")).thenReturn("en-US");
        when(sessions.currentUserId()).thenThrow(new IllegalStateException("not logged in"));

        assertThat(resolver.resolve(request)).isEqualTo("en");
    }

    private void givenUser(String locale) {
        when(users.findById(USER)).thenReturn(Optional.of(
                new User(USER, "bob", null, null, "hash", locale, Instant.now(), Instant.now())));
    }
}
