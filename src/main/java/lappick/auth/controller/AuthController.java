package lappick.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import lappick.auth.dto.RegisterRequest;
import lappick.auth.mapper.AuthMapper;
import lappick.auth.service.AuthService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AuthMapper authMapper;

    @Value("${app.mail.preview-enabled:true}")
    private boolean mailPreviewEnabled;

    @Value("${app.mail.preview-url:http://127.0.0.1:8025}")
    private String mailPreviewUrl;

    @GetMapping("/login")
    public String loginForm() {
        return "user/auth/login";
    }

    @ModelAttribute("registerRequest")
    public RegisterRequest registerRequest() {
        return new RegisterRequest();
    }

    @GetMapping("/register/agree")
    public String agree() {
        return "user/auth/register-agree";
    }

    @GetMapping("/register/write")
    public String registerForm() {
        return "user/auth/register-form";
    }

    @PostMapping("/register/write")
    public String register(@Validated @ModelAttribute("registerRequest") RegisterRequest registerRequest,
                           BindingResult result) {
        if (!result.hasFieldErrors("memberPwCon")
                && registerRequest.getMemberPw() != null
                && registerRequest.getMemberPwCon() != null
                && !registerRequest.getMemberPw().equals(registerRequest.getMemberPwCon())) {
            result.rejectValue("memberPwCon", "register.password.mismatch", "비밀번호 확인이 일치하지 않습니다.");
        }

        if (result.hasErrors()) {
            return "user/auth/register-form";
        }

        try {
            authService.joinMember(registerRequest);
        } catch (IllegalArgumentException e) {
            rejectRegistrationError(registerRequest, result, e.getMessage());
            return "user/auth/register-form";
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration failed due to data integrity violation. memberId={}", registerRequest.getMemberId(), e);
            rejectRegistrationError(registerRequest, result, "회원가입 처리 중 문제가 발생했습니다. 입력 정보를 다시 확인해주세요.");
            return "user/auth/register-form";
        }

        return "redirect:/auth/register/welcome";
    }

    private void rejectRegistrationError(RegisterRequest registerRequest, BindingResult result, String defaultMessage) {
        boolean rejected = false;

        if (registerRequest.getMemberId() != null && authMapper.idCheckSelectOne(registerRequest.getMemberId()) != null) {
            result.rejectValue("memberId", "register.duplicate.memberId", "이미 사용 중인 아이디입니다.");
            rejected = true;
        }

        if (registerRequest.getMemberEmail() != null && authMapper.emailCheckSelectOne(registerRequest.getMemberEmail()) != null) {
            result.rejectValue("memberEmail", "register.duplicate.memberEmail", "이미 사용 중인 이메일입니다.");
            rejected = true;
        }

        if (!rejected) {
            result.reject("register.failed",
                    defaultMessage != null ? defaultMessage : "회원가입 처리 중 문제가 발생했습니다. 입력 정보를 다시 확인해주세요.");
        }
    }

    @GetMapping("/register/welcome")
    public String welcome() {
        return "user/auth/register-complete";
    }

    @GetMapping("/find-id")
    public String findIdForm() {
        return "user/auth/find-id-form";
    }

    @PostMapping("/find-id")
    public String findIdAction(@RequestParam("memberName") String memberName,
                               @RequestParam("memberEmail") String memberEmail,
                               Model model) {
        String memberId = authService.findIdByNameAndEmail(memberName, memberEmail);
        model.addAttribute("memberId", memberId);
        return "user/auth/find-id-result";
    }

    @GetMapping("/find-pw")
    public String findPwForm() {
        return "user/auth/find-pw-form";
    }

    @PostMapping("/find-pw")
    public String findPwAction(@RequestParam("memberId") String memberId,
                               @RequestParam("memberEmail") String memberEmail,
                               Model model) {
        try {
            boolean resetCompleted = authService.resetPassword(memberId, memberEmail);
            model.addAttribute("success", resetCompleted);

            if (resetCompleted) {
                model.addAttribute("mailPreviewEnabled", mailPreviewEnabled);
                model.addAttribute("mailPreviewUrl", mailPreviewUrl);
            } else {
                model.addAttribute("errorMessage", "입력하신 정보와 일치하는 회원 정보가 없습니다.");
            }
        } catch (Exception e) {
            log.error("Failed to reset password for memberId={}", memberId, e);
            model.addAttribute("success", false);
            model.addAttribute("errorMessage", "임시 비밀번호 발송 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
        return "user/auth/find-pw-result";
    }

    @PostMapping("/userIdCheck")
    @ResponseBody
    public Integer userIdCheck(@RequestParam("userId") String userId) {
        return authMapper.idCheckSelectOne(userId);
    }
}
