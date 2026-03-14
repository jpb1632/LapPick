package lappick.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lappick.cart.mapper.CartMapper;
import lappick.goods.dto.GoodsStockResponse;
import lappick.goods.mapper.GoodsMapper;
import lappick.goods.service.GoodsService;
import lappick.purchase.dto.PurchaseItemResponse;
import lappick.purchase.dto.PurchaseRequest;
import lappick.purchase.dto.PurchaseResponse;
import lappick.purchase.mapper.PurchaseMapper;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseMapper purchaseMapper;

    @Mock
    private CartMapper cartMapper;

    @Mock
    private GoodsService goodsService;

    @Mock
    private GoodsMapper goodsMapper;

    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseService(purchaseMapper, cartMapper, goodsService, goodsMapper);
    }

    @Test
    void placeOrder_recalculatesTotalAndAggregatesDuplicateGoods() {
        PurchaseRequest request = buildCardOrderRequest();
        request.setGoodsNums(new String[]{"GOODS-1", "GOODS-1"});
        request.setGoodsQtys(new String[]{"1", "2"});

        GoodsStockResponse goodsStock = new GoodsStockResponse();
        goodsStock.setGoodsNum("GOODS-1");
        goodsStock.setGoodsName("테스트 노트북");
        goodsStock.setGoodsPrice(1_500_000);
        goodsStock.setStockQty(5);

        when(goodsMapper.selectGoodsForUpdate("GOODS-1")).thenReturn("GOODS-1");
        when(goodsService.getGoodsDetailWithStock("GOODS-1")).thenReturn(goodsStock);

        String purchaseNum = purchaseService.placeOrder(request, "mem_100041");

        ArgumentCaptor<PurchaseResponse> purchaseCaptor = ArgumentCaptor.forClass(PurchaseResponse.class);
        ArgumentCaptor<PurchaseItemResponse> itemCaptor = ArgumentCaptor.forClass(PurchaseItemResponse.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cartDeleteCaptor = ArgumentCaptor.forClass(Map.class);

        verify(purchaseMapper).insertPurchase(purchaseCaptor.capture());
        verify(purchaseMapper, times(1)).insertPurchaseItem(itemCaptor.capture());
        verify(goodsService).changeStock(eq("GOODS-1"), eq(-3), contains(purchaseNum));
        verify(cartMapper).goodsNumsDelete(cartDeleteCaptor.capture());

        PurchaseResponse savedPurchase = purchaseCaptor.getValue();
        PurchaseItemResponse savedItem = itemCaptor.getValue();
        String[] deletedGoodsNums = (String[]) cartDeleteCaptor.getValue().get("goodsNums");

        assertThat(savedPurchase.getPurchaseTotal()).isEqualTo(4_500_000);
        assertThat(savedPurchase.getPaymentMethod()).isEqualTo("신용카드");
        assertThat(savedItem.getGoodsNum()).isEqualTo("GOODS-1");
        assertThat(savedItem.getPurchaseQty()).isEqualTo(3);
        assertThat(savedItem.getPurchasePrice()).isEqualTo(1_500_000);
        assertThat(deletedGoodsNums).containsExactly("GOODS-1");
        assertThat(purchaseNum).startsWith(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
    }

    @Test
    void updateOrderStatus_rejectsUnknownStatus() {
        assertThatThrownBy(() -> purchaseService.updateOrderStatus("PUR-1", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("허용되지 않은 주문 상태입니다.");

        verify(purchaseMapper, never()).updatePurchaseStatus(anyString(), anyString());
    }

    @Test
    void getOrderDetailForMember_throwsWhenOrderIsNotOwnedByMember() {
        when(purchaseMapper.selectPurchaseDetailByMember("PUR-1", "mem_100041")).thenReturn(null);

        assertThatThrownBy(() -> purchaseService.getOrderDetailForMember("PUR-1", "mem_100041"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("주문 조회 권한이 없습니다.");
    }

    private PurchaseRequest buildCardOrderRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setReceiverName("홍길동");
        request.setReceiverPhone("01012345678");
        request.setPurchasePost("12345");
        request.setPurchaseAddr("서울시 강남구");
        request.setPurchaseAddrDetail("101호");
        request.setPurchaseMsg("문 앞에 놔주세요");
        request.setPaymentMethod("신용카드");
        request.setCardCompany("신한카드");
        request.setCardNumber("1234123412341234");
        return request;
    }
}
