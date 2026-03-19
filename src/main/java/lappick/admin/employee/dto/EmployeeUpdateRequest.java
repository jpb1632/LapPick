package lappick.admin.employee.dto;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.*;
import lappick.config.ValidationGroups;
import lombok.Data;

@Data
public class EmployeeUpdateRequest {
    String empNum;

    @NotEmpty(message = "아이디를 입력해주세요.", groups = ValidationGroups.Create.class)
    @Size(min = 5, max = 12, groups = ValidationGroups.Create.class)
    String empId;

    @NotEmpty(message = "비밀번호를 입력하여 주세요.", groups = ValidationGroups.Create.class)
    String empPw;

    @NotEmpty(message = "비밀번호확인 입력하여 주세요.", groups = ValidationGroups.Create.class)
    String empPwCon;

    @NotBlank(message = "이름을 입력하여 주세요.", groups = {ValidationGroups.Create.class, ValidationGroups.Update.class})
    String empName;

    @NotBlank(message = "주소를 입력하여 주세요.", groups = {ValidationGroups.Create.class, ValidationGroups.Update.class})
    String empAddr;

    String empAddrDetail;
    Integer empPost;

    @NotBlank(message = "연락처을 입력하여 주세요.", groups = {ValidationGroups.Create.class, ValidationGroups.Update.class})
    String empPhone;

    @Email(message = "형식에 맞지 않습니다.", groups = {ValidationGroups.Create.class, ValidationGroups.Update.class})
    @NotEmpty(message = "이메일을 입력하여 주세요.", groups = {ValidationGroups.Create.class, ValidationGroups.Update.class})
    String empEmail;

    @NotEmpty(message = "주민번호를 입력하여 주세요.", groups = ValidationGroups.Create.class)
    String empJumin;

    @NotNull(message = "입사일을 입력하여 주세요.", groups = {ValidationGroups.Create.class, ValidationGroups.Update.class})
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    Date empHireDate;

    public boolean isEmpPwEqualsEmpPwCon() {
        if (empPw == null || empPwCon == null) {
            return false;
        }
        return empPw.equals(empPwCon);
    }
}
