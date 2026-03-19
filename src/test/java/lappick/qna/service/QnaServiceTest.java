package lappick.qna.service;

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

import lappick.member.dto.MemberResponse;
import lappick.member.mapper.MemberMapper;
import lappick.purchase.mapper.PurchaseMapper;
import lappick.qna.domain.Qna;
import lappick.qna.dto.QnaAnswerRequest;
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
    void writeQnaFromMyPage_throwsWhenPurchaseItemKeyIsInvalid() {
        when(memberMapper.selectOneById("member1")).thenReturn(buildMember());

        QnaWriteRequest request = buildWriteRequest("invalid-key");

        assertThatThrownBy(() -> qnaService.writeQnaFromMyPage(request, "member1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(qnaMapper, never()).insertQna(any());
    }

    @Test
    void writeQnaFromMyPage_rejectsItemThatWasNotPurchasedByMember() {
        when(memberMapper.selectOneById("member1")).thenReturn(buildMember());
        when(purchaseMapper.countDeliveredPurchaseItemByMember("20260319-abc12345", "goods_100001", "mem_100001"))
                .thenReturn(0);

        QnaWriteRequest request = buildWriteRequest("20260319-abc12345-goods_100001");

        assertThatThrownBy(() -> qnaService.writeQnaFromMyPage(request, "member1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(qnaMapper, never()).insertQna(any());
    }

    @Test
    void writeQnaFromMyPage_insertsQnaWhenDeliveredPurchaseIsVerified() {
        when(memberMapper.selectOneById("member1")).thenReturn(buildMember());
        when(purchaseMapper.countDeliveredPurchaseItemByMember("20260319-abc12345", "goods_100001", "mem_100001"))
                .thenReturn(1);

        QnaWriteRequest request = buildWriteRequest("20260319-abc12345-goods_100001");

        qnaService.writeQnaFromMyPage(request, "member1");

        verify(qnaMapper).insertQna(any());
    }

    @Test
    void addOrUpdateAnswer_updatesAnswerWhenQnaExists() {
        Qna qna = new Qna();
        qna.setQnaNum(1);
        when(qnaMapper.selectQnaByNum(1)).thenReturn(qna);

        QnaAnswerRequest request = new QnaAnswerRequest();
        request.setQnaNum(1);
        request.setAnswerContent("answer");

        qnaService.addOrUpdateAnswer(request);

        ArgumentCaptor<Qna> captor = ArgumentCaptor.forClass(Qna.class);
        verify(qnaMapper).updateAnswer(captor.capture());
        assertThat(captor.getValue().getAnswerContent()).isEqualTo("answer");
    }

    @Test
    void addOrUpdateAnswer_throwsWhenQnaNotFound() {
        when(qnaMapper.selectQnaByNum(1)).thenReturn(null);

        QnaAnswerRequest request = new QnaAnswerRequest();
        request.setQnaNum(1);
        request.setAnswerContent("answer");

        assertThatThrownBy(() -> qnaService.addOrUpdateAnswer(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(qnaMapper, never()).updateAnswer(any());
    }

    @Test
    void deleteQnaByAdmin_deletesSuccessfully() {
        qnaService.deleteQnaByAdmin(1);

        verify(qnaMapper).deleteQnaByQnaNums(java.util.Collections.singletonList(1));
    }

    private MemberResponse buildMember() {
        MemberResponse member = new MemberResponse();
        member.setMemberNum("mem_100001");
        member.setMemberId("member1");
        member.setMemberBirth(LocalDate.of(1995, 1, 1));
        return member;
    }

    private QnaWriteRequest buildWriteRequest(String purchaseItemKey) {
        QnaWriteRequest request = new QnaWriteRequest();
        request.setPurchaseItemKey(purchaseItemKey);
        request.setQnaType("PRODUCT");
        request.setQnaTitle("Stock question");
        request.setQnaContent("When will it be restocked?");
        return request;
    }
}
