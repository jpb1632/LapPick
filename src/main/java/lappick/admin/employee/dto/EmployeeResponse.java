package lappick.admin.employee.dto;

import java.util.Date;
import org.apache.ibatis.type.Alias;
import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
@Alias("EmployeeResponse")
public class EmployeeResponse {
    String empNum;
    String empId;
    String empPw;
    String empName;
    String empAddr;
    String empAddrDetail;
    Integer empPost;
    String empPhone;
    String empEmail; 
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    Date empHireDate;
    String empJumin;
    String maskedEmpJumin;
    String empImage;
}
