package lappick.purchase.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import lappick.cart.dto.CartItemResponse;
import lappick.purchase.dto.PurchaseDraft;
import lappick.purchase.dto.PurchaseDraftItem;

@Service
public class PurchaseDraftService {

    private static final String PURCHASE_DRAFTS_SESSION_KEY = "purchaseDrafts";
    private static final int MAX_DRAFT_COUNT = 10;

    public String storeDraft(List<CartItemResponse> items, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("주문 세션 정보가 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품 정보가 없습니다.");
        }

        List<PurchaseDraftItem> draftItems = new ArrayList<>();
        for (CartItemResponse item : items) {
            if (item == null || item.getGoods() == null || item.getCart() == null) {
                throw new IllegalArgumentException("주문할 상품 정보가 올바르지 않습니다.");
            }

            String goodsNum = item.getGoods().getGoodsNum();
            if (goodsNum == null || goodsNum.isBlank()) {
                throw new IllegalArgumentException("주문할 상품 정보가 올바르지 않습니다.");
            }

            int quantity = item.getCart().getCartQty();
            if (quantity < 1) {
                throw new IllegalArgumentException("주문 수량이 올바르지 않습니다.");
            }

            draftItems.add(new PurchaseDraftItem(goodsNum, quantity));
        }

        @SuppressWarnings("unchecked")
        Map<String, PurchaseDraft> drafts = (Map<String, PurchaseDraft>) session.getAttribute(PURCHASE_DRAFTS_SESSION_KEY);
        if (drafts == null) {
            drafts = new LinkedHashMap<>();
        }

        while (drafts.size() >= MAX_DRAFT_COUNT) {
            String oldestToken = drafts.keySet().iterator().next();
            drafts.remove(oldestToken);
        }

        String token = UUID.randomUUID().toString();
        drafts.put(token, new PurchaseDraft(List.copyOf(draftItems)));
        session.setAttribute(PURCHASE_DRAFTS_SESSION_KEY, drafts);
        return token;
    }

    public PurchaseDraft getDraft(String token, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("주문 세션 정보가 없습니다.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("주문 정보를 다시 확인해주세요.");
        }

        @SuppressWarnings("unchecked")
        Map<String, PurchaseDraft> drafts = (Map<String, PurchaseDraft>) session.getAttribute(PURCHASE_DRAFTS_SESSION_KEY);
        if (drafts == null) {
            throw new IllegalArgumentException("주문 정보를 다시 확인해주세요.");
        }

        PurchaseDraft draft = drafts.get(token);
        if (draft == null || draft.items() == null || draft.items().isEmpty()) {
            throw new IllegalArgumentException("주문 정보를 다시 확인해주세요.");
        }

        return draft;
    }

    public PurchaseDraft consumeDraft(String token, HttpSession session) {
        PurchaseDraft draft = getDraft(token, session);
        removeDraft(token, session);
        return draft;
    }

    public void removeDraft(String token, HttpSession session) {
        if (session == null || token == null || token.isBlank()) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, PurchaseDraft> drafts = (Map<String, PurchaseDraft>) session.getAttribute(PURCHASE_DRAFTS_SESSION_KEY);
        if (drafts == null) {
            return;
        }

        drafts.remove(token);
        if (drafts.isEmpty()) {
            session.removeAttribute(PURCHASE_DRAFTS_SESSION_KEY);
            return;
        }

        session.setAttribute(PURCHASE_DRAFTS_SESSION_KEY, drafts);
    }
}
