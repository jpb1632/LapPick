package lappick.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
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
        ReflectionTestUtils.setField(authController, "mailPreviewEnabled", true);
        ReflectionTestUtils.setField(authController, "mailPreviewUrl", "http://127.0.0.1:8025");
    }

    @Test
    void register_redirectsToWelcomeOnSuccess() {
        RegisterRequest request = createValidRequest();
        BindingResult result = new BeanPropertyBindingResult(request, "registerRequest");

        String viewName = authController.register(request, result);

        assertThat(viewName).isEqualTo("redirect:/auth/register/welcome");
        verify(authService).joinMember(request);
    }

    @Test
    void register_showsFieldErrorWhenPasswordConfirmationDoesNotMatch() {
        RegisterRequest request = createValidRequest();
        request.setMemberPwCon("different123");
        BindingResult result = new BeanPropertyBindingResult(request, "registerRequest");

        String viewName = authController.register(request, result);

        assertThat(viewName).isEqualTo("user/auth/register-form");
        assertThat(result.getFieldError("memberPwCon")).isNotNull();
        verifyNoInteractions(authService);
    }

    @Test
    void register_showsFieldErrorWhenMemberIdIsDuplicated() {
        RegisterRequest request = createValidRequest();
        BindingResult result = new BeanPropertyBindingResult(request, "registerRequest");

        when(authMapper.idCheckSelectOne("tester01")).thenReturn(1);
        when(authMapper.emailCheckSelectOne("tester01@example.com")).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("duplicate member id"))
                .when(authService).joinMember(any(RegisterRequest.class));

        String viewName = authController.register(request, result);

        assertThat(viewName).isEqualTo("user/auth/register-form");
        assertThat(result.getFieldError("memberId")).isNotNull();
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
    }

    @Test
    void findIdAction_keepsNullWhenMemberIsNotFound() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(authService.findIdByNameAndEmail("Tester", "tester@example.com")).thenReturn(null);

        String viewName = authController.findIdAction("Tester", "tester@example.com", model);

        assertThat(viewName).isEqualTo("user/auth/find-id-result");
        assertThat(model.getAttribute("memberId")).isNull();
    }

    @Test
    void findPwAction_setsFailureMessageWhenMemberIsNotFound() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(authService.resetPassword("tester01", "tester@example.com")).thenReturn(false);

        String viewName = authController.findPwAction("tester01", "tester@example.com", model);

        assertThat(viewName).isEqualTo("user/auth/find-pw-result");
        assertThat(model.getAttribute("success")).isEqualTo(false);
        assertThat(model.getAttribute("errorMessage")).isNotNull();
    }

    @Test
    void findPwAction_exposesPreviewInfoWhenResetSucceeds() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(authService.resetPassword("tester01", "tester@example.com")).thenReturn(true);

        String viewName = authController.findPwAction("tester01", "tester@example.com", model);

        assertThat(viewName).isEqualTo("user/auth/find-pw-result");
        assertThat(model.getAttribute("success")).isEqualTo(true);
        assertThat(model.getAttribute("mailPreviewEnabled")).isEqualTo(true);
        assertThat(model.getAttribute("mailPreviewUrl")).isEqualTo("http://127.0.0.1:8025");
    }

    private RegisterRequest createValidRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setMemberId("tester01");
        request.setMemberPw("password123");
        request.setMemberPwCon("password123");
        request.setMemberName("Tester");
        request.setMemberEmail("tester01@example.com");
        request.setMemberPhone1("01012345678");
        request.setMemberAddr("Seoul");
        request.setMemberAddrDetail("101");
        request.setMemberPost("12345");
        request.setGender("M");
        request.setMemberBirth(LocalDate.of(1995, 1, 1));
        return request;
    }
}
