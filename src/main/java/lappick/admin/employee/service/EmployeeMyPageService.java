package lappick.admin.employee.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lappick.admin.employee.dto.EmployeeResponse;
import lappick.admin.employee.mapper.EmployeeMapper;
import lappick.common.util.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeMyPageService {

    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeInfo(String empId) {
        EmployeeResponse employee = employeeMapper.selectByEmpId(empId);
        if (employee != null) {
            employee.setEmpJumin(SensitiveDataMasker.maskJuminForDisplay(employee.getEmpJumin()));
        }
        return employee;
    }
    
    public void changePassword(String empId, String oldPw, String newPw) {
        EmployeeResponse emp = employeeMapper.selectByEmpId(empId);
        if (emp == null || !passwordEncoder.matches(oldPw, emp.getEmpPw())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        String encodedNewPw = passwordEncoder.encode(newPw);
        employeeMapper.employeePwUpdate(encodedNewPw, empId);
    }
    
    public void updateMyInfo(EmployeeResponse updatedInfo, String empId) {
        // 1. DB에서 기존의 완전한 정보를 가져옵니다.
        EmployeeResponse existingInfo = employeeMapper.selectByEmpId(empId);

        // 2. 폼에서 넘어온 수정된 값들을 기존 정보(existingInfo)에 덮어씁니다.
        existingInfo.setEmpName(updatedInfo.getEmpName());
        if (updatedInfo.getEmpJumin() != null && !updatedInfo.getEmpJumin().isBlank()) {
            existingInfo.setEmpJumin(SensitiveDataMasker.maskJuminForStorage(updatedInfo.getEmpJumin()));
        }
        existingInfo.setEmpPhone(updatedInfo.getEmpPhone());
        existingInfo.setEmpEmail(updatedInfo.getEmpEmail());
        existingInfo.setEmpAddr(updatedInfo.getEmpAddr());
        existingInfo.setEmpAddrDetail(updatedInfo.getEmpAddrDetail());
        existingInfo.setEmpPost(updatedInfo.getEmpPost());
        
        // 입사일(empHireDate)은 직원이 직접 수정할 수 없으므로,
        // 폼에서 넘어온 값으로 덮어쓰지 않고 기존 DB 값을 그대로 유지합니다.

        // 3. 완전한 데이터가 담긴 DTO로 업데이트를 수행합니다.
        employeeMapper.employeeUpdate(existingInfo);
    }
}
