package lappick.admin.member.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lappick.admin.member.dto.AdminMemberPageResponse;
import lappick.auth.mapper.AuthMapper;
import lappick.cart.mapper.CartMapper;
import lappick.common.dto.StartEndPageDTO;
import lappick.member.dto.MemberResponse;
import lappick.member.dto.MemberUpdateRequest;
import lappick.member.mapper.MemberMapper;
import lappick.purchase.mapper.PurchaseMapper;
import lappick.qna.mapper.QnaMapper;
import lappick.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberMapper memberMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final QnaMapper qnaMapper;
    private final PurchaseMapper purchaseMapper;
    private final ReviewMapper reviewMapper;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public AdminMemberPageResponse getMemberListPage(Integer page, Integer size, String searchWord) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 5 : size;

        long startRow = (p - 1L) * s + 1;
        long endRow = p * 1L * s;

        StartEndPageDTO sep = new StartEndPageDTO(startRow, endRow, searchWord);
        List<MemberResponse> list = memberMapper.memberSelectList(sep);
        int total = memberMapper.memberCountBySearch(searchWord);

        int totalPages = (total > 0) ? (int) Math.ceil((double) total / s) : 0;
        int pageBlock = 5;
        int startPage = ((p - 1) / pageBlock) * pageBlock + 1;
        int endPage = Math.min(startPage + pageBlock - 1, totalPages);

        if (totalPages == 0 || endPage < startPage) {
            endPage = startPage;
        }

        return AdminMemberPageResponse.builder()
                .items(list)
                .page(p).size(s)
                .total(total).totalPages(totalPages)
                .searchWord(searchWord)
                .startPage(startPage)
                .endPage(endPage)
                .build();
    }

    @Transactional(readOnly = true)
    public MemberResponse getMemberDetail(String memberNum) {
        return memberMapper.memberSelectOneByNum(memberNum);
    }

    public void createMember(MemberUpdateRequest command) {
        validateNewMember(command.getMemberId(), command.getMemberEmail());

        MemberResponse dto = new MemberResponse();
        dto.setMemberId(command.getMemberId());
        dto.setMemberName(command.getMemberName());
        dto.setMemberAddr(command.getMemberAddr());
        dto.setMemberAddrDetail(command.getMemberAddrDetail());
        dto.setMemberPost(command.getMemberPost());
        dto.setGender(command.getGender());
        dto.setMemberPhone1(command.getMemberPhone1());
        dto.setMemberPhone2(command.getMemberPhone2());
        dto.setMemberEmail(command.getMemberEmail());
        dto.setMemberBirth(command.getMemberBirth());
        dto.setMemberPw(passwordEncoder.encode(command.getMemberPw()));

        authMapper.userInsert(dto);
    }

    public void updateMember(MemberUpdateRequest command) {
        MemberResponse existingInfo = memberMapper.memberSelectOneByNum(command.getMemberNum());
        if (existingInfo == null) {
            throw new IllegalArgumentException("수정할 회원 정보를 찾을 수 없습니다.");
        }

        validateChangedEmail(existingInfo.getMemberEmail(), command.getMemberEmail());

        existingInfo.setMemberName(command.getMemberName());
        existingInfo.setMemberAddr(command.getMemberAddr());
        existingInfo.setMemberAddrDetail(command.getMemberAddrDetail());
        existingInfo.setMemberPost(command.getMemberPost());
        existingInfo.setGender(command.getGender());
        existingInfo.setMemberPhone1(command.getMemberPhone1());
        existingInfo.setMemberPhone2(command.getMemberPhone2());
        existingInfo.setMemberEmail(command.getMemberEmail());
        existingInfo.setMemberBirth(command.getMemberBirth());

        memberMapper.memberUpdate(existingInfo);
    }

    public int deleteMembers(List<String> memberNums) {
        List<String> targetMemberNums = memberNums.stream()
                .filter(memberNum -> memberNum != null && !memberNum.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (targetMemberNums.isEmpty()) {
            throw new IllegalArgumentException("삭제할 회원을 선택해주세요.");
        }

        List<MemberResponse> blockedMembers = purchaseMapper.selectMembersWithPurchases(targetMemberNums);
        if (!blockedMembers.isEmpty()) {
            throw new IllegalStateException(buildDeleteBlockedMessage(blockedMembers));
        }

        cartMapper.deleteCartByMemberNums(targetMemberNums);
        reviewMapper.deleteReviewsByMemberNums(targetMemberNums);
        qnaMapper.deleteQnaByMemberNums(targetMemberNums);
        return memberMapper.memberDelete(targetMemberNums);
    }

    private void validateNewMember(String memberId, String memberEmail) {
        if (authMapper.idCheckSelectOne(memberId) != null) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (authMapper.emailCheckSelectOne(memberEmail) != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }

    private void validateChangedEmail(String currentEmail, String newEmail) {
        if (!Objects.equals(currentEmail, newEmail) && authMapper.emailCheckSelectOne(newEmail) != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }

    private String buildDeleteBlockedMessage(List<MemberResponse> blockedMembers) {
        String blockedMembersMessage = blockedMembers.stream()
                .limit(3)
                .map(member -> member.getMemberId() + "(" + member.getMemberNum() + ")")
                .collect(Collectors.joining(", "));

        if (blockedMembers.size() > 3) {
            blockedMembersMessage += " 외 " + (blockedMembers.size() - 3) + "명";
        }

        return "주문 이력이 있는 회원은 삭제할 수 없습니다. " + blockedMembersMessage;
    }
}
