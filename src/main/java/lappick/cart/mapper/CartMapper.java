package lappick.cart.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import lappick.cart.domain.Cart;
import lappick.cart.dto.CartItemResponse;

@Mapper
public interface CartMapper {
	public void cartMerge(Cart dto);
	
    public List<CartItemResponse> cartSelectList(
            @Param("memberNum") String memberNum, 
            @Param("nums") String [] nums,
            @Param("searchWord") String searchWord);

    public int cartAllDelete(String memberNum);
    
	public int cartQtyDown(@Param("goodsNum") String goodsNum
            ,@Param("memberNum") String memberNum);
            
	public int goodsNumsDelete(Map<String, Object> condition);
	
	public int countCartItems(String memberNum);

    public int deleteCartByMemberNums(List<String> memberNums);
}
