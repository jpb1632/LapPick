package lappick.goods.controller;

import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lappick.common.dto.PageData;
import lappick.goods.dto.GoodsFilterRequest;
import lappick.goods.dto.GoodsPageResponse;
import lappick.goods.dto.GoodsStockResponse;
import lappick.goods.service.GoodsService;
import lappick.member.dto.MemberResponse;
import lappick.member.mapper.MemberMapper;
import lappick.qna.service.QnaService;
import lappick.qna.dto.QnaResponse;
import lappick.qna.dto.QnaWriteRequest;
import lappick.review.dto.ReviewPageResponse;
import lappick.review.dto.ReviewSummaryResponse;
import lappick.review.service.ReviewService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;
    private final MemberMapper memberMapper;
    private final QnaService qnaService;
    private final ReviewService reviewService;

    @GetMapping("/goodsFullList")
    public String goodsFullList(GoodsFilterRequest filter, Model model) {
        GoodsPageResponse pageData = goodsService.getGoodsListPage(filter, 9);
        model.addAttribute("pageData", pageData);
        model.addAttribute("goodsList", pageData.getItems());
        model.addAttribute("filter", filter);

        int paginationRange = 5;
        int startPage = (int) (Math.floor((pageData.getPage() - 1.0) / paginationRange) * paginationRange + 1);
        int endPage = Math.min(startPage + paginationRange - 1, pageData.getTotalPages());

        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("hasPrev", startPage > 1);
        model.addAttribute("hasNext", endPage < pageData.getTotalPages());

        return "user/goods/goods-list";
    }

    @GetMapping("/detail/{goodsNum}")
    public String showGoodsDetail(
            @PathVariable("goodsNum") String goodsNum,
            @RequestParam(name = "reviewPage", defaultValue = "1") int reviewPage,
            @RequestParam(name = "qnaPage", defaultValue = "1") int qnaPage,
            Model model, Principal principal, RedirectAttributes ra) {

        GoodsStockResponse dto = goodsService.getGoodsDetailWithStock(goodsNum);
        
        if (dto == null) {
            ra.addFlashAttribute("error", "상품 정보를 찾을 수 없습니다.");
            return "redirect:/goods/goodsFullList";
        }

        // QnA 목록 조회 (페이징)
        PageData<QnaResponse> qnaPageData = qnaService.getQnaPageByGoodsNum(goodsNum, qnaPage, 5); // 5개씩

        // 리뷰 목록 조회 (페이징)
        ReviewPageResponse reviewPageData = reviewService.findReviewsByGoodsNum(goodsNum, reviewPage, 5); // 5개씩
        ReviewSummaryResponse reviewSummary = reviewService.getReviewSummary(goodsNum);

        String loginMemberNum = null;
        boolean isEmployee = false;
        if (principal != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            MemberResponse member = memberMapper.selectOneById(auth.getName());
            if (member != null) {
                loginMemberNum = member.getMemberNum();
            }
            isEmployee = auth.getAuthorities().stream()
                             .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
        }

        // --- 페이지네이션 번호 계산 (최대 5개씩) ---
        int paginationRange = 5;

        // 리뷰 페이지네이션 계산
        int reviewStartPage = (int) (Math.floor((reviewPageData.getPage() - 1.0) / paginationRange) * paginationRange + 1);
        int reviewEndPage = Math.min(reviewStartPage + paginationRange - 1, reviewPageData.getTotalPages());
        boolean reviewHasPrev = reviewStartPage > 1;
        boolean reviewHasNext = reviewEndPage < reviewPageData.getTotalPages();

        // QnA 페이지네이션 계산
        int qnaStartPage = (int) (Math.floor((qnaPageData.getPage() - 1.0) / paginationRange) * paginationRange + 1);
        int qnaEndPage = Math.min(qnaStartPage + paginationRange - 1, qnaPageData.getTotalPages());
        boolean qnaHasPrev = qnaStartPage > 1;
        boolean qnaHasNext = qnaEndPage < qnaPageData.getTotalPages();
        // --- 페이지네이션 번호 계산 끝 ---

        model.addAttribute("loginMemberNum", loginMemberNum);
        model.addAttribute("isEmployee", isEmployee);
        model.addAttribute("goods", dto);
        model.addAttribute("reviewSummary", reviewSummary);
        model.addAttribute("qnaWriteRequest", new QnaWriteRequest());

        // 리뷰 관련 모델 속성
        model.addAttribute("reviewPageData", reviewPageData);
        model.addAttribute("reviewStartPage", reviewStartPage);
        model.addAttribute("reviewEndPage", reviewEndPage);
        model.addAttribute("reviewHasPrev", reviewHasPrev);
        model.addAttribute("reviewHasNext", reviewHasNext);

        // QnA 관련 모델 속성
        model.addAttribute("qnaPageData", qnaPageData);
        model.addAttribute("qnaStartPage", qnaStartPage);
        model.addAttribute("qnaEndPage", qnaEndPage);
        model.addAttribute("qnaHasPrev", qnaHasPrev);
        model.addAttribute("qnaHasNext", qnaHasNext);

        return "user/goods/goods-detail";
    }
}
