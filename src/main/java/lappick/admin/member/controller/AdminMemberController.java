package lappick.admin.member.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lappick.admin.member.dto.AdminMemberPageResponse;
import lappick.admin.member.service.AdminMemberService;
import lappick.member.dto.MemberResponse;
import lappick.member.dto.MemberUpdateRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public String listMembers(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "5") Integer size,
                              @RequestParam(required = false) String searchWord,
                              Model model) {
        AdminMemberPageResponse pageData = adminMemberService.getMemberListPage(page, size, searchWord);
        model.addAttribute("pageData", pageData);
        model.addAttribute("members", pageData.getItems());
        model.addAttribute("searchWord", searchWord);
        model.addAttribute("page", pageData.getPage());
        model.addAttribute("totalPage", pageData.getTotalPages());

        return "admin/member/member-list";
    }

    @GetMapping("/{memberNum}")
    public String memberDetail(@PathVariable String memberNum, Model model) {
        MemberResponse dto = adminMemberService.getMemberDetail(memberNum);
        model.addAttribute("memberCommand", dto);
        return "admin/member/member-detail";
    }

    @GetMapping("/{memberNum}/edit")
    public String editForm(@PathVariable String memberNum, Model model) {
        MemberResponse responseDto = adminMemberService.getMemberDetail(memberNum);
        MemberUpdateRequest requestDto = new MemberUpdateRequest();
        BeanUtils.copyProperties(responseDto, requestDto);

        model.addAttribute("memberCommand", requestDto);
        model.addAttribute("memberInfo", responseDto);
        return "admin/member/member-edit";
    }

    @PostMapping("/update")
    public String updateMember(MemberUpdateRequest command, RedirectAttributes ra, Model model) {
        try {
            adminMemberService.updateMember(command);
            ra.addFlashAttribute("message", "회원 정보가 성공적으로 수정되었습니다.");
            return "redirect:/admin/members/" + command.getMemberNum();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("memberCommand", command);
            model.addAttribute("memberInfo", adminMemberService.getMemberDetail(command.getMemberNum()));
            return "admin/member/member-edit";
        }
    }

    @GetMapping("/add")
    public String addForm(@ModelAttribute("member") MemberUpdateRequest command) {
        command.setMemberBirth(LocalDate.of(1990, 1, 1));
        return "admin/member/member-form";
    }

    @PostMapping("/add")
    public String addMember(@ModelAttribute("member") @Valid MemberUpdateRequest command,
                            BindingResult br,
                            RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "admin/member/member-form";
        }

        if (!command.getMemberPw().equals(command.getMemberPwCon())) {
            br.rejectValue("memberPwCon", "password.mismatch", "비밀번호가 일치하지 않습니다.");
            return "admin/member/member-form";
        }

        try {
            adminMemberService.createMember(command);
            ra.addFlashAttribute("msg", "회원이 성공적으로 등록되었습니다.");
            return "redirect:/admin/members";
        } catch (IllegalArgumentException e) {
            br.reject("member.duplicate", e.getMessage());
            return "admin/member/member-form";
        }
    }

    @PostMapping("/delete")
    public String deleteMembers(@RequestParam(value = "memberNums", required = false) List<String> memberNums,
                                RedirectAttributes ra) {
        if (memberNums == null || memberNums.isEmpty()) {
            ra.addFlashAttribute("error", "삭제할 회원을 선택해주세요.");
            return "redirect:/admin/members";
        }

        try {
            int deletedCount = adminMemberService.deleteMembers(memberNums);
            ra.addFlashAttribute("msg", deletedCount + "명의 회원 정보를 삭제했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/delete/{memberNum}")
    public String deleteSingleMember(@PathVariable String memberNum, RedirectAttributes ra) {
        try {
            int deletedCount = adminMemberService.deleteMembers(java.util.Collections.singletonList(memberNum));
            ra.addFlashAttribute("msg", deletedCount + "명의 회원 정보를 삭제했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }
}
