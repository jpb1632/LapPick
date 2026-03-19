package lappick.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import lappick.cart.domain.Cart;
import lappick.cart.dto.CartItemResponse;
import lappick.goods.dto.GoodsResponse;
import lappick.purchase.dto.PurchaseDraft;
import lappick.purchase.dto.PurchaseDraftItem;

class PurchaseDraftServiceTest {

    private PurchaseDraftService purchaseDraftService;

    @BeforeEach
    void setUp() {
        purchaseDraftService = new PurchaseDraftService();
    }

    @Test
    void storeDraftAndGetDraft_useServerSideItems() {
        MockHttpSession session = new MockHttpSession();

        String token = purchaseDraftService.storeDraft(List.of(
                createCartItem("GOODS-1", 1),
                createCartItem("GOODS-2", 3)
        ), session);

        PurchaseDraft draft = purchaseDraftService.getDraft(token, session);

        assertThat(draft.items())
                .containsExactly(
                        new PurchaseDraftItem("GOODS-1", 1),
                        new PurchaseDraftItem("GOODS-2", 3)
                );
    }

    @Test
    void getDraft_throwsWhenTokenDoesNotExist() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> purchaseDraftService.getDraft("missing-token", session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeDraft_clearsStoredDraft() {
        MockHttpSession session = new MockHttpSession();
        String token = purchaseDraftService.storeDraft(List.of(createCartItem("GOODS-1", 1)), session);

        purchaseDraftService.removeDraft(token, session);

        assertThatThrownBy(() -> purchaseDraftService.getDraft(token, session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeDraft_returnsDraftAndRemovesIt() {
        MockHttpSession session = new MockHttpSession();
        String token = purchaseDraftService.storeDraft(List.of(createCartItem("GOODS-1", 2)), session);

        PurchaseDraft draft = purchaseDraftService.consumeDraft(token, session);

        assertThat(draft.items()).containsExactly(new PurchaseDraftItem("GOODS-1", 2));
        assertThatThrownBy(() -> purchaseDraftService.getDraft(token, session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeDraft_throwsWhenItemListIsEmpty() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> purchaseDraftService.storeDraft(List.of(), session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CartItemResponse createCartItem(String goodsNum, int quantity) {
        GoodsResponse goods = new GoodsResponse();
        goods.setGoodsNum(goodsNum);

        Cart cart = new Cart();
        cart.setCartQty(quantity);

        CartItemResponse response = new CartItemResponse();
        response.setGoods(goods);
        response.setCart(cart);
        return response;
    }
}
