package lappick.admin.employee.controller;

import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;
import lappick.admin.employee.dto.EmployeeResponse;
import lappick.admin.employee.service.EmployeeMyPageService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/employee/my-page")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
public class EmployeeMyPageController {

    private final EmployeeMyPageService employeeMyPageService;

    @GetMapping
    public String myPage(Model model, Principal principal) {
        String empId = principal.getName();
        EmployeeResponse dto = employeeMyPageService.getEmployeeInfo(empId);
        model.addAttribute("employeeCommand", dto);
        return "admin/employee/employee-mypage";
    }
    
    @GetMapping("/edit")
    public String editForm(Model model, Principal principal) {
        EmployeeResponse dto = employeeMyPageService.getEmployeeInfo(principal.getName());
        dto.setMaskedEmpJumin(dto.getEmpJumin());
        dto.setEmpJumin("");
        
        // 뷰에서 '관리자' 컨텍스트와 구분하기 위해 '마이페이지'임을 전달 (폼 재활용)
        model.addAttribute("isAdminContext", false); 
        
        model.addAttribute("employeeCommand", dto);
        return "admin/employee/employee-edit";
    }
    
    @PostMapping("/update")
    public String updateInfo(EmployeeResponse command, Principal principal, RedirectAttributes ra) {
        employeeMyPageService.updateMyInfo(command, principal.getName());
        ra.addFlashAttribute("message", "정보가 성공적으로 수정되었습니다.");
        return "redirect:/employee/my-page";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("oldPw") String oldPw,
                                 @RequestParam("newPw") String newPw,
                                 @RequestParam("newPwCon") String newPwCon,
                                 Principal principal,
                                 RedirectAttributes ra,
                                 HttpSession session) {
        if (!newPw.equals(newPwCon)) {
            ra.addFlashAttribute("error", "새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
            return "redirect:/employee/my-page";
        }

        try {
            employeeMyPageService.changePassword(principal.getName(), oldPw, newPw);
            ra.addFlashAttribute("message", "비밀번호가 변경되었습니다. 다시 로그인해주세요.");
            
            SecurityContextHolder.clearContext();
            session.invalidate();
            
            return "redirect:/auth/login"; 
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/employee/my-page";
        }
    }
}
