package lappick.purchase.dto;

import lombok.Data;

@Data
public class PurchaseRequest {
    private String purchaseToken;
    private String receiverName;
    private String receiverPhone;
    private String purchasePost;
    private String purchaseAddr;
    private String purchaseAddrDetail;
    private String purchaseMsg;
    private String[] goodsNums;
    private String[] goodsQtys;
    private String paymentMethod;
    private String cardCompany;
    private String cardNumber;
    private String bankName;
    private String depositorName;
}
