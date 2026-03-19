package lappick.qna.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import lappick.common.dto.PageData;
import lappick.qna.dto.QnaAnswerRequest;
import lappick.qna.dto.QnaResponse;
import lappick.qna.service.QnaService;

@ExtendWith(MockitoExtension.class)
class AdminQnaControllerTest {

    @Mock
    private QnaService qnaService;

    private AdminQnaController adminQnaController;

    @BeforeEach
    void setUp() {
        adminQnaController = new AdminQnaController(qnaService);
    }

    @Test
    void qnaList_returnsPageDataInModel() {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/qna");
        request.setQueryString("page=2&status=PENDING");

        PageData<QnaResponse> pageData = new PageData<>(List.of(new QnaResponse()), 2, 5, 6, "word");
        when(qnaService.getAllQnaListPage("word", "PENDING", 2, 5)).thenReturn(pageData);

        String viewName = adminQnaController.qnaList("word", "PENDING", 2, model, request);

        assertThat(viewName).isEqualTo("admin/qna/qna-list");
        assertThat(model.getAttribute("pageData")).isEqualTo(pageData);
        assertThat(model.getAttribute("qnaList")).isEqualTo(pageData.getItems());
        assertThat(model.getAttribute("currentUrl")).isEqualTo("/admin/qna?page=2&status=PENDING");
    }

    @Test
    void addAnswer_returnsSafeRedirectWhenValidationFails() {
        QnaAnswerRequest request = new QnaAnswerRequest();
        request.setQnaNum(1);
        request.setReturnUrl("https://evil.example");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "qnaAnswerRequest");
        bindingResult.rejectValue("answerContent", "required", "required");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = adminQnaController.addAnswer(request, bindingResult, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/qna");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("answerBindingResult");
    }

    @Test
    void addAnswer_redirectsToRequestedAdminPageAfterSuccess() {
        QnaAnswerRequest request = new QnaAnswerRequest();
        request.setQnaNum(1);
        request.setAnswerContent("done");
        request.setReturnUrl("/admin/qna?page=2");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "qnaAnswerRequest");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = adminQnaController.addAnswer(request, bindingResult, redirectAttributes);

        verify(qnaService).addOrUpdateAnswer(request);
        assertThat(viewName).isEqualTo("redirect:/admin/qna?page=2");
    }

    @Test
    void deleteSingleQna_ignoresExternalReturnUrl() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = adminQnaController.deleteSingleQna(1, "https://evil.example", redirectAttributes);

        verify(qnaService).deleteQnaByAdmin(1);
        assertThat(viewName).isEqualTo("redirect:/admin/qna");
    }

    @Test
    void deleteBulkQna_deletesMultipleQnaAndKeepsInternalReturnUrl() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = adminQnaController.deleteBulkQna(List.of(1, 2, 3), "/admin/qna?page=3", redirectAttributes);

        verify(qnaService).deleteQnaByAdmin(List.of(1, 2, 3));
        assertThat(viewName).isEqualTo("redirect:/admin/qna?page=3");
    }
}
