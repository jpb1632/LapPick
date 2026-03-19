package lappick.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

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

        when(goodsMapper.selectGoodsForUpdate("GOODS-1")).thenReturn("GOODS-1");
        when(goodsService.getGoodsDetailWithStock("GOODS-1"))
                .thenReturn(goodsStock("GOODS-1", "Test Notebook", 1_500_000, 5));

        String purchaseNum = purchaseService.placeOrder(request, "mem_100041");

        ArgumentCaptor<PurchaseResponse> purchaseCaptor = ArgumentCaptor.forClass(PurchaseResponse.class);
        ArgumentCaptor<PurchaseItemResponse> itemCaptor = ArgumentCaptor.forClass(PurchaseItemResponse.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cartDeleteCaptor = ArgumentCaptor.forClass(Map.class);

        verify(purchaseMapper).insertPurchase(purchaseCaptor.capture());
        verify(purchaseMapper, times(1)).insertPurchaseItem(itemCaptor.capture());
        verify(goodsMapper).selectGoodsForUpdate("GOODS-1");
        verify(goodsService).changeStock(eq("GOODS-1"), eq(-3), contains(purchaseNum));
        verify(cartMapper).goodsNumsDelete(cartDeleteCaptor.capture());

        PurchaseResponse savedPurchase = purchaseCaptor.getValue();
        PurchaseItemResponse savedItem = itemCaptor.getValue();
        String[] deletedGoodsNums = (String[]) cartDeleteCaptor.getValue().get("goodsNums");

        assertThat(savedPurchase.getPurchaseTotal()).isEqualTo(4_500_000);
        assertThat(savedPurchase.getCardNumber()).isEqualTo("************1234");
        assertThat(savedItem.getGoodsNum()).isEqualTo("GOODS-1");
        assertThat(savedItem.getPurchaseQty()).isEqualTo(3);
        assertThat(savedItem.getPurchasePrice()).isEqualTo(1_500_000);
        assertThat(deletedGoodsNums).containsExactly("GOODS-1");
        assertThat(purchaseNum).startsWith(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
    }

    @Test
    void placeOrder_throwsWhenStockIsInsufficient() {
        PurchaseRequest request = buildCardOrderRequest();
        request.setGoodsNums(new String[]{"GOODS-1"});
        request.setGoodsQtys(new String[]{"3"});

        when(goodsMapper.selectGoodsForUpdate("GOODS-1")).thenReturn("GOODS-1");
        when(goodsService.getGoodsDetailWithStock("GOODS-1"))
                .thenReturn(goodsStock("GOODS-1", "Test Notebook", 1_500_000, 1));

        assertThatThrownBy(() -> purchaseService.placeOrder(request, "mem_100041"))
                .isInstanceOf(IllegalStateException.class);

        verify(purchaseMapper, never()).insertPurchase(any());
        verify(goodsService, never()).changeStock(anyString(), eq(-3), anyString());
    }

    @Test
    void placeOrder_acquiresLockForEachUniqueGoods() {
        PurchaseRequest request = buildCardOrderRequest();
        request.setGoodsNums(new String[]{"GOODS-1", "GOODS-2"});
        request.setGoodsQtys(new String[]{"1", "2"});

        when(goodsMapper.selectGoodsForUpdate("GOODS-1")).thenReturn("GOODS-1");
        when(goodsMapper.selectGoodsForUpdate("GOODS-2")).thenReturn("GOODS-2");
        when(goodsService.getGoodsDetailWithStock("GOODS-1"))
                .thenReturn(goodsStock("GOODS-1", "Notebook A", 1_000_000, 5));
        when(goodsService.getGoodsDetailWithStock("GOODS-2"))
                .thenReturn(goodsStock("GOODS-2", "Notebook B", 2_000_000, 5));

        purchaseService.placeOrder(request, "mem_100041");

        verify(goodsMapper).selectGoodsForUpdate("GOODS-1");
        verify(goodsMapper).selectGoodsForUpdate("GOODS-2");
        verify(goodsService).changeStock(eq("GOODS-1"), eq(-1), contains("#"));
        verify(goodsService).changeStock(eq("GOODS-2"), eq(-2), contains("#"));
        verify(purchaseMapper, times(2)).insertPurchaseItem(any(PurchaseItemResponse.class));
    }

    @Test
    void placeOrder_throwsWhenStockLockCannotBeAcquired() {
        PurchaseRequest request = buildCardOrderRequest();
        request.setGoodsNums(new String[]{"GOODS-1"});
        request.setGoodsQtys(new String[]{"1"});

        when(goodsMapper.selectGoodsForUpdate("GOODS-1"))
                .thenThrow(new RuntimeException("lock timeout"));

        assertThatThrownBy(() -> purchaseService.placeOrder(request, "mem_100041"))
                .isInstanceOf(CannotAcquireLockException.class);

        verify(goodsService, never()).getGoodsDetailWithStock(anyString());
        verify(purchaseMapper, never()).insertPurchase(any());
    }

    @Test
    void updateOrderStatus_rejectsUnknownStatus() {
        assertThatThrownBy(() -> purchaseService.updateOrderStatus("PUR-1", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(purchaseMapper, never()).updatePurchaseStatus(anyString(), anyString());
    }

    @Test
    void updateOrderStatus_restoresStockWhenOrderIsCancelled() {
        PurchaseItemResponse item = new PurchaseItemResponse();
        item.setGoodsNum("GOODS-1");
        item.setPurchaseQty(2);

        PurchaseResponse purchase = new PurchaseResponse();
        purchase.setPurchaseNum("PUR-1");
        purchase.setPurchaseStatus("결제완료");
        purchase.setPurchaseItems(List.of(item));

        when(purchaseMapper.selectPurchaseDetail("PUR-1")).thenReturn(purchase);

        purchaseService.updateOrderStatus("PUR-1", "주문취소");

        verify(goodsService).changeStock("GOODS-1", 2, "주문 취소 재고 복원 (#PUR-1)");
        verify(purchaseMapper).updatePurchaseStatus("PUR-1", "주문취소");
    }

    @Test
    void getOrderDetailForMember_throwsWhenOrderIsNotOwnedByMember() {
        when(purchaseMapper.selectPurchaseDetailByMember("PUR-1", "mem_100041")).thenReturn(null);

        assertThatThrownBy(() -> purchaseService.getOrderDetailForMember("PUR-1", "mem_100041"))
                .isInstanceOf(SecurityException.class);
    }

    private PurchaseRequest buildCardOrderRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setReceiverName("Tester");
        request.setReceiverPhone("01012345678");
        request.setPurchasePost("12345");
        request.setPurchaseAddr("Seoul");
        request.setPurchaseAddrDetail("101");
        request.setPurchaseMsg("Leave at the door");
        request.setPaymentMethod("신용카드");
        request.setCardCompany("신한카드");
        request.setCardNumber("1234123412341234");
        return request;
    }

    private GoodsStockResponse goodsStock(String goodsNum, String goodsName, int unitPrice, int stockQty) {
        GoodsStockResponse goodsStock = new GoodsStockResponse();
        goodsStock.setGoodsNum(goodsNum);
        goodsStock.setGoodsName(goodsName);
        goodsStock.setGoodsPrice(unitPrice);
        goodsStock.setStockQty(stockQty);
        return goodsStock;
    }
}
