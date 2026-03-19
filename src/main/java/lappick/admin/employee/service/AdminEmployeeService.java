package lappick.admin.employee.service;

import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lappick.admin.employee.dto.AdminEmployeePageResponse;
import lappick.admin.employee.dto.EmployeeResponse;
import lappick.admin.employee.dto.EmployeeUpdateRequest;
import lappick.admin.employee.mapper.EmployeeMapper;
import lappick.auth.mapper.AuthMapper;
import lappick.common.util.SensitiveDataMasker;
import lappick.common.dto.StartEndPageDTO;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminEmployeeService {

    private final EmployeeMapper employeeMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminEmployeePageResponse getEmployeeListPage(String searchWord, int page) {
        int limit = 5;
        long startRow = (page - 1L) * limit + 1;
        long endRow = page * 1L * limit;

        StartEndPageDTO sepDTO = new StartEndPageDTO(startRow, endRow, searchWord);
        List<EmployeeResponse> list = employeeMapper.employeeAllSelect(sepDTO);
        int total = employeeMapper.employeeCount(searchWord);

        int totalPages = (total > 0) ? (int) Math.ceil((double) total / limit) : 0;
        int pageBlock = 5;
        int startPage = ((page - 1) / pageBlock) * pageBlock + 1;
        int endPage = Math.min(startPage + pageBlock - 1, totalPages);

        if (totalPages == 0 || endPage < startPage) {
            endPage = startPage;
        }

        return AdminEmployeePageResponse.builder().items(list).page(page).size(limit)
                .total(total).totalPages(totalPages).searchWord(searchWord)
                .startPage(startPage)
                .endPage(endPage)
                .build();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeDetail(String empNum) {
        EmployeeResponse employee = employeeMapper.selectByEmpNum(empNum);
        if (employee != null) {
            employee.setEmpJumin(SensitiveDataMasker.maskJuminForDisplay(employee.getEmpJumin()));
        }
        return employee;
    }

    public void createEmployee(EmployeeUpdateRequest command) {
        validateNewEmployee(command.getEmpId(), command.getEmpEmail());

        EmployeeResponse dto = new EmployeeResponse();
        dto.setEmpNum(command.getEmpNum());
        dto.setEmpId(command.getEmpId());
        dto.setEmpPw(passwordEncoder.encode(command.getEmpPw()));
        dto.setEmpName(command.getEmpName());
        dto.setEmpJumin(SensitiveDataMasker.maskJuminForStorage(command.getEmpJumin()));
        dto.setEmpPhone(command.getEmpPhone());
        dto.setEmpEmail(command.getEmpEmail());
        dto.setEmpAddr(command.getEmpAddr());
        dto.setEmpAddrDetail(command.getEmpAddrDetail());
        dto.setEmpPost(command.getEmpPost());
        dto.setEmpHireDate(command.getEmpHireDate());

        employeeMapper.employeeInsert(dto);
    }

    public void updateEmployee(EmployeeUpdateRequest command) {
        EmployeeResponse existingInfo = employeeMapper.selectByEmpNum(command.getEmpNum());
        if (existingInfo == null) {
            throw new IllegalArgumentException("수정할 직원 정보를 찾을 수 없습니다.");
        }

        validateChangedEmail(existingInfo.getEmpEmail(), command.getEmpEmail());

        existingInfo.setEmpName(command.getEmpName());
        if (command.getEmpJumin() != null && !command.getEmpJumin().isBlank()) {
            existingInfo.setEmpJumin(SensitiveDataMasker.maskJuminForStorage(command.getEmpJumin()));
        }
        existingInfo.setEmpPhone(command.getEmpPhone());
        existingInfo.setEmpEmail(command.getEmpEmail());
        existingInfo.setEmpAddr(command.getEmpAddr());
        existingInfo.setEmpAddrDetail(command.getEmpAddrDetail());
        existingInfo.setEmpPost(command.getEmpPost());
        existingInfo.setEmpHireDate(command.getEmpHireDate());

        employeeMapper.employeeUpdate(existingInfo);
    }

    public void deleteEmployees(String[] empNums) {
        employeeMapper.employeesDelete(empNums);
    }

    private void validateNewEmployee(String empId, String empEmail) {
        if (authMapper.idCheckSelectOne(empId) != null) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (authMapper.emailCheckSelectOne(empEmail) != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }

    private void validateChangedEmail(String currentEmail, String newEmail) {
        if (!Objects.equals(currentEmail, newEmail) && authMapper.emailCheckSelectOne(newEmail) != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }
}
