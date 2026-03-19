package lappick.member.controller;

import lappick.auth.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lappick.member.dto.MemberResponse;
import lappick.member.dto.MemberUpdateRequest;
import lappick.member.mapper.MemberMapper;
import lappick.member.service.MemberService;
import lappick.review.dto.ReviewPageResponse;
import lappick.review.service.ReviewService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class MyPageController {

    private final MemberService memberService;
    private final AuthService authService;
    private final ReviewService reviewService;
    private final MemberMapper memberMapper;

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    @PreAuthorize("hasAuthority('ROLE_MEMBER')")
    @GetMapping("/my-page")
    public String myPage(Model model) {
        String memberId = getCurrentUserId();
        if (memberId == null) {
            return "redirect:/auth/login";
        }
        MemberResponse dto = memberService.getMemberInfo(memberId);
        
        // 회원 정보가 null일 경우 (탈퇴 직후 등) 방어 코드
        if (dto == null) {
            SecurityContextHolder.clearContext(); // 컨텍스트를 확실히 비움
            return "redirect:/auth/login";
        }
        
        model.addAttribute("memberCommand", dto); // 뷰 호환성을 위해 이름 유지
        return "user/member/mypage";
    }

    @PreAuthorize("hasAuthority('ROLE_MEMBER')")
    @PostMapping("/my-page/update")
    public String updateMyInfo(MemberUpdateRequest command, RedirectAttributes ra) {
        String memberId = getCurrentUserId();
        try {
            memberService.updateMyInfo(command, memberId);
            ra.addFlashAttribute("message", "정보가 성공적으로 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/member/my-page";
    }

    @PreAuthorize("hasAuthority('ROLE_MEMBER')")
    @PostMapping("/my-page/change-password")
    public String changePassword(@RequestParam("oldPw") String oldPw,
                                 @RequestParam("newPw") String newPw,
                                 RedirectAttributes ra) {
        String memberId = getCurrentUserId();
        if (memberId == null) {
            return "redirect:/auth/login";
        }
        
        try {
            authService.changePassword(memberId, oldPw, newPw);
            
            ra.addFlashAttribute("message", "비밀번호가 성공적으로 변경되었습니다. 다시 로그인해주세요.");
            
            SecurityContextHolder.clearContext();
            
            return "redirect:/auth/login";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/member/my-page";
        }
    }
    
    @PreAuthorize("hasAuthority('ROLE_MEMBER')")
    @PostMapping("/withdraw")
    public String withdraw(@RequestParam("memberPw") String rawPassword,
                           RedirectAttributes ra,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           Authentication authentication) {
        String memberId = getCurrentUserId();
        try {
            memberService.withdrawMember(memberId, rawPassword);

            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/?message=withdrawSuccess";
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/member/my-page";
        }
    }
    
    @PreAuthorize("hasAuthority('ROLE_MEMBER')")
    @GetMapping("/my-reviews")
    public String myReviews(Model model, @RequestParam(value="page", defaultValue="1") int page) {
        String memberId = getCurrentUserId();
        String memberNum = memberMapper.memberNumSelect(memberId);
        
        ReviewPageResponse pageData = reviewService.getMyReviewsPage(memberNum, page, 5);
        
        model.addAttribute("pageData", pageData);
        return "user/member/my-reviews";
    }
}
