package lappick.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lappick.auth.dto.RegisterRequest;
import lappick.auth.mapper.AuthMapper;
import lappick.member.dto.MemberResponse;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final AuthMapper authMapper;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public void joinMember(RegisterRequest registerRequest) {
        validateNewMember(registerRequest.getMemberId(), registerRequest.getMemberEmail());

        MemberResponse dto = new MemberResponse();
        dto.setGender(registerRequest.getGender());
        dto.setMemberAddr(registerRequest.getMemberAddr());
        dto.setMemberAddrDetail(registerRequest.getMemberAddrDetail());
        dto.setMemberBirth(registerRequest.getMemberBirth());
        dto.setMemberEmail(registerRequest.getMemberEmail());
        dto.setMemberId(registerRequest.getMemberId());
        dto.setMemberName(registerRequest.getMemberName());
        dto.setMemberPhone1(registerRequest.getMemberPhone1());
        dto.setMemberPhone2(registerRequest.getMemberPhone2());
        dto.setMemberPost(registerRequest.getMemberPost());
        dto.setMemberPw(passwordEncoder.encode(registerRequest.getMemberPw()));

        authMapper.userInsert(dto);
    }

    @Transactional(readOnly = true)
    public String findIdByNameAndEmail(String memberName, String memberEmail) {
        return authMapper.findIdByNameAndEmail(memberName, memberEmail);
    }

    public boolean resetPassword(String memberId, String memberEmail) {
        MemberResponse member = authMapper.findByIdAndEmail(memberId, memberEmail);
        if (member == null) {
            return false;
        }

        String tempPassword = UUID.randomUUID().toString().substring(0, 8);
        member.setMemberPw(passwordEncoder.encode(tempPassword));
        authMapper.memberPwUpdate(member);

        String subject = "[LapPick] 임시 비밀번호 안내";
        String text = "회원님의 임시 비밀번호는 " + tempPassword + " 입니다."
                + System.lineSeparator()
                + "로그인 후 반드시 비밀번호를 변경해 주세요.";
        emailService.sendSimpleMessage(member.getMemberEmail(), subject, text);
        return true;
    }

    public void changePassword(String memberId, String oldPw, String newPw) {
        String encodedPassword = authMapper.selectPwById(memberId);
        if (encodedPassword == null || !passwordEncoder.matches(oldPw, encodedPassword)) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        MemberResponse dto = new MemberResponse();
        dto.setMemberId(memberId);
        dto.setMemberPw(passwordEncoder.encode(newPw));

        authMapper.memberPwUpdate(dto);
    }

    private void validateNewMember(String memberId, String memberEmail) {
        if (authMapper.idCheckSelectOne(memberId) != null) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (authMapper.emailCheckSelectOne(memberEmail) != null) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }
}
