package lappick.review.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import lappick.review.domain.Review;
import lappick.review.dto.ReviewSummaryResponse;

@Repository
@Mapper
public interface ReviewMapper {

    void insertReview(Review dto);

    List<Review> selectReviewsByGoodsNum(String goodsNum);

    ReviewSummaryResponse getReviewSummary(String goodsNum);

    int countReviewsByMemberNum(String memberNum);

    // memberNum, pagination 정보를 Map으로 받아 처리
    List<Review> selectReviewsByMemberNum(Map<String, Object> params);

    Review selectReview(Long reviewNum);

    void deleteReview(Long reviewNum);
    int deleteReviewsByMemberNums(List<String> memberNums);

    void updateReview(Review dto);

    int countReviewsByPurchaseGoodsMember(
            @Param("purchaseNum") String purchaseNum,
            @Param("goodsNum") String goodsNum,
            @Param("memberNum") String memberNum
    );
    
    // ===== 관리자 기능 =====
    int countReviewsForAdmin(Map<String, Object> params);
    List<Review> findReviewsForAdminPaginated(Map<String, Object> params);
    int updateReviewStatus(Map<String, Object> params);

    // ===== 상품 상세 페이지 (페이지네이션) =====
    int countReviewsByGoodsNum(String goodsNum);
    List<Review> selectReviewsByGoodsNumPaginated(Map<String, Object> params);
}
