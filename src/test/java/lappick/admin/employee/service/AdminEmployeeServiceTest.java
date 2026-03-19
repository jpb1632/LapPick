package lappick.admin.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import lappick.admin.employee.dto.EmployeeResponse;
import lappick.admin.employee.dto.EmployeeUpdateRequest;
import lappick.admin.employee.mapper.EmployeeMapper;
import lappick.auth.mapper.AuthMapper;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminEmployeeService adminEmployeeService;

    @BeforeEach
    void setUp() {
        adminEmployeeService = new AdminEmployeeService(employeeMapper, authMapper, passwordEncoder);
    }

    @Test
    void createEmployee_masksJuminBeforeInsert() {
        EmployeeUpdateRequest command = createCreateCommand();

        when(authMapper.idCheckSelectOne("employee1")).thenReturn(null);
        when(authMapper.emailCheckSelectOne("employee1@test.com")).thenReturn(null);
        when(passwordEncoder.encode("rawPw")).thenReturn("encodedPw");

        adminEmployeeService.createEmployee(command);

        ArgumentCaptor<EmployeeResponse> captor = ArgumentCaptor.forClass(EmployeeResponse.class);
        verify(employeeMapper).employeeInsert(captor.capture());
        assertThat(captor.getValue().getEmpJumin()).isEqualTo("900101-1******");
    }

    @Test
    void createEmployee_throwsWhenEmpIdIsDuplicated() {
        EmployeeUpdateRequest command = createCreateCommand();
        when(authMapper.idCheckSelectOne("employee1")).thenReturn(1);

        assertThatThrownBy(() -> adminEmployeeService.createEmployee(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(employeeMapper, never()).employeeInsert(any());
    }

    @Test
    void createEmployee_throwsWhenEmpEmailIsDuplicated() {
        EmployeeUpdateRequest command = createCreateCommand();
        when(authMapper.idCheckSelectOne("employee1")).thenReturn(null);
        when(authMapper.emailCheckSelectOne("employee1@test.com")).thenReturn(1);

        assertThatThrownBy(() -> adminEmployeeService.createEmployee(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(employeeMapper, never()).employeeInsert(any());
    }

    @Test
    void updateEmployee_keepsExistingMaskedJuminWhenNoNewValueIsEntered() {
        EmployeeUpdateRequest command = createUpdateCommand();
        command.setEmpJumin("");

        EmployeeResponse existing = createExistingEmployee();
        existing.setEmpJumin("900101-1******");
        when(employeeMapper.selectByEmpNum("emp_100001")).thenReturn(existing);

        adminEmployeeService.updateEmployee(command);

        ArgumentCaptor<EmployeeResponse> captor = ArgumentCaptor.forClass(EmployeeResponse.class);
        verify(employeeMapper).employeeUpdate(captor.capture());
        assertThat(captor.getValue().getEmpJumin()).isEqualTo("900101-1******");
    }

    @Test
    void updateEmployee_throwsWhenChangedEmailIsDuplicated() {
        EmployeeUpdateRequest command = createUpdateCommand();
        command.setEmpEmail("duplicate@test.com");

        when(employeeMapper.selectByEmpNum("emp_100001")).thenReturn(createExistingEmployee());
        when(authMapper.emailCheckSelectOne("duplicate@test.com")).thenReturn(1);

        assertThatThrownBy(() -> adminEmployeeService.updateEmployee(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(employeeMapper, never()).employeeUpdate(any());
    }

    @Test
    void updateEmployee_throwsWhenEmployeeNotFound() {
        when(employeeMapper.selectByEmpNum("emp_100001")).thenReturn(null);

        assertThatThrownBy(() -> adminEmployeeService.updateEmployee(createUpdateCommand()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(employeeMapper, never()).employeeUpdate(any());
    }

    @Test
    void getEmployeeDetail_returnsMaskedJumin() {
        EmployeeResponse employee = createExistingEmployee();
        employee.setEmpJumin("9001011234567");
        when(employeeMapper.selectByEmpNum("emp_100001")).thenReturn(employee);

        EmployeeResponse result = adminEmployeeService.getEmployeeDetail("emp_100001");

        assertThat(result.getEmpJumin()).isEqualTo("900101-1******");
    }

    @Test
    void deleteEmployees_delegatesToMapper() {
        String[] empNums = {"emp_100001", "emp_100002"};

        adminEmployeeService.deleteEmployees(empNums);

        verify(employeeMapper).employeesDelete(empNums);
    }

    private EmployeeUpdateRequest createCreateCommand() {
        EmployeeUpdateRequest command = new EmployeeUpdateRequest();
        command.setEmpNum("emp_100001");
        command.setEmpId("employee1");
        command.setEmpPw("rawPw");
        command.setEmpName("Tester");
        command.setEmpJumin("900101-1234567");
        command.setEmpPhone("01012345678");
        command.setEmpEmail("employee1@test.com");
        command.setEmpAddr("Seoul");
        command.setEmpAddrDetail("101");
        command.setEmpPost(12345);
        command.setEmpHireDate(new Date());
        return command;
    }

    private EmployeeUpdateRequest createUpdateCommand() {
        EmployeeUpdateRequest command = new EmployeeUpdateRequest();
        command.setEmpNum("emp_100001");
        command.setEmpName("Tester");
        command.setEmpJumin("900101-1234567");
        command.setEmpPhone("01012345678");
        command.setEmpEmail("employee1@test.com");
        command.setEmpAddr("Seoul");
        command.setEmpAddrDetail("101");
        command.setEmpPost(12345);
        command.setEmpHireDate(new Date());
        return command;
    }

    private EmployeeResponse createExistingEmployee() {
        EmployeeResponse existing = new EmployeeResponse();
        existing.setEmpNum("emp_100001");
        existing.setEmpId("employee1");
        existing.setEmpName("Tester");
        existing.setEmpEmail("employee1@test.com");
        existing.setEmpPhone("01012345678");
        existing.setEmpAddr("Seoul");
        existing.setEmpAddrDetail("101");
        existing.setEmpPost(12345);
        existing.setEmpHireDate(new Date());
        return existing;
    }
}
