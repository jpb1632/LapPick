package lappick.purchase.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import lappick.member.dto.MemberResponse;
import lappick.purchase.dto.DeliveryRequest;
import lappick.purchase.dto.PurchaseItemResponse;
import lappick.purchase.dto.PurchaseResponse;

@Mapper
public interface PurchaseMapper {
    void insertPurchase(PurchaseResponse dto);
    void insertPurchaseItem(PurchaseItemResponse dto);
    List<PurchaseResponse> selectMyPurchases(Map<String, Object> params);
    int countMyPurchases(Map<String, Object> params);
    List<PurchaseResponse> selectAllPurchases(Map<String, Object> params);
    int countAllPurchases(Map<String, Object> params);
    void updatePurchaseStatus(@Param("purchaseNum") String purchaseNum, @Param("status") String status);
    void insertDelivery(DeliveryRequest dto);
    PurchaseResponse selectPurchaseDetail(String purchaseNum);
    PurchaseResponse selectPurchaseDetailByMember(
            @Param("purchaseNum") String purchaseNum,
            @Param("memberNum") String memberNum
    );
    List<PurchaseItemResponse> selectPurchasedItemsByMemberNum(String memberNum);
    int countPurchasesByMemberNum(String memberNum);
    List<MemberResponse> selectMembersWithPurchases(List<String> memberNums);
    int countDeliveredPurchaseItemByMember(
            @Param("purchaseNum") String purchaseNum,
            @Param("goodsNum") String goodsNum,
            @Param("memberNum") String memberNum
    );
}
