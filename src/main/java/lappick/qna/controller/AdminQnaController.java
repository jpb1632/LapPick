package lappick.qna.controller;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lappick.common.dto.PageData;
import lappick.qna.dto.QnaAnswerRequest;
import lappick.qna.dto.QnaResponse;
import lappick.qna.service.QnaService;

@Controller
@RequestMapping("/admin/qna")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
public class AdminQnaController {

    private static final Logger log = LoggerFactory.getLogger(AdminQnaController.class);

    private final QnaService qnaService;

    /**
     * 관리자 Q&A 목록 조회
     */
    @GetMapping
    public String qnaList(@RequestParam(value = "searchWord", required = false) String searchWord,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "page", defaultValue = "1") int page,
                          Model model,
                          HttpServletRequest request) {
        int size = 5;
        try {
            PageData<QnaResponse> pageData = qnaService.getAllQnaListPage(searchWord, status, page, size);

            model.addAttribute("pageData", pageData);
            model.addAttribute("qnaList", pageData.getItems());
            model.addAttribute("status", status);

            String currentUrl = request.getRequestURI();
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                currentUrl += "?" + queryString;
            }
            model.addAttribute("currentUrl", currentUrl);

            return "admin/qna/qna-list";

        } catch (Exception e) {
            log.error("관리자 QnA 목록 조회 중 오류 발생", e);
            model.addAttribute("error", "문의 목록을 불러오는 중 오류가 발생했습니다.");
            return "admin/qna/qna-list";
        }
    }

    /**
     * Q&A 답변 등록/수정 처리
     */
    @PostMapping("/answer")
    public String addAnswer(@Validated @ModelAttribute QnaAnswerRequest request, BindingResult bindingResult,
                            RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            log.warn("QnA 답변 유효성 검사 실패: {}", bindingResult.getAllErrors());
            // 에러 메시지를 FlashAttribute로 전달하여 리다이렉트 후 표시
            ra.addFlashAttribute("answerErrorQnaNum", request.getQnaNum());
            ra.addFlashAttribute("answerBindingResult", bindingResult);
            ra.addFlashAttribute("qnaAnswerRequest", request);
            // returnUrl이 있으면 해당 경로로, 없으면 기본 목록 경로로 리다이렉트
            String redirectUrl = resolveSafeReturnUrl(request.getReturnUrl());
            return "redirect:" + redirectUrl;
        }

        try {
            qnaService.addOrUpdateAnswer(request);
            ra.addFlashAttribute("message", request.getQnaNum() + "번 문의에 답변이 등록되었습니다.");

            // returnUrl이 있으면 해당 경로로, 없으면 기본 목록 경로로 리다이렉트
            String redirectUrl = resolveSafeReturnUrl(request.getReturnUrl());
            return "redirect:" + redirectUrl;

        } catch (IllegalArgumentException e) {
             log.warn("QnA 답변 등록 실패: {}", e.getMessage());
             ra.addFlashAttribute("error", e.getMessage());
             String redirectUrl = resolveSafeReturnUrl(request.getReturnUrl());
             return "redirect:" + redirectUrl;
        } catch (Exception e) {
            log.error("QnA 답변 등록 중 예상치 못한 오류 발생: qnaNum={}", request.getQnaNum(), e);
            ra.addFlashAttribute("error", "답변 등록 중 오류가 발생했습니다.");
            String redirectUrl = resolveSafeReturnUrl(request.getReturnUrl());
            return "redirect:" + redirectUrl;
        }
    }
    
    @PostMapping("/delete/{qnaNum}")
    public String deleteSingleQna(@PathVariable("qnaNum") Integer qnaNum, 
                                  @RequestParam(value = "returnUrl", defaultValue = "/admin/qna") String returnUrl,
                                  RedirectAttributes ra) {
        try {
            qnaService.deleteQnaByAdmin(qnaNum);
            ra.addFlashAttribute("message", "문의(번호: " + qnaNum + ")가 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            log.error("관리자 QnA 개별 삭제 중 오류 발생: qnaNum={}", qnaNum, e);
            ra.addFlashAttribute("error", "문의 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:" + resolveSafeReturnUrl(returnUrl);
    }

    @PostMapping("/delete-bulk")
    public String deleteBulkQna(@RequestParam("qnaNums") List<Integer> qnaNums,
                                @RequestParam(value = "returnUrl", defaultValue = "/admin/qna") String returnUrl,
                                RedirectAttributes ra) {
        try {
            qnaService.deleteQnaByAdmin(qnaNums);
            ra.addFlashAttribute("message", "선택한 " + qnaNums.size() + "개의 문의가 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            log.error("관리자 QnA 일괄 삭제 중 오류 발생", e);
            ra.addFlashAttribute("error", "문의 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:" + resolveSafeReturnUrl(returnUrl);
    }

    private String resolveSafeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "/admin/qna";
        }
        if (!returnUrl.startsWith("/admin/qna")) {
            return "/admin/qna";
        }
        if (returnUrl.startsWith("//") || returnUrl.contains("://")) {
            return "/admin/qna";
        }
        return returnUrl;
    }
}
