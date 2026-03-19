package lappick.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import lappick.auth.mapper.AuthMapper;
import lappick.cart.mapper.CartMapper;
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
        verify(memberMapper, never()).deleteMemberById("member1");
    }

    @Test
    void withdrawMember_throwsWhenSoftWithdrawFails() {
        when(authMapper.selectPwById("member1")).thenReturn("encodedPw");
        when(passwordEncoder.matches("rawPw", "encodedPw")).thenReturn(true);
        when(memberMapper.memberNumSelect("member1")).thenReturn("mem_100001");
        when(memberMapper.softWithdrawMember("member1", "withdrawn_mem_100001")).thenReturn(0);

        assertThatThrownBy(() -> memberService.withdrawMember("member1", "rawPw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("탈퇴 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");

        verify(cartMapper).cartAllDelete("mem_100001");
        verify(authMapper).deletePersistentLoginsByUsername("member1");
        verify(memberMapper, never()).deleteMemberById("member1");
    }
}
