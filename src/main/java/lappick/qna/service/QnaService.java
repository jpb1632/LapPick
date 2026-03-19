package lappick.qna.service;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lappick.common.dto.PageData;
import lappick.member.dto.MemberResponse;
import lappick.member.mapper.MemberMapper;
import lappick.purchase.dto.PurchaseItemResponse;
import lappick.purchase.mapper.PurchaseMapper;
import lappick.qna.domain.Qna;
import lappick.qna.dto.QnaAnswerRequest;
import lappick.qna.dto.QnaResponse;
import lappick.qna.dto.QnaWriteRequest;
import lappick.qna.mapper.QnaMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class QnaService {

    private static final Logger log = LoggerFactory.getLogger(QnaService.class);

    private final QnaMapper qnaMapper;
    private final PurchaseMapper purchaseMapper;
    private final MemberMapper memberMapper;

    /**
     * 나의 Q&A 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public PageData<QnaResponse> getMyQnaListPage(String memberId, String searchWord, String status, int page, int size) {
    	MemberResponse member = memberMapper.selectOneById(memberId);
        if (member == null) {
            log.warn("getMyQnaListPage: Member not found for ID {}", memberId);
            // 빈 페이지 반환
            return new PageData<>(List.of(), page, size, 0, searchWord);
        }
        String memberNum = member.getMemberNum();

        Map<String, Object> params = new HashMap<>();
        params.put("memberNum", memberNum);
        params.put("searchWord", searchWord);
        params.put("status", status);
        params.put("startRow", (long)(page - 1) * size + 1);
        params.put("endRow", (long)page * size);

        List<QnaResponse> list = qnaMapper.selectQnaByMemberNum(params);
        int total = qnaMapper.countMyQna(params);

        return new PageData<>(list, page, size, total, searchWord);
    }

    /**
     * '나의 문의' 페이지에서 문의 작성 (구매 상품 선택)
     */
    @Transactional
    public void writeQnaFromMyPage(QnaWriteRequest request, String memberId) {
        MemberResponse member = getMemberResponse(memberId); // 사용자 조회 및 검증

        // purchaseItemKey (purchaseNum-goodsNum) 분리
        String purchaseNum = null;
        String goodsNum = null;
        if (request.getPurchaseItemKey() != null) {
            int lastHyphenIndex = request.getPurchaseItemKey().lastIndexOf('-');
            if (lastHyphenIndex != -1) {
                purchaseNum = request.getPurchaseItemKey().substring(0, lastHyphenIndex);
                goodsNum = request.getPurchaseItemKey().substring(lastHyphenIndex + 1);
            } else {
                 log.error("Invalid purchaseItemKey format: {}", request.getPurchaseItemKey());
                 throw new IllegalArgumentException("선택된 주문 상품 정보가 올바르지 않습니다.");
            }
        } else {
             throw new IllegalArgumentException("문의할 주문 상품을 선택해주세요.");
        }

        int deliveredPurchaseCount = purchaseMapper.countDeliveredPurchaseItemByMember(
                purchaseNum,
                goodsNum,
                member.getMemberNum()
        );
        if (deliveredPurchaseCount == 0) {
            throw new IllegalArgumentException("구매가 확인된 상품만 문의할 수 있습니다.");
        }

        Qna qna = new Qna();
        qna.setMemberNum(member.getMemberNum());
        qna.setGoodsNum(goodsNum);
        qna.setQnaType(request.getQnaType());
        qna.setQnaTitle(request.getQnaTitle());
        qna.setQnaContent(request.getQnaContent());
        // qnaStatus는 DB 기본값 또는 Mapper에서 설정 ('답변대기')

        qnaMapper.insertQna(qna);
        log.info("QnA 작성 완료 (from MyPage): qnaNum={}, memberNum={}, goodsNum={}", qna.getQnaNum(), member.getMemberNum(), goodsNum);
    }


    /**
     * 상품 상세 페이지에서 문의 작성 (구매 이력 무관)
     */
    @Transactional
    public void writeQnaFromProductPage(QnaWriteRequest request, String memberId) {
        MemberResponse member = getMemberResponse(memberId); // 사용자 조회 및 검증

        Qna qna = new Qna();
        qna.setMemberNum(member.getMemberNum());
        qna.setGoodsNum(request.getGoodsNum());
        qna.setQnaType(request.getQnaType());
        qna.setQnaTitle(request.getQnaTitle());
        qna.setQnaContent(request.getQnaContent());
        // qnaStatus는 DB 기본값 또는 Mapper에서 설정 ('답변대기')

        qnaMapper.insertQna(qna);
        log.info("QnA 작성 완료 (from ProductPage): qnaNum={}, memberNum={}, goodsNum={}", qna.getQnaNum(), member.getMemberNum(), request.getGoodsNum());
    }

    /**
     * 관리자용 Q&A 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public PageData<QnaResponse> getAllQnaListPage(String searchWord, String status, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("searchWord", searchWord);
        params.put("status", status);
        params.put("startRow", (long)(page - 1) * size + 1);
        params.put("endRow", (long)page * size);

        List<QnaResponse> list = qnaMapper.selectAllQna(params);
        int total = qnaMapper.countAllQna(params);

        return new PageData<>(list, page, size, total, searchWord);
    }

    /**
     * Q&A 답변 등록/수정
     */
    @Transactional
    public void addOrUpdateAnswer(QnaAnswerRequest request) {
        // 답변 권한 확인 로직 추가 가능 (예: 현재 사용자가 관리자인지)

        Qna qna = qnaMapper.selectQnaByNum(request.getQnaNum()); // 답변 대상 Q&A 조회
        if (qna == null) {
            log.error("답변 대상 QnA 없음: qnaNum={}", request.getQnaNum());
            throw new IllegalArgumentException("답변할 문의를 찾을 수 없습니다.");
        }

        qna.setAnswerContent(request.getAnswerContent());
        // qna.setAnswerDate(new Date()); // Mapper에서 SYSDATE 사용
        // qna.setQnaStatus("답변완료"); // Mapper에서 설정

        qnaMapper.updateAnswer(qna);
        log.info("QnA 답변 등록/수정 완료: qnaNum={}", request.getQnaNum());
    }

    /**
     * 사용자 ID로 MemberResponse 조회 및 예외 처리 (헬퍼 메소드)
     */
    private MemberResponse getMemberResponse(String memberId) {
        MemberResponse member = memberMapper.selectOneById(memberId);
        if (member == null) {
            log.warn("Member not found for ID: {}", memberId);
            throw new SecurityException("사용자 정보를 찾을 수 없습니다. 로그인이 필요합니다.");
        }
        return member;
    }

    /**
     * 구매한 상품 목록 조회 (나의 문의 작성 시 상품 선택용)
     */
     @Transactional(readOnly = true)
     public List<PurchaseItemResponse> getPurchasedItemsForQna(String memberId) {
         MemberResponse member = getMemberResponse(memberId);
         // PurchaseMapper 직접 사용
         return purchaseMapper.selectPurchasedItemsByMemberNum(member.getMemberNum());
     }
     
     @Transactional(readOnly = true)
     public PageData<QnaResponse> getQnaPageByGoodsNum(String goodsNum, int page, int size) {
         Map<String, Object> params = new HashMap<>();
         params.put("goodsNum", goodsNum);
         params.put("startRow", (long)(page - 1) * size + 1);
         params.put("endRow", (long)page * size);

         List<QnaResponse> list = qnaMapper.selectQnaByGoodsNum(params);
         int total = qnaMapper.countQnaByGoodsNum(goodsNum);

         return new PageData<>(list, page, size, total, null); // searchWord는 사용 안 함
     }
     
     @Transactional
     public void deleteQnaByAdmin(List<Integer> qnaNums) {
         if (qnaNums == null || qnaNums.isEmpty()) {
             log.warn("삭제할 QnA가 선택되지 않았습니다.");
             return; // 삭제할 항목이 없으면 중단
         }
         
         // QnA 답변이 먼저 삭제되어야 하는 등의 FK 제약조건이 있다면 여기에 로직 추가
         
         int deletedCount = qnaMapper.deleteQnaByQnaNums(qnaNums);
         log.info("관리자에 의해 {}개의 QnA가 삭제되었습니다.", deletedCount);
     }
     
     // 단일 QnA 삭제 헬퍼
     @Transactional
     public void deleteQnaByAdmin(Integer qnaNum) {
         this.deleteQnaByAdmin(Collections.singletonList(qnaNum));
     }
     
}
