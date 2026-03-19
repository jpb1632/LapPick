package lappick.purchase.service;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lappick.cart.mapper.CartMapper;
import lappick.common.util.SensitiveDataMasker;
import lappick.goods.dto.GoodsStockResponse;
import lappick.goods.mapper.GoodsMapper;
import lappick.goods.service.GoodsService;
import lappick.purchase.dto.DeliveryRequest;
import lappick.purchase.dto.PurchaseItemResponse;
import lappick.purchase.dto.PurchasePageResponse;
import lappick.purchase.dto.PurchaseRequest;
import lappick.purchase.dto.PurchaseResponse;
import lappick.purchase.mapper.PurchaseMapper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);
    private static final String PAYMENT_METHOD_CARD = "신용카드";
    private static final String PAYMENT_METHOD_BANK_TRANSFER = "무통장입금";
    private static final String ORDER_STATUS_PAID = "결제완료";
    private static final String ORDER_STATUS_PREPARING = "상품준비중";
    private static final String ORDER_STATUS_SHIPPING = "배송중";
    private static final String ORDER_STATUS_DELIVERED = "배송완료";
    private static final String ORDER_STATUS_CANCEL_REQUESTED = "취소요청";
    private static final String ORDER_STATUS_CANCELED = "주문취소";
    private static final Set<String> ALLOWED_ORDER_STATUSES = Set.of(
            ORDER_STATUS_PAID,
            ORDER_STATUS_PREPARING,
            ORDER_STATUS_SHIPPING,
            ORDER_STATUS_DELIVERED,
            ORDER_STATUS_CANCEL_REQUESTED,
            ORDER_STATUS_CANCELED
    );

    private final PurchaseMapper purchaseMapper;
    private final CartMapper cartMapper;
    private final GoodsService goodsService;
    private final GoodsMapper goodsMapper;

    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            timeout = 10,
            rollbackFor = Exception.class
    )
    public String placeOrder(PurchaseRequest command, String memberNum) {
        String safeMemberNum = requireText(memberNum, "회원 정보가 올바르지 않습니다.");

        try {
            validateOrderCommand(command);
            List<ValidatedPurchaseItem> validatedItems = validateAndPrepareOrderItems(command);
            int recalculatedTotal = calculateTotal(validatedItems);

            log.info("주문 시작: 회원번호={}, 상품개수={}, 총액={}", safeMemberNum, validatedItems.size(), recalculatedTotal);

            String purchaseNum = new SimpleDateFormat("yyyyMMdd").format(new Date())
                    + "-" + UUID.randomUUID().toString().substring(0, 8);

            PurchaseResponse purchase = new PurchaseResponse();
            purchase.setPurchaseNum(purchaseNum);
            purchase.setMemberNum(safeMemberNum);
            purchase.setReceiverName(command.getReceiverName());
            purchase.setReceiverPhone(command.getReceiverPhone());

            String fullAddress = "(" + command.getPurchasePost() + ") "
                    + command.getPurchaseAddr() + " " + command.getPurchaseAddrDetail();
            purchase.setPurchaseAddr(fullAddress);
            purchase.setPurchaseMsg(command.getPurchaseMsg());
            purchase.setPurchaseTotal(recalculatedTotal);
            purchase.setPaymentMethod(command.getPaymentMethod());

            if (PAYMENT_METHOD_CARD.equals(command.getPaymentMethod())) {
                purchase.setCardCompany(command.getCardCompany());
                purchase.setCardNumber(command.getCardNumber());
            } else if (PAYMENT_METHOD_BANK_TRANSFER.equals(command.getPaymentMethod())) {
                purchase.setBankName(command.getBankName());
                purchase.setDepositorName(command.getDepositorName());
            }

            purchaseMapper.insertPurchase(purchase);
            log.info("주문 헤더 생성 완료: {}", purchaseNum);

            for (ValidatedPurchaseItem validatedItem : validatedItems) {
                PurchaseItemResponse item = new PurchaseItemResponse();
                item.setPurchaseNum(purchaseNum);
                item.setGoodsNum(validatedItem.goodsNum());
                item.setPurchaseQty(validatedItem.quantity());
                item.setPurchasePrice(validatedItem.unitPrice());

                purchaseMapper.insertPurchaseItem(item);

                String memo = "주문 출고 (#" + purchaseNum + ")";
                goodsService.changeStock(item.getGoodsNum(), -item.getPurchaseQty(), memo);

                log.debug("재고 차감 완료: 상품번호={}, 수량={}", item.getGoodsNum(), item.getPurchaseQty());
            }

            Map<String, Object> condition = new HashMap<>();
            condition.put("memberNum", safeMemberNum);
            condition.put("goodsNums", validatedItems.stream()
                    .map(ValidatedPurchaseItem::goodsNum)
                    .toArray(String[]::new));
            cartMapper.goodsNumsDelete(condition);

            log.info("주문 완료: {}", purchaseNum);
            return purchaseNum;
        } catch (CannotAcquireLockException e) {
            log.warn("락 타임아웃: 회원번호={}", safeMemberNum, e);
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("주문 실패: 회원번호={}, 사유={}", safeMemberNum, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("주문 처리 중 오류 발생: 회원번호={}", safeMemberNum, e);
            throw new RuntimeException("주문 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
        }
    }

    @Transactional(readOnly = true)
    public PurchasePageResponse getMyOrderListPage(String memberNum, String searchWord, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("memberNum", memberNum);
        params.put("searchWord", searchWord);
        params.put("startRow", (long) (page - 1) * size + 1);
        params.put("endRow", (long) page * size);

        List<PurchaseResponse> rawList = purchaseMapper.selectMyPurchases(params);
        int total = purchaseMapper.countMyPurchases(params);

        Map<String, List<PurchaseItemResponse>> itemsGroupedByPurchaseNum = rawList.stream()
                .filter(p -> p.getPurchaseItems() != null && !p.getPurchaseItems().isEmpty())
                .flatMap(p -> p.getPurchaseItems().stream())
                .collect(Collectors.groupingBy(PurchaseItemResponse::getPurchaseNum));

        List<PurchaseResponse> finalList = rawList.stream()
                .collect(Collectors.toMap(PurchaseResponse::getPurchaseNum, p -> p, (p1, p2) -> p1, LinkedHashMap::new))
                .values().stream()
                .peek(order -> {
                    order.setPurchaseItems(itemsGroupedByPurchaseNum.get(order.getPurchaseNum()));
                    if (order.getDelivery() == null) {
                        rawList.stream()
                                .filter(raw -> raw.getPurchaseNum().equals(order.getPurchaseNum()) && raw.getDelivery() != null)
                                .findFirst()
                                .ifPresent(raw -> order.setDelivery(raw.getDelivery()));
                    }
                })
                .collect(Collectors.toList());

        return buildPageResponse(finalList, page, size, total);
    }

    @Transactional(readOnly = true)
    public PurchasePageResponse getAllOrders(Map<String, Object> params, int page, int size) {
        params.put("startRow", (long) (page - 1) * size + 1);
        params.put("endRow", (long) page * size);
        List<PurchaseResponse> list = purchaseMapper.selectAllPurchases(params);
        int total = purchaseMapper.countAllPurchases(params);
        return buildPageResponse(list, page, size, total);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            timeout = 10,
            rollbackFor = Exception.class
    )
    public void processShipping(DeliveryRequest dto) {
        if (dto == null) {
            throw new IllegalArgumentException("배송 요청 정보가 없습니다.");
        }

        String purchaseNum = requireText(dto.getPurchaseNum(), "주문번호가 없습니다.");
        dto.setPurchaseNum(purchaseNum);
        dto.setDeliveryCompany(requireText(dto.getDeliveryCompany(), "택배사를 입력해주세요."));
        dto.setDeliveryNum(requireText(dto.getDeliveryNum(), "송장번호를 입력해주세요."));

        PurchaseResponse purchase = purchaseMapper.selectPurchaseDetail(purchaseNum);
        if (purchase == null) {
            throw new IllegalArgumentException("주문 정보를 찾을 수 없습니다.");
        }
        if (purchase.getDelivery() != null) {
            throw new IllegalStateException("이미 배송 정보가 등록된 주문입니다.");
        }

        purchaseMapper.insertDelivery(dto);
        updateOrderStatus(purchaseNum, ORDER_STATUS_SHIPPING);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            timeout = 10,
            rollbackFor = Exception.class
    )
    public void updateOrderStatus(String purchaseNum, String status) {
        String safePurchaseNum = requireText(purchaseNum, "주문번호가 없습니다.");
        String trimmedStatus = requireText(status, "주문 상태가 없습니다.");

        log.info("주문 상태 변경 시작. 주문번호: {}, 변경할 상태: '{}'", safePurchaseNum, trimmedStatus);

        if (!ALLOWED_ORDER_STATUSES.contains(trimmedStatus)) {
            throw new IllegalArgumentException("허용되지 않은 주문 상태입니다.");
        }

        PurchaseResponse purchase = purchaseMapper.selectPurchaseDetail(safePurchaseNum);
        if (purchase == null) {
            throw new IllegalArgumentException("주문 정보를 찾을 수 없습니다.");
        }

        if (ORDER_STATUS_CANCELED.equals(trimmedStatus) && !ORDER_STATUS_CANCELED.equals(purchase.getPurchaseStatus())) {
            List<PurchaseItemResponse> items = purchase.getPurchaseItems();
            if (items != null && !items.isEmpty()) {
                log.info("취소 주문 재고 복원 시작: 주문번호={}, 상품건수={}", safePurchaseNum, items.size());
                for (PurchaseItemResponse item : items) {
                    String memo = "주문 취소 재고 복원 (#" + safePurchaseNum + ")";
                    goodsService.changeStock(item.getGoodsNum(), item.getPurchaseQty(), memo);
                }
            }
        }

        purchaseMapper.updatePurchaseStatus(safePurchaseNum, trimmedStatus);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getOrderDetail(String purchaseNum) {
        PurchaseResponse purchase = purchaseMapper.selectPurchaseDetail(purchaseNum);
        maskSensitivePaymentInfo(purchase);
        return purchase;
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getOrderDetailForMember(String purchaseNum, String memberNum) {
        PurchaseResponse purchase = purchaseMapper.selectPurchaseDetailByMember(
                requireText(purchaseNum, "주문번호가 없습니다."),
                requireText(memberNum, "회원 정보가 올바르지 않습니다.")
        );
        if (purchase == null) {
            throw new SecurityException("주문 조회 권한이 없습니다.");
        }
        return purchase;
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            timeout = 10,
            rollbackFor = Exception.class
    )
    public void requestCancelOrder(String purchaseNum, String memberNum) {
        PurchaseResponse purchase = getOrderDetailForMember(purchaseNum, memberNum);
        if (ORDER_STATUS_PAID.equals(purchase.getPurchaseStatus()) || ORDER_STATUS_PREPARING.equals(purchase.getPurchaseStatus())) {
            purchaseMapper.updatePurchaseStatus(purchase.getPurchaseNum(), ORDER_STATUS_CANCEL_REQUESTED);
            return;
        }
        throw new IllegalStateException("주문 취소가 불가능한 상태입니다.");
    }

    @Transactional(readOnly = true)
    public List<PurchaseItemResponse> getPurchasedItems(String memberNum) {
        return purchaseMapper.selectPurchasedItemsByMemberNum(memberNum);
    }

    @Transactional(readOnly = true)
    public List<PurchaseItemResponse> getPurchasedItemsOfProduct(String memberNum, String goodsNum) {
        List<PurchaseItemResponse> allPurchases = purchaseMapper.selectPurchasedItemsByMemberNum(memberNum);
        return allPurchases.stream()
                .filter(item -> item.getGoodsNum().equals(goodsNum))
                .collect(Collectors.toList());
    }

    private PurchasePageResponse buildPageResponse(List<PurchaseResponse> items, int page, int size, int total) {
        int totalPages = (total > 0) ? (int) Math.ceil((double) total / size) : 0;
        int paginationRange = 5;
        int startPage = (int) (Math.floor((page - 1.0) / paginationRange) * paginationRange + 1);
        int endPage = Math.min(startPage + paginationRange - 1, totalPages);

        return PurchasePageResponse.builder()
                .items(items).page(page).size(size).total(total)
                .totalPages(totalPages).startPage(startPage).endPage(endPage)
                .hasPrev(startPage > 1).hasNext(endPage < totalPages)
                .build();
    }

    private void validateOrderCommand(PurchaseRequest command) {
        if (command == null) {
            throw new IllegalArgumentException("주문 정보가 없습니다.");
        }

        command.setReceiverName(requireText(command.getReceiverName(), "받으시는 분을 입력해주세요."));
        command.setReceiverPhone(requireText(command.getReceiverPhone(), "연락처를 입력해주세요."));
        command.setPurchasePost(requireText(command.getPurchasePost(), "우편번호를 입력해주세요."));
        command.setPurchaseAddr(requireText(command.getPurchaseAddr(), "주소를 입력해주세요."));
        command.setPurchaseAddrDetail(requireText(command.getPurchaseAddrDetail(), "상세주소를 입력해주세요."));
        command.setPaymentMethod(requireText(command.getPaymentMethod(), "결제 수단을 선택해주세요."));

        if (PAYMENT_METHOD_CARD.equals(command.getPaymentMethod())) {
            command.setCardCompany(requireText(command.getCardCompany(), "카드사를 선택해주세요."));
            command.setCardNumber(SensitiveDataMasker.maskCardNumberForStorage(
                    requireText(command.getCardNumber(), "카드번호를 입력해주세요.")
            ));
            command.setBankName(null);
            command.setDepositorName(null);
            return;
        }

        if (PAYMENT_METHOD_BANK_TRANSFER.equals(command.getPaymentMethod())) {
            command.setBankName(requireText(command.getBankName(), "입금 계좌를 선택해주세요."));
            command.setDepositorName(requireText(command.getDepositorName(), "입금자명을 입력해주세요."));
            command.setCardCompany(null);
            command.setCardNumber(null);
            return;
        }

        throw new IllegalArgumentException("결제 수단이 올바르지 않습니다.");
    }

    private List<ValidatedPurchaseItem> validateAndPrepareOrderItems(PurchaseRequest command) {
        if (command.getGoodsNums() == null || command.getGoodsQtys() == null) {
            throw new IllegalArgumentException("주문 상품 정보가 올바르지 않습니다.");
        }
        if (command.getGoodsNums().length == 0) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }
        if (command.getGoodsNums().length != command.getGoodsQtys().length) {
            throw new IllegalArgumentException("주문 상품 정보가 올바르지 않습니다.");
        }

        Map<String, Integer> requestedQuantities = new LinkedHashMap<>();
        for (int i = 0; i < command.getGoodsNums().length; i++) {
            String goodsNum = requireText(command.getGoodsNums()[i], "주문 상품 정보가 올바르지 않습니다.");
            int quantity = parsePositiveInt(command.getGoodsQtys()[i], "주문 수량이 올바르지 않습니다.");
            requestedQuantities.merge(goodsNum, quantity, this::addQuantitySafely);
        }

        List<ValidatedPurchaseItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            String goodsNum = entry.getKey();
            int quantity = entry.getValue();

            lockGoods(goodsNum);

            GoodsStockResponse goodsStock = goodsService.getGoodsDetailWithStock(goodsNum);
            if (goodsStock == null) {
                throw new IllegalStateException("상품 정보를 찾을 수 없습니다. (상품번호: " + goodsNum + ")");
            }
            if (goodsStock.getGoodsPrice() == null || goodsStock.getGoodsPrice() < 0) {
                throw new IllegalStateException("상품 가격 정보가 올바르지 않습니다. (상품번호: " + goodsNum + ")");
            }
            if (goodsStock.getStockQty() == null) {
                throw new IllegalStateException("재고 정보를 확인할 수 없습니다. (상품번호: " + goodsNum + ")");
            }
            if (goodsStock.getStockQty() < quantity) {
                throw new IllegalStateException(
                        String.format(
                                "재고 부족: '%s' 상품의 재고가 부족합니다. (요청: %d개, 현재: %d개)",
                                goodsStock.getGoodsName(),
                                quantity,
                                goodsStock.getStockQty()
                        )
                );
            }

            items.add(new ValidatedPurchaseItem(
                    goodsNum,
                    goodsStock.getGoodsName(),
                    quantity,
                    goodsStock.getGoodsPrice()
            ));
        }

        return items;
    }

    private int calculateTotal(List<ValidatedPurchaseItem> validatedItems) {
        int total = 0;
        try {
            for (ValidatedPurchaseItem item : validatedItems) {
                total = Math.addExact(total, item.lineTotal());
            }
            return total;
        } catch (ArithmeticException e) {
            throw new IllegalStateException("주문 금액 계산 중 오류가 발생했습니다.", e);
        }
    }

    private int addQuantitySafely(int currentQuantity, int addedQuantity) {
        try {
            return Math.addExact(currentQuantity, addedQuantity);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("주문 수량이 올바르지 않습니다.", e);
        }
    }

    private void lockGoods(String goodsNum) {
        try {
            goodsMapper.selectGoodsForUpdate(goodsNum);
            log.debug("락 획득 성공: 상품번호={}", goodsNum);
        } catch (Exception e) {
            log.error("락 획득 실패: 상품번호={}", goodsNum, e);
            throw new CannotAcquireLockException(
                    "현재 많은 주문이 몰려 재고 확인이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
                    e
            );
        }
    }

    private String requireText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException(message);
        }

        return trimmedValue;
    }

    private int parsePositiveInt(String value, String message) {
        try {
            int parsedValue = Integer.parseInt(requireText(value, message));
            if (parsedValue < 1) {
                throw new IllegalArgumentException(message);
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    private void maskSensitivePaymentInfo(PurchaseResponse purchase) {
        if (purchase == null) {
            return;
        }

        if (PAYMENT_METHOD_CARD.equals(purchase.getPaymentMethod())) {
            purchase.setCardNumber(SensitiveDataMasker.maskCardNumberForDisplay(purchase.getCardNumber()));
            return;
        }

        purchase.setCardNumber(null);
    }

    private record ValidatedPurchaseItem(String goodsNum, String goodsName, int quantity, int unitPrice) {
        private int lineTotal() {
            try {
                return Math.multiplyExact(quantity, unitPrice);
            } catch (ArithmeticException e) {
                throw new IllegalStateException("주문 금액 계산 중 오류가 발생했습니다.", e);
            }
        }
    }
}
