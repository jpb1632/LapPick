package lappick.purchase.dto;

import java.io.Serializable;

public record PurchaseDraftItem(String goodsNum, int quantity) implements Serializable {
    private static final long serialVersionUID = 1L;
}
