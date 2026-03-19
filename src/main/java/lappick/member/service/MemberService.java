package lappick.member.service;

import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lappick.auth.mapper.AuthMapper;
import lappick.cart.mapper.CartMapper;
import lappick.member.dto.MemberResponse;
import lappick.member.dto.MemberUpdateRequest;
import lappick.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public MemberResponse getMemberInfo(String memberId) {
        return memberMapper.selectMemberById(memberId);
    }

    public void updateMyInfo(MemberUpdateRequest request, String memberId) {
        String encodedPassword = authMapper.selectPwById(memberId);
        if (encodedPassword == null || !passwordEncoder.matches(request.getMemberPw(), encodedPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        MemberResponse currentMember = memberMapper.selectMemberById(memberId);
        if (currentMember == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        if (!Objects.equals(currentMember.getMemberEmail(), request.getMemberEmail())
                && authMapper.emailCheckSelectOne(request.getMemberEmail()) != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        MemberResponse dto = new MemberResponse();
        dto.setMemberId(memberId);
        dto.setMemberName(request.getMemberName());
        dto.setMemberAddr(request.getMemberAddr());
        dto.setMemberAddrDetail(request.getMemberAddrDetail());
        dto.setMemberPost(request.getMemberPost());
        dto.setMemberPhone1(request.getMemberPhone1());
        dto.setMemberPhone2(request.getMemberPhone2());
        dto.setMemberEmail(request.getMemberEmail());
        dto.setMemberBirth(request.getMemberBirth());
        dto.setGender(request.getGender());

        memberMapper.memberUpdate(dto);
    }

    public void withdrawMember(String memberId, String rawPassword) {
        String encodedPassword = authMapper.selectPwById(memberId);
        if (encodedPassword == null || !passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String memberNum = memberMapper.memberNumSelect(memberId);
        if (memberNum == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        cartMapper.cartAllDelete(memberNum);
        authMapper.deletePersistentLoginsByUsername(memberId);

        int updatedCount = memberMapper.softWithdrawMember(memberId, buildWithdrawnMemberId(memberNum));
        if (updatedCount == 0) {
            throw new IllegalStateException("탈퇴 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private String buildWithdrawnMemberId(String memberNum) {
        return "withdrawn_" + memberNum;
    }
}
