package art.pilipala.api.auth.web;

import java.util.Map;

import art.pilipala.api.auth.application.AuthSession;
import art.pilipala.api.auth.application.ChangePassword;
import art.pilipala.api.auth.application.CurrentUser;
import art.pilipala.api.auth.application.GetCurrentUser;
import art.pilipala.api.auth.application.LoginCommand;
import art.pilipala.api.auth.application.LoginUser;
import art.pilipala.api.auth.application.LogoutUser;
import art.pilipala.api.auth.application.OtpService;
import art.pilipala.api.auth.application.RebindEmail;
import art.pilipala.api.auth.application.RebindPhone;
import art.pilipala.api.auth.application.RegisterCommand;
import art.pilipala.api.auth.application.RegisterUser;
import art.pilipala.api.auth.application.ResetPassword;
import art.pilipala.api.auth.application.EmailAuthPolicy;
import art.pilipala.api.auth.application.SendOtp;
import art.pilipala.api.billing.infra.ClientIpResolver;
import art.pilipala.api.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUser registerUser;
    private final LoginUser loginUser;
    private final LogoutUser logoutUser;
    private final GetCurrentUser getCurrentUser;
    private final SendOtp sendOtp;
    private final ChangePassword changePassword;
    private final ResetPassword resetPassword;
    private final RebindPhone rebindPhone;
    private final RebindEmail rebindEmail;
    private final EmailAuthPolicy emailAuthPolicy;

    public AuthController(RegisterUser registerUser, LoginUser loginUser, LogoutUser logoutUser, GetCurrentUser getCurrentUser,
            SendOtp sendOtp, ChangePassword changePassword, ResetPassword resetPassword, RebindPhone rebindPhone,
            RebindEmail rebindEmail, EmailAuthPolicy emailAuthPolicy) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
        this.logoutUser = logoutUser;
        this.getCurrentUser = getCurrentUser;
        this.sendOtp = sendOtp;
        this.changePassword = changePassword;
        this.resetPassword = resetPassword;
        this.rebindPhone = rebindPhone;
        this.rebindEmail = rebindEmail;
        this.emailAuthPolicy = emailAuthPolicy;
    }

    @GetMapping("/features")
    public ApiResponse<AuthFeaturesResponse> features() {
        EmailAuthPolicy.AuthFeaturesView view = emailAuthPolicy.features();
        return ApiResponse.ok(new AuthFeaturesResponse(view.emailLoginEnabled(), view.emailRegisterEnabled()));
    }

    @PostMapping("/otp/send")
    public ApiResponse<OtpSendResponse> sendOtp(@RequestBody OtpSendRequest request) {
        OtpService.SendOtpResult result = sendOtp.send(request.channel(), request.destination(), request.purpose());
        return ApiResponse.ok(new OtpSendResponse(result.expiresIn(), result.cooldown()));
    }

    @PostMapping("/register")
    public ApiResponse<AuthSession> register(@RequestBody RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(request.phone(), request.email(), request.password(), request.otp(),
                request.locale());
        return ApiResponse.ok(registerUser.register(command));
    }

    @PostMapping("/login")
    public ApiResponse<AuthSession> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginCommand command = new LoginCommand(request.phone(), request.email(), request.password(), request.otp());
        return ApiResponse.ok(loginUser.login(command, ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/password/change")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest request) {
        changePassword.change(request.oldPassword(), request.newPassword());
        return ApiResponse.ok(Map.of());
    }

    @PostMapping("/password/reset")
    public ApiResponse<AuthSession> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(resetPassword.reset(request.phone(), request.email(), request.otp(), request.newPassword()));
    }

    @PostMapping("/rebind/phone")
    public ApiResponse<Map<String, Object>> rebindPhone(@RequestBody RebindPhoneRequest request) {
        rebindPhone.rebind(request.newPhone(), request.otp());
        return ApiResponse.ok(Map.of());
    }

    @PostMapping("/rebind/email")
    public ApiResponse<Map<String, Object>> rebindEmail(@RequestBody RebindEmailRequest request) {
        rebindEmail.rebind(request.newEmail(), request.otp());
        return ApiResponse.ok(Map.of());
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        logoutUser.logout();
        return ApiResponse.ok(Map.of());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me() {
        return ApiResponse.ok(getCurrentUser.current());
    }

    public record AuthFeaturesResponse(boolean emailLoginEnabled, boolean emailRegisterEnabled) {
    }

    public record OtpSendRequest(String channel, String destination, String purpose) {
    }

    public record OtpSendResponse(int expiresIn, int cooldown) {
    }

    public record RegisterRequest(String phone, String email, String password, String otp, String locale) {
    }

    public record LoginRequest(String phone, String email, String password, String otp) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    public record ResetPasswordRequest(String phone, String email, String otp, String newPassword) {
    }

    public record RebindPhoneRequest(String newPhone, String otp) {
    }

    public record RebindEmailRequest(String newEmail, String otp) {
    }
}
