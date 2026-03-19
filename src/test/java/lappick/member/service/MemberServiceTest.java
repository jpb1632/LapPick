package lappick.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import lappick.auth.mapper.AuthMapper;
import lappick.cart.mapper.CartMapper;
import lappick.member.dto.MemberResponse;
import lappick.member.dto.MemberUpdateRequest;
import lappick.member.mapper.MemberMapper;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private CartMapper cartMapper;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(
                memberMapper,
                passwordEncoder,
                authMapper,
                cartMapper
        );
    }

    @Test
    void withdrawMember_softDeletesMemberEvenWhenPurchaseHistoryExists() {
        when(authMapper.selectPwById("member1")).thenReturn("encodedPw");
        when(passwordEncoder.matches("rawPw", "encodedPw")).thenReturn(true);
        when(memberMapper.memberNumSelect("member1")).thenReturn("mem_100001");
        when(memberMapper.softWithdrawMember("member1", "withdrawn_mem_100001")).thenReturn(1);

        memberService.withdrawMember("member1", "rawPw");

        verify(cartMapper).cartAllDelete("mem_100001");
        verify(authMapper).deletePersistentLoginsByUsername("member1");
        verify(memberMapper).softWithdrawMember("member1", "withdrawn_mem_100001");
    }

    @Test
    void withdrawMember_throwsWhenPasswordIsWrong() {
        when(authMapper.selectPwById("member1")).thenReturn("encodedPw");
        when(passwordEncoder.matches("wrongPw", "encodedPw")).thenReturn(false);

        assertThatThrownBy(() -> memberService.withdrawMember("member1", "wrongPw"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(memberMapper, never()).softWithdrawMember(any(), any());
        verify(cartMapper, never()).cartAllDelete(any());
    }

    @Test
    void withdrawMember_throwsWhenSoftWithdrawFails() {
        when(authMapper.selectPwById("member1")).thenReturn("encodedPw");
        when(passwordEncoder.matches("rawPw", "encodedPw")).thenReturn(true);
        when(memberMapper.memberNumSelect("member1")).thenReturn("mem_100001");
        when(memberMapper.softWithdrawMember("member1", "withdrawn_mem_100001")).thenReturn(0);

        assertThatThrownBy(() -> memberService.withdrawMember("member1", "rawPw"))
                .isInstanceOf(IllegalStateException.class);

        verify(cartMapper).cartAllDelete("mem_100001");
        verify(authMapper).deletePersistentLoginsByUsername("member1");
    }

    @Test
    void updateMyInfo_updatesCorrectFields() {
        MemberUpdateRequest request = new MemberUpdateRequest();
        request.setMemberPw("rawPw");
        request.setMemberName("Updated Name");
        request.setMemberAddr("Seoul");
        request.setMemberAddrDetail("201");
        request.setMemberPost("12345");
        request.setMemberPhone1("01098765432");
        request.setMemberPhone2("0212345678");
        request.setMemberEmail("updated@test.com");
        request.setMemberBirth(LocalDate.of(1992, 2, 2));
        request.setGender("F");

        MemberResponse currentMember = new MemberResponse();
        currentMember.setMemberId("member1");
        currentMember.setMemberEmail("before@test.com");

        when(authMapper.selectPwById("member1")).thenReturn("encodedPw");
        when(passwordEncoder.matches("rawPw", "encodedPw")).thenReturn(true);
        when(memberMapper.selectMemberById("member1")).thenReturn(currentMember);
        when(authMapper.emailCheckSelectOne("updated@test.com")).thenReturn(null);

        memberService.updateMyInfo(request, "member1");

        ArgumentCaptor<MemberResponse> captor = ArgumentCaptor.forClass(MemberResponse.class);
        verify(memberMapper).memberUpdate(captor.capture());

        MemberResponse updatedMember = captor.getValue();
        assertThat(updatedMember.getMemberId()).isEqualTo("member1");
        assertThat(updatedMember.getMemberName()).isEqualTo("Updated Name");
        assertThat(updatedMember.getMemberEmail()).isEqualTo("updated@test.com");
        assertThat(updatedMember.getMemberBirth()).isEqualTo(LocalDate.of(1992, 2, 2));
        assertThat(updatedMember.getGender()).isEqualTo("F");
    }
}
