package lappick.cart.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import lappick.cart.dto.CartAddRequest;
import lappick.cart.service.CartService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/cartList")
    public String cartList(
            @RequestParam(value = "searchWord", required = false) String searchWord,
            Principal principal, Model model) {
        
        if (principal == null) return "redirect:/auth/login";
        
        Map<String, Object> cartMap = cartService.getCartList(principal.getName(), searchWord);
        
        model.addAttribute("list", cartMap.get("list"));
        model.addAttribute("totPri", cartMap.get("totalPrice"));
        model.addAttribute("totQty", cartMap.get("totalQty"));
        model.addAttribute("searchWord", searchWord); 
        
        return "user/cart/cart-list";
    }

    @PostMapping("/cartAdd")
    @ResponseBody
    public String cartAdd(@RequestBody CartAddRequest cartRequest, Principal principal) {
        if (principal == null) return "000";
        cartService.addItemToCart(principal.getName(), cartRequest.getGoodsNum(), cartRequest.getQty());
        return "200";
    }

    @GetMapping("/cartQtyDown")
    @ResponseBody
    public void cartQtyDown(@RequestParam("goodsNum") String goodsNum, Principal principal) {
        if (principal != null) {
            cartService.decreaseItemQuantity(principal.getName(), goodsNum);
        }
    }

    @PostMapping("/cartDels")
    @ResponseBody
    public String cartDels(@RequestBody List<String> goodsNums, Principal principal) {
        if (principal == null) return "000";
        cartService.removeItemsFromCart(principal.getName(), goodsNums.toArray(new String[0]));
        return "200";
    }
    
    @PostMapping("/cartDelAll")
    @ResponseBody
    public String cartDelAll(Principal principal) {
        if (principal == null) return "000";
        cartService.removeAllItems(principal.getName());
        return "200";
    }

    @GetMapping("/cartDel")
    public String cartDel(@RequestParam("goodsNums") String goodsNums, Principal principal) {
        if (principal != null) {
            cartService.removeItemsFromCart(principal.getName(), new String[]{goodsNums});
        }
        return "redirect:cartList";
    }
    
    @GetMapping("/count")
    @ResponseBody
    public int getCartItemCount(Principal principal) {
        if (principal == null) {
            return 0;
        }
        return cartService.getCartItemCount(principal.getName());
    }
}
