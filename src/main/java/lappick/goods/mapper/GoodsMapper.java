package lappick.goods.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import lappick.goods.dto.GoodsFilterRequest;
import lappick.goods.dto.StockHistoryResponse;
import lappick.goods.dto.GoodsResponse;
import lappick.goods.dto.GoodsSalesResponse;
import lappick.goods.dto.GoodsStockResponse;

import java.util.List;
import java.util.Map;

@Repository
@Mapper
public interface GoodsMapper {
    
    // 상품(Goods) 관련 메서드
    List<GoodsResponse> allSelect(GoodsFilterRequest filter);
    int goodsCount(GoodsFilterRequest filter);
    GoodsResponse selectOne(String goodsNum);
    void goodsInsert(GoodsResponse dto);
    void goodsUpdate(GoodsResponse dto);
    void goodsDelete(List<String> nums);
    GoodsStockResponse selectOneWithStock(String goodsNum);
    List<GoodsResponse> selectGoodsByNumList(List<String> nums);
    List<GoodsResponse> selectDeleteBlockedGoods(List<String> nums);
    
    // 비관적 락을 위한 메서드 선언
    String selectGoodsForUpdate(String goodsNum);
    
    // 재고(GoodsIpgo) 관련 메서드
    void insertGoodsIpgo(@Param("goodsNum") String goodsNum, @Param("ipgoQty") int ipgoQty, @Param("memo") String memo);
    int countIpgoHistory(String goodsNum);
    List<StockHistoryResponse> selectIpgoHistoryPaged(java.util.Map<String, Object> params);
    
    void deleteGoodsIpgo(List<String> nums);

    // 기타 조회 메서드
    List<GoodsResponse> selectAllForFilter();
    List<GoodsResponse> selectBestGoodsList();
    int countGoodsSalesStatus(Map<String, Object> params);
    List<GoodsSalesResponse> findGoodsSalesStatusPaginated(Map<String, Object> params);
}
