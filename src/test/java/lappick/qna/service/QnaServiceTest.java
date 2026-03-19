package lappick.qna.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lappick.member.dto.MemberResponse;
import lappick.member.mapper.MemberMapper;
import lappick.purchase.mapper.PurchaseMapper;
import lappick.qna.dto.QnaWriteRequest;
import lappick.qna.mapper.QnaMapper;

@ExtendWith(MockitoExtension.class)
class QnaServiceTest {

    @Mock
    private QnaMapper qnaMapper;

    @Mock
    private PurchaseMapper purchaseMapper;

    @Mock
    private MemberMapper memberMapper;

    private QnaService qnaService;

    @BeforeEach
    void setUp() {
        qnaService = new QnaService(qnaMapper, purchaseMapper, memberMapper);
    }

    @Test
    void writeQnaFromMyPage_rejectsItemThatWasNotPurchasedByMember() {
        MemberResponse member = buildMember();
        when(memberMapper.selectOneById("member1")).thenReturn(member);
        when(purchaseMapper.countDeliveredPurchaseItemByMember("20260319-abc12345", "goods_100001", "mem_100001"))
                .thenReturn(0);

        QnaWriteRequest request = buildRequest("20260319-abc12345-goods_100001");

        assertThatThrownBy(() -> qnaService.writeQnaFromMyPage(request, "member1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("구매가 확인된 상품만 문의할 수 있습니다.");

        verify(qnaMapper, never()).insertQna(any());
    }

    @Test
    void writeQnaFromMyPage_insertsQnaWhenDeliveredPurchaseIsVerified() {
        MemberResponse member = buildMember();
        when(memberMapper.selectOneById("member1")).thenReturn(member);
        when(purchaseMapper.countDeliveredPurchaseItemByMember("20260319-abc12345", "goods_100001", "mem_100001"))
                .thenReturn(1);

        QnaWriteRequest request = buildRequest("20260319-abc12345-goods_100001");

        qnaService.writeQnaFromMyPage(request, "member1");

        verify(qnaMapper).insertQna(any());
    }

    private MemberResponse buildMember() {
        MemberResponse member = new MemberResponse();
        member.setMemberNum("mem_100001");
        member.setMemberId("member1");
        member.setMemberBirth(LocalDate.of(1995, 1, 1));
        return member;
    }

    private QnaWriteRequest buildRequest(String purchaseItemKey) {
        QnaWriteRequest request = new QnaWriteRequest();
        request.setPurchaseItemKey(purchaseItemKey);
        request.setQnaType("상품문의");
        request.setQnaTitle("재고 문의");
        request.setQnaContent("이 상품 재입고 예정이 있나요?");
        return request;
    }
}
