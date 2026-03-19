package lappick.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import lappick.common.dto.StartEndPageDTO;
import lappick.member.dto.MemberResponse;

@Mapper
@Repository
public interface MemberMapper {

    // Admin member management
    List<MemberResponse> memberSelectList(StartEndPageDTO sepDTO);
    Integer memberCountBySearch(@Param("searchWord") String searchWord);
    MemberResponse memberSelectOneByNum(String memberNum);
    void adminMemberUpdate(MemberResponse dto);
    int memberDelete(List<String> memberNums);

    // Member my-page
    MemberResponse selectMemberById(String memberId);
    void memberUpdate(MemberResponse dto);
    int softWithdrawMember(@Param("memberId") String memberId, @Param("withdrawnMemberId") String withdrawnMemberId);

    // Common use
    String memberNumSelect(String memberId);
    MemberResponse selectOneById(String memberId);
}
