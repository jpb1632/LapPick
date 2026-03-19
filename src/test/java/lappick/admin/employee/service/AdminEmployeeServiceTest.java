package lappick.admin.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        EmployeeUpdateRequest command = new EmployeeUpdateRequest();
        command.setEmpNum("emp_100001");
        command.setEmpId("employee1");
        command.setEmpPw("rawPw");
        command.setEmpName("홍길동");
        command.setEmpJumin("900101-1234567");
        command.setEmpPhone("01012345678");
        command.setEmpEmail("employee1@test.com");

        when(authMapper.idCheckSelectOne("employee1")).thenReturn(null);
        when(authMapper.emailCheckSelectOne("employee1@test.com")).thenReturn(null);
        when(passwordEncoder.encode("rawPw")).thenReturn("encodedPw");

        adminEmployeeService.createEmployee(command);

        ArgumentCaptor<EmployeeResponse> captor = ArgumentCaptor.forClass(EmployeeResponse.class);
        verify(employeeMapper).employeeInsert(captor.capture());
        assertThat(captor.getValue().getEmpJumin()).isEqualTo("900101-1******");
    }

    @Test
    void updateEmployee_keepsExistingMaskedJuminWhenNoNewValueIsEntered() {
        EmployeeUpdateRequest command = new EmployeeUpdateRequest();
        command.setEmpNum("emp_100001");
        command.setEmpName("홍길동");
        command.setEmpJumin("");
        command.setEmpPhone("01012345678");
        command.setEmpEmail("employee1@test.com");

        EmployeeResponse existing = new EmployeeResponse();
        existing.setEmpNum("emp_100001");
        existing.setEmpEmail("employee1@test.com");
        existing.setEmpJumin("900101-1******");

        when(employeeMapper.selectByEmpNum("emp_100001")).thenReturn(existing);

        adminEmployeeService.updateEmployee(command);

        ArgumentCaptor<EmployeeResponse> captor = ArgumentCaptor.forClass(EmployeeResponse.class);
        verify(employeeMapper).employeeUpdate(captor.capture());
        assertThat(captor.getValue().getEmpJumin()).isEqualTo("900101-1******");
    }
}
