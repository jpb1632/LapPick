package lappick.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import lappick.auth.dto.RegisterRequest;
import lappick.auth.mapper.AuthMapper;
import lappick.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthMapper authMapper;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, authMapper);
    }

    @Test
    void register_showsFieldErrorWhenPasswordConfirmationDoesNotMatch() {
        RegisterRequest request = createValidRequest();
        request.setMemberPwCon("different123");
        BindingResult result = new BeanPropertyBindingResult(request, "registerRequest");

        String viewName = authController.register(request, result);

        assertThat(viewName).isEqualTo("user/auth/register-form");
        assertThat(result.getFieldError("memberPwCon")).isNotNull();
        assertThat(result.getFieldError("memberPwCon").getDefaultMessage()).isEqualTo("비밀번호 확인이 일치하지 않습니다.");
        verifyNoInteractions(authService);
    }

    @Test
    void register_showsFieldErrorWhenMemberIdIsDuplicated() {
        RegisterRequest request = createValidRequest();
        BindingResult result = new BeanPropertyBindingResult(request, "registerRequest");

        when(authMapper.idCheckSelectOne("tester01")).thenReturn(1);
        when(authMapper.emailCheckSelectOne("tester01@example.com")).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("이미 사용 중인 아이디입니다."))
                .when(authService).joinMember(any(RegisterRequest.class));

        String viewName = authController.register(request, result);

        assertThat(viewName).isEqualTo("user/auth/register-form");
        assertThat(result.getFieldError("memberId")).isNotNull();
        assertThat(result.getFieldError("memberId").getDefaultMessage()).isEqualTo("이미 사용 중인 아이디입니다.");
    }

    @Test
    void register_showsFieldErrorWhenUniqueConstraintFailsForEmail() {
        RegisterRequest request = createValidRequest();
        BindingResult result = new BeanPropertyBindingResult(request, "registerRequest");

        when(authMapper.idCheckSelectOne("tester01")).thenReturn(null);
        when(authMapper.emailCheckSelectOne("tester01@example.com")).thenReturn(1);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("unique constraint"))
                .when(authService).joinMember(any(RegisterRequest.class));

        String viewName = authController.register(request, result);

        assertThat(viewName).isEqualTo("user/auth/register-form");
        assertThat(result.getFieldError("memberEmail")).isNotNull();
        assertThat(result.getFieldError("memberEmail").getDefaultMessage()).isEqualTo("이미 사용 중인 이메일입니다.");
    }

    private RegisterRequest createValidRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setMemberId("tester01");
        request.setMemberPw("password123");
        request.setMemberPwCon("password123");
        request.setMemberName("테스터");
        request.setMemberEmail("tester01@example.com");
        request.setMemberPhone1("01012345678");
        request.setMemberAddr("서울시 강남구");
        request.setMemberAddrDetail("101호");
        request.setMemberPost("12345");
        request.setGender("M");
        request.setMemberBirth(java.time.LocalDate.of(1995, 1, 1));
        return request;
    }
}
