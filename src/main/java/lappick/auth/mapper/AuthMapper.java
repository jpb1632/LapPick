package lappick.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import lappick.auth.dto.AuthDetails;
import lappick.member.dto.MemberResponse;

@Mapper
@Repository
public interface AuthMapper {

    Integer userInsert(MemberResponse dto);

    Integer idCheckSelectOne(@Param("userId") String userId);
    Integer emailCheckSelectOne(@Param("userEmail") String userEmail);
    AuthDetails loginSelectOne(String userId);

    String findIdByNameAndEmail(@Param("memberName") String memberName, @Param("memberEmail") String memberEmail);
    MemberResponse findByIdAndEmail(@Param("memberId") String memberId, @Param("memberEmail") String memberEmail);
    void memberPwUpdate(MemberResponse dto);
    String selectPwById(String memberId);
    int deletePersistentLoginsByUsername(String username);
}
