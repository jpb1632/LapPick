package lappick.goods.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import lappick.goods.dto.GoodsFilterRequest;
import lappick.goods.dto.GoodsPageResponse;
import lappick.goods.dto.GoodsRequest;
import lappick.goods.dto.GoodsStockResponse;
import lappick.goods.dto.StockHistoryPageResponse;
import lappick.goods.service.GoodsService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/goods")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_EMPLOYEE')")
public class AdminGoodsController {

    private final GoodsService goodsService;

    @GetMapping
    public String goodsList(GoodsFilterRequest filter, Model model) {
        GoodsPageResponse pageData = goodsService.getGoodsListPage(filter, 5);

        model.addAttribute("pageData", pageData);
        model.addAttribute("goodsList", pageData.getItems());
        model.addAttribute("filter", filter);
        return "admin/goods/goods-list";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        model.addAttribute("goodsCommand", new GoodsRequest());
        return "admin/goods/goods-form";
    }

    @PostMapping("/add")
    public String addGoods(@Validated GoodsRequest command, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("goodsCommand", command);
            return "admin/goods/goods-form";
        }

        try {
            goodsService.createGoods(command);
        } catch (IllegalArgumentException e) {
            model.addAttribute("goodsCommand", command);
            model.addAttribute("error", e.getMessage());
            return "admin/goods/goods-form";
        }

        ra.addFlashAttribute("message", "상품이 성공적으로 등록되었습니다.");
        return "redirect:/admin/goods";
    }

    @GetMapping("/{goodsNum}")
    public String goodsDetail(@PathVariable("goodsNum") String goodsNum,
                              @RequestParam(value = "historyPage", defaultValue = "1") int historyPage,
                              @RequestParam(value = "showHistory", required = false, defaultValue = "false") boolean showHistory,
                              Model model,
                              RedirectAttributes ra) {
        GoodsStockResponse dto = goodsService.getGoodsDetailWithStock(goodsNum);
        if (dto == null) {
            ra.addFlashAttribute("error", "상품 정보를 찾을 수 없습니다.");
            return "redirect:/admin/goods";
        }
        model.addAttribute("goodsCommand", dto);

        StockHistoryPageResponse historyPageData = goodsService.getStockHistoryPage(goodsNum, historyPage, 5);
        model.addAttribute("historyPageData", historyPageData);
        model.addAttribute("showHistory", showHistory);

        return "admin/goods/goods-detail";
    }

    @GetMapping("/{goodsNum}/edit")
    public String editForm(@PathVariable("goodsNum") String goodsNum, Model model, HttpSession session) {
        session.removeAttribute("fileList");
        GoodsStockResponse dto = goodsService.getGoodsDetailWithStock(goodsNum);
        model.addAttribute("goodsCommand", dto);
        return "admin/goods/goods-edit";
    }

    @PostMapping("/update")
    public String updateGoods(@Validated GoodsRequest command, BindingResult result,
                              @RequestParam(value = "imagesToDelete", required = false) List<String> imagesToDelete,
                              @RequestParam(value = "detailDescImagesToDelete", required = false) List<String> detailDescImagesToDelete,
                              Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("goodsCommand", command);
            return "admin/goods/goods-edit";
        }

        try {
            goodsService.updateGoods(command, imagesToDelete, detailDescImagesToDelete);
        } catch (IllegalArgumentException e) {
            model.addAttribute("goodsCommand", command);
            model.addAttribute("error", e.getMessage());
            return "admin/goods/goods-edit";
        }

        ra.addFlashAttribute("message", "상품 정보가 성공적으로 수정되었습니다.");
        return "redirect:/admin/goods/" + command.getGoodsNum();
    }

    @PostMapping("/delete")
    public String deleteGoods(@RequestParam("nums") String[] goodsNums, RedirectAttributes ra) {
        try {
            goodsService.deleteGoods(goodsNums);
            ra.addFlashAttribute("message", "선택한 " + goodsNums.length + "개의 상품을 삭제했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/goods";
    }

    @PostMapping("/stock-change")
    public String stockChange(@RequestParam("goodsNum") String goodsNum,
                              @RequestParam("quantity") int quantity,
                              @RequestParam(value = "memo", required = false) String memo,
                              RedirectAttributes ra) {
        try {
            goodsService.changeStock(goodsNum, quantity, memo);
            String message = (quantity > 0)
                    ? "'" + goodsNum + "' 상품을 " + quantity + "개 입고 처리했습니다."
                    : "'" + goodsNum + "' 상품을 " + (-quantity) + "개 출고/차감 처리했습니다.";
            ra.addFlashAttribute("message", message);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/goods";
    }
}
