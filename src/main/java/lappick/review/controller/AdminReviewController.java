package lappick.review.controller;

import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lappick.common.dto.PageData;
import lappick.goods.dto.GoodsResponse;
import lappick.review.domain.Review;
import lappick.review.service.ReviewService;

@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
public class AdminReviewController {

    private static final Logger log = LoggerFactory.getLogger(AdminReviewController.class);

    private final ReviewService reviewService;

    @GetMapping
    public String adminReviewList(@RequestParam(value = "searchWord", required = false) String searchWord,
                                  @RequestParam(value = "rating", required = false) Integer rating,
                                  @RequestParam(value = "goodsNum", required = false) String goodsNum,
                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                  Model model) {
        int size = 5;
        try {
            PageData<Review> pageData = reviewService.getReviewPageForAdmin(searchWord, rating, goodsNum, page, size);
            List<GoodsResponse> goodsFilterList = reviewService.getAllGoods();

            model.addAttribute("pageData", pageData);
            // 뷰가 아이템에 'reviewList'를 사용한다고 가정
            model.addAttribute("reviewList", pageData.getItems());
            model.addAttribute("goodsFilterList", goodsFilterList);

            model.addAttribute("searchWord", searchWord);
            model.addAttribute("rating", rating);
            model.addAttribute("goodsNum", goodsNum);

            return "admin/review/review-list";
        } catch (Exception e) {
             log.error("관리자 리뷰 목록 조회 중 오류 발생", e);
             model.addAttribute("error", "리뷰 목록을 불러오는 중 오류가 발생했습니다.");
             return "admin/review/review-list"; // 또는 다른 에러 뷰
        }
    }

    @PostMapping("/bulk-action")
    public String bulkAction(@RequestParam("action") String action,
                             @RequestParam("reviewNums") List<Long> reviewNums,
                             RedirectAttributes ra) {
        if (reviewNums == null || reviewNums.isEmpty()) {
             ra.addFlashAttribute("warning", "처리할 리뷰를 선택해주세요.");
             return "redirect:/admin/reviews";
        }
        try {
            if ("hide".equals(action)) {
                reviewService.updateReviewStatus(reviewNums, "HIDDEN");
                ra.addFlashAttribute("message", reviewNums.size() + "개의 리뷰가 숨김 처리되었습니다.");
            } else if ("publish".equals(action)) {
                reviewService.updateReviewStatus(reviewNums, "PUBLISHED");
                ra.addFlashAttribute("message", reviewNums.size() + "개의 리뷰가 게시 처리되었습니다.");
            } else if ("delete".equals(action)) {
                reviewService.deleteReviewsByAdmin(reviewNums);
                ra.addFlashAttribute("message", reviewNums.size() + "개의 리뷰가 삭제되었습니다.");
            } else {
                 ra.addFlashAttribute("warning", "알 수 없는 작업 요청입니다.");
            }
        } catch (Exception e) {
             log.error("리뷰 일괄 작업 처리 중 오류 발생: action={}, reviewNums={}", action, reviewNums, e);
             ra.addFlashAttribute("error", "작업 처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/reviews";
    }

    @PostMapping("/toggle-status/{reviewNum}")
    public String toggleStatus(@PathVariable("reviewNum") Long reviewNum,
                               @RequestParam("currentStatus") String currentStatus, RedirectAttributes ra) {
        try {
            String newStatus = "PUBLISHED".equalsIgnoreCase(currentStatus) ? "HIDDEN" : "PUBLISHED";
            reviewService.updateReviewStatus(Collections.singletonList(reviewNum), newStatus);
            ra.addFlashAttribute("message", reviewNum + "번 리뷰 상태가 '" + (newStatus.equals("HIDDEN") ? "숨김" : "게시") + "'(으)로 변경되었습니다.");
        } catch (Exception e) {
            log.error("리뷰 상태 변경 중 오류 발생: reviewNum={}", reviewNum, e);
            ra.addFlashAttribute("error", "상태 변경 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/reviews";
    }

    @PostMapping("/delete/{reviewNum}")
    public String deleteReview(@PathVariable("reviewNum") Long reviewNum, RedirectAttributes ra) {
        try {
            reviewService.deleteReviewsByAdmin(Collections.singletonList(reviewNum));
            ra.addFlashAttribute("message", reviewNum + "번 리뷰가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("관리자 리뷰 삭제 중 오류 발생: reviewNum={}", reviewNum, e);
            ra.addFlashAttribute("error", "리뷰 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/reviews";
    }
}
