package lappick.admin.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

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
    void createMember_throwsWhenMemberIdIsDuplicated() {
        MemberUpdateRequest command = createMemberCommand();
        when(authMapper.idCheckSelectOne("member1")).thenReturn(1);

        assertThatThrownBy(() -> adminMemberService.createMember(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(authMapper, never()).userInsert(any());
    }

    @Test
    void createMember_throwsWhenMemberEmailIsDuplicated() {
        MemberUpdateRequest command = createMemberCommand();
        when(authMapper.idCheckSelectOne("member1")).thenReturn(null);
        when(authMapper.emailCheckSelectOne("member1@test.com")).thenReturn(1);

        assertThatThrownBy(() -> adminMemberService.createMember(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(authMapper, never()).userInsert(any());
    }

    @Test
    void updateMember_throwsWhenChangedEmailIsDuplicated() {
        MemberUpdateRequest command = createMemberCommand();
        command.setMemberNum("mem_100001");
        command.setMemberEmail("duplicate@test.com");

        MemberResponse existing = new MemberResponse();
        existing.setMemberNum("mem_100001");
        existing.setMemberEmail("member1@test.com");

        when(memberMapper.memberSelectOneByNum("mem_100001")).thenReturn(existing);
        when(authMapper.emailCheckSelectOne("duplicate@test.com")).thenReturn(1);

        assertThatThrownBy(() -> adminMemberService.updateMember(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(memberMapper, never()).memberUpdate(any());
    }

    @Test
    void deleteMembers_cleansDependentDataBeforeDelete() {
        when(purchaseMapper.selectMembersWithPurchases(List.of("mem_100001", "mem_100002")))
                .thenReturn(List.of());
        when(memberMapper.memberDelete(List.of("mem_100001", "mem_100002"))).thenReturn(2);

        int deletedCount = adminMemberService.deleteMembers(List.of("mem_100001", "mem_100002"));

        assertThat(deletedCount).isEqualTo(2);
        verify(cartMapper).deleteCartByMemberNums(List.of("mem_100001", "mem_100002"));
        verify(reviewMapper).deleteReviewsByMemberNums(List.of("mem_100001", "mem_100002"));
        verify(qnaMapper).deleteQnaByMemberNums(List.of("mem_100001", "mem_100002"));
    }

    @Test
    void deleteMembers_ignoresNullBlankAndDuplicateMemberNums() {
        when(purchaseMapper.selectMembersWithPurchases(List.of("mem_100001"))).thenReturn(List.of());
        when(memberMapper.memberDelete(List.of("mem_100001"))).thenReturn(1);

        int deletedCount = adminMemberService.deleteMembers(Arrays.asList(null, "", "mem_100001", " ", "mem_100001"));

        assertThat(deletedCount).isEqualTo(1);
        verify(cartMapper).deleteCartByMemberNums(List.of("mem_100001"));
        verify(memberMapper).memberDelete(List.of("mem_100001"));
    }

    @Test
    void deleteMembers_blocksMemberWithPurchaseHistory() {
        when(purchaseMapper.selectMembersWithPurchases(List.of("mem_100001")))
                .thenReturn(List.of(createBlockedMember("mem_100001", "member1")));

        assertThatThrownBy(() -> adminMemberService.deleteMembers(List.of("mem_100001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("member1(mem_100001)");

        verify(cartMapper, never()).deleteCartByMemberNums(anyList());
        verify(memberMapper, never()).memberDelete(anyList());
    }

    @Test
    void deleteMembers_truncatesBlockedMemberMessageAfterThreeMembers() {
        List<MemberResponse> blockedMembers = List.of(
                createBlockedMember("mem_100001", "member1"),
                createBlockedMember("mem_100002", "member2"),
                createBlockedMember("mem_100003", "member3"),
                createBlockedMember("mem_100004", "member4")
        );
        when(purchaseMapper.selectMembersWithPurchases(List.of("mem_100001", "mem_100002", "mem_100003", "mem_100004")))
                .thenReturn(blockedMembers);

        Throwable thrown = catchThrowable(() ->
                adminMemberService.deleteMembers(List.of("mem_100001", "mem_100002", "mem_100003", "mem_100004"))
        );

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageContaining("member1(mem_100001)");
        assertThat(thrown).hasMessageContaining("member3(mem_100003)");
        assertThat(thrown.getMessage()).doesNotContain("member4(mem_100004)");
    }

    @Test
    void createMember_encodesPasswordBeforeInsert() {
        MemberUpdateRequest command = createMemberCommand();
        when(authMapper.idCheckSelectOne("member1")).thenReturn(null);
        when(authMapper.emailCheckSelectOne("member1@test.com")).thenReturn(null);
        when(passwordEncoder.encode("rawPw")).thenReturn("encodedPw");

        adminMemberService.createMember(command);

        ArgumentCaptor<MemberResponse> captor = ArgumentCaptor.forClass(MemberResponse.class);
        verify(authMapper).userInsert(captor.capture());
        assertThat(captor.getValue().getMemberPw()).isEqualTo("encodedPw");
    }

    private MemberUpdateRequest createMemberCommand() {
        MemberUpdateRequest command = new MemberUpdateRequest();
        command.setMemberNum("mem_100001");
        command.setMemberId("member1");
        command.setMemberPw("rawPw");
        command.setMemberName("Tester");
        command.setMemberAddr("Seoul");
        command.setMemberAddrDetail("101");
        command.setMemberPost("12345");
        command.setMemberPhone1("01012345678");
        command.setMemberPhone2("0212345678");
        command.setGender("M");
        command.setMemberBirth(LocalDate.of(1995, 1, 1));
        command.setMemberEmail("member1@test.com");
        return command;
    }

    private MemberResponse createBlockedMember(String memberNum, String memberId) {
        MemberResponse blockedMember = new MemberResponse();
        blockedMember.setMemberNum(memberNum);
        blockedMember.setMemberId(memberId);
        return blockedMember;
    }
}
