package lappick.purchase.controller;

import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpSession;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lappick.cart.domain.Cart;
import lappick.cart.dto.CartItemResponse;
import lappick.cart.mapper.CartMapper;
import lappick.goods.dto.GoodsResponse;
import lappick.goods.mapper.GoodsMapper;
import lappick.member.dto.MemberResponse;
import lappick.member.mapper.MemberMapper;
import lappick.purchase.dto.DeliveryRequest;
import lappick.purchase.dto.PurchaseDraft;
import lappick.purchase.dto.PurchaseDraftItem;
import lappick.purchase.dto.PurchasePageResponse;
import lappick.purchase.dto.PurchaseRequest;
import lappick.purchase.dto.PurchaseResponse;
import lappick.purchase.service.PurchaseDraftService;
import lappick.purchase.service.PurchaseService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseDraftService purchaseDraftService;
    private final MemberMapper memberMapper;
    private final CartMapper cartMapper;
    private final GoodsMapper goodsMapper;

    @PostMapping("/purchases/order")
    public String purchaseForm(@RequestParam("nums") String[] goodsNums,
                               Authentication auth,
                               Model model,
                               RedirectAttributes ra,
                               HttpSession session) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        MemberResponse memberDTO = memberMapper.selectOneById(userDetails.getUsername());

        List<CartItemResponse> items = cartMapper.cartSelectList(memberDTO.getMemberNum(), goodsNums, null);
        if (items == null || items.isEmpty()) {
            ra.addFlashAttribute("error", "주문할 상품 정보를 다시 확인해주세요.");
            return "redirect:/cart/cartList";
        }

        prepareOrderForm(model, session, memberDTO, items);
        return "user/purchase/order-form";
    }

    @GetMapping("/purchases/order-direct")
    public String purchaseFormDirect(@RequestParam("goodsNum") String goodsNum,
                                     @RequestParam(value = "qty", defaultValue = "1") int qty,
                                     Authentication auth,
                                     Model model,
                                     RedirectAttributes ra,
                                     HttpSession session) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        MemberResponse memberDTO = memberMapper.selectOneById(userDetails.getUsername());
        GoodsResponse goodsDTO = goodsMapper.selectOne(goodsNum);
        if (goodsDTO == null) {
            ra.addFlashAttribute("error", "주문할 상품 정보를 다시 확인해주세요.");
            return "redirect:/goods/detail/" + goodsNum;
        }
        int safeQty = Math.max(qty, 1);

        Cart cart = new Cart();
        cart.setCartQty(safeQty);

        CartItemResponse item = new CartItemResponse();
        item.setGoods(goodsDTO);
        item.setCart(cart);

        prepareOrderForm(model, session, memberDTO, Collections.singletonList(item));
        return "user/purchase/order-form";
    }

    @PostMapping("/purchases")
    public String placeOrder(PurchaseRequest command,
                             Authentication auth,
                             RedirectAttributes ra,
                             HttpSession session) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        MemberResponse memberDTO = memberMapper.selectOneById(userDetails.getUsername());
        try {
            PurchaseDraft draft = purchaseDraftService.consumeDraft(command.getPurchaseToken(), session);
            applyDraftToCommand(command, draft);
            String purchaseNum = purchaseService.placeOrder(command, memberDTO.getMemberNum());
            return "redirect:/purchases/complete?purchaseNum=" + purchaseNum;
        } catch (CannotAcquireLockException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart/cartList";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart/cartList";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart/cartList";
        }
    }

    @GetMapping("/purchases/complete")
    public String purchaseComplete(@RequestParam("purchaseNum") String purchaseNum, Model model) {
        model.addAttribute("purchaseNum", purchaseNum);
        return "user/purchase/order-complete";
    }

    @GetMapping("/purchases/my-orders")
    public String myOrderList(Authentication auth,
                              Model model,
                              @RequestParam(value = "searchWord", required = false) String searchWord,
                              @RequestParam(value = "page", defaultValue = "1") int page) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        MemberResponse memberDTO = memberMapper.selectOneById(userDetails.getUsername());

        PurchasePageResponse pageData = purchaseService.getMyOrderListPage(memberDTO.getMemberNum(), searchWord, page, 5);

        int paginationRange = 5;
        int startPage = (int) (Math.floor((pageData.getPage() - 1.0) / paginationRange) * paginationRange + 1);
        int endPage = Math.min(startPage + paginationRange - 1, pageData.getTotalPages());
        boolean hasPrev = startPage > 1;
        boolean hasNext = endPage < pageData.getTotalPages();

        model.addAttribute("pageData", pageData);
        model.addAttribute("searchWord", searchWord);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);

        return "user/purchase/order-list";
    }

    @GetMapping("/purchases/{purchaseNum}")
    public String orderDetail(@PathVariable("purchaseNum") String purchaseNum,
                              Authentication auth,
                              Model model,
                              RedirectAttributes ra) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        MemberResponse memberDTO = memberMapper.selectOneById(userDetails.getUsername());
        PurchaseResponse order;
        try {
            order = purchaseService.getOrderDetailForMember(purchaseNum, memberDTO.getMemberNum());
        } catch (SecurityException e) {
            ra.addFlashAttribute("error", "조회 권한이 없는 주문입니다.");
            return "redirect:/purchases/my-orders";
        }
        model.addAttribute("order", order);
        return "user/purchase/order-detail";
    }

    @PostMapping("/purchases/{purchaseNum}/cancel")
    public String cancelOrderRequest(@PathVariable("purchaseNum") String purchaseNum, Authentication auth) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        MemberResponse memberDTO = memberMapper.selectOneById(userDetails.getUsername());
        purchaseService.requestCancelOrder(purchaseNum, memberDTO.getMemberNum());
        return "redirect:/purchases/" + purchaseNum;
    }

    @GetMapping("/admin/purchases")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
    public String orderList(@RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "searchWord", required = false) String searchWord,
                            Model model) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", status);
        params.put("searchWord", searchWord);
        PurchasePageResponse pageData = purchaseService.getAllOrders(params, page, 5);
        model.addAttribute("pageData", pageData);
        model.addAttribute("status", status);
        model.addAttribute("searchWord", searchWord);
        return "admin/purchase/order-list";
    }

    @GetMapping("/admin/purchases/{purchaseNum}")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
    public String empOrderDetail(@PathVariable("purchaseNum") String purchaseNum, Model model) {
        PurchaseResponse order = purchaseService.getOrderDetail(purchaseNum);
        model.addAttribute("order", order);
        return "admin/purchase/order-detail";
    }

    @PostMapping("/admin/purchases/process-shipping")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
    public String processShipping(DeliveryRequest dto, RedirectAttributes ra) {
        try {
            purchaseService.processShipping(dto);
            ra.addFlashAttribute("message", "송장 정보가 등록되었고 주문 상태가 '배송중'으로 변경되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchases";
    }

    @PostMapping("/admin/purchases/update-status")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
    public String updateStatus(@RequestParam("purchaseNum") String purchaseNum,
                               @RequestParam("status") String status,
                               RedirectAttributes ra) {
        try {
            purchaseService.updateOrderStatus(purchaseNum, status);
            ra.addFlashAttribute("message", "주문 상태가 '" + status + "'(으)로 변경되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchases";
    }

    private void prepareOrderForm(Model model,
                                  HttpSession session,
                                  MemberResponse memberDTO,
                                  List<CartItemResponse> items) {
        String purchaseToken = purchaseDraftService.storeDraft(items, session);

        model.addAttribute("member", memberDTO);
        model.addAttribute("items", items);
        model.addAttribute("purchaseToken", purchaseToken);
        model.addAttribute("totalItemCount", items.size());
        model.addAttribute("totalQuantity", items.stream().mapToInt(item -> item.getCart().getCartQty()).sum());
        model.addAttribute("totalPayment", items.stream()
                .mapToInt(item -> item.getGoods().getGoodsPrice() * item.getCart().getCartQty())
                .sum());
    }

    private void applyDraftToCommand(PurchaseRequest command, PurchaseDraft draft) {
        List<PurchaseDraftItem> draftItems = draft.items();
        String[] goodsNums = new String[draftItems.size()];
        String[] goodsQtys = new String[draftItems.size()];

        for (int i = 0; i < draftItems.size(); i++) {
            PurchaseDraftItem item = draftItems.get(i);
            goodsNums[i] = item.goodsNum();
            goodsQtys[i] = String.valueOf(item.quantity());
        }

        command.setGoodsNums(goodsNums);
        command.setGoodsQtys(goodsQtys);
    }
}
