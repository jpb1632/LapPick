package lappick.admin.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import lappick.auth.mapper.AuthMapper;
import lappick.cart.mapper.CartMapper;
import lappick.member.dto.MemberResponse;
import lappick.member.mapper.MemberMapper;
import lappick.purchase.mapper.PurchaseMapper;
import lappick.qna.mapper.QnaMapper;
import lappick.review.mapper.ReviewMapper;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private QnaMapper qnaMapper;

    @Mock
    private PurchaseMapper purchaseMapper;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private CartMapper cartMapper;

    private AdminMemberService adminMemberService;

    @BeforeEach
    void setUp() {
        adminMemberService = new AdminMemberService(
                memberMapper,
                authMapper,
                passwordEncoder,
                qnaMapper,
                purchaseMapper,
                reviewMapper,
                cartMapper
        );
    }

    @Test
    void deleteMembers_cleansDependentDataBeforeDelete() {
        when(purchaseMapper.selectMemberNumsWithPurchases(List.of("mem_100001", "mem_100002")))
                .thenReturn(List.of());
        when(memberMapper.memberDelete(List.of("mem_100001", "mem_100002"))).thenReturn(2);

        int deletedCount = adminMemberService.deleteMembers(List.of("mem_100001", "mem_100002"));

        assertThat(deletedCount).isEqualTo(2);
        verify(cartMapper).deleteCartByMemberNums(List.of("mem_100001", "mem_100002"));
        verify(reviewMapper).deleteReviewsByMemberNums(List.of("mem_100001", "mem_100002"));
        verify(qnaMapper).deleteQnaByMemberNums(List.of("mem_100001", "mem_100002"));
    }

    @Test
    void deleteMembers_blocksMemberWithPurchaseHistory() {
        MemberResponse blockedMember = new MemberResponse();
        blockedMember.setMemberNum("mem_100001");
        blockedMember.setMemberId("member1");

        when(purchaseMapper.selectMemberNumsWithPurchases(List.of("mem_100001")))
                .thenReturn(List.of("mem_100001"));
        when(memberMapper.memberSelectOneByNum("mem_100001")).thenReturn(blockedMember);

        assertThatThrownBy(() -> adminMemberService.deleteMembers(List.of("mem_100001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("주문 이력이 있는 회원은 삭제할 수 없습니다.")
                .hasMessageContaining("member1(mem_100001)");

        verify(cartMapper, never()).deleteCartByMemberNums(anyList());
        verify(memberMapper, never()).memberDelete(anyList());
    }
}
