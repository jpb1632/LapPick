package lappick.qna.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

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
    void deleteSingleQna_ignoresExternalReturnUrl() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = adminQnaController.deleteSingleQna(1, "https://evil.example", redirectAttributes);

        verify(qnaService).deleteQnaByAdmin(1);
        assertThat(viewName).isEqualTo("redirect:/admin/qna");
    }
}
