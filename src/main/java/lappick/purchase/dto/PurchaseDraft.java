package lappick.purchase.dto;

import java.io.Serializable;
import java.util.List;

public record PurchaseDraft(List<PurchaseDraftItem> items) implements Serializable {
    private static final long serialVersionUID = 1L;
}
