package lappick.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import lappick.auth.service.AuthService;
import lappick.cart.controller.CartController;
import lappick.cart.service.CartService;
import lappick.config.SecurityConfig;
import lappick.goods.controller.AdminGoodsController;
import lappick.goods.service.GoodsService;
import lappick.member.controller.MyPageController;
import lappick.member.mapper.MemberMapper;
import lappick.member.service.MemberService;
import lappick.review.service.ReviewService;

@WebMvcTest(controllers = {CartController.class, AdminGoodsController.class, MyPageController.class})
@Import(SecurityConfig.class)
class SecurityFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private GoodsService goodsService;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private MemberMapper memberMapper;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void cartDels_blocksRequestWithoutCsrf() throws Exception {
        mockMvc.perform(post("/cart/cartDels")
                        .with(user("member1").authorities(() -> "ROLE_MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"goods_100001\"]"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void cartDels_allowsRequestWithCsrf() throws Exception {
        mockMvc.perform(post("/cart/cartDels")
                        .with(user("member1").authorities(() -> "ROLE_MEMBER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"goods_100001\"]"))
                .andExpect(status().isOk())
                .andExpect(content().string("200"));

        ArgumentCaptor<String[]> goodsNumsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(cartService).removeItemsFromCart(anyString(), goodsNumsCaptor.capture());
        assertThat(goodsNumsCaptor.getValue()).containsExactly("goods_100001");
    }

    @Test
    void adminGoodsDelete_blocksRequestWithoutCsrf() throws Exception {
        mockMvc.perform(post("/admin/goods/delete")
                        .with(user("employee1").authorities(() -> "ROLE_EMPLOYEE"))
                        .param("nums", "goods_100001"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(goodsService);
    }

    @Test
    void adminGoodsDelete_redirectsWithErrorMessageWhenDeleteIsBlocked() throws Exception {
        doThrow(new IllegalStateException("주문 이력이나 문의가 남아 있는 상품은 삭제할 수 없습니다. goods_100001"))
                .when(goodsService).deleteGoods(any(String[].class));

        mockMvc.perform(post("/admin/goods/delete")
                        .with(user("employee1").authorities(() -> "ROLE_EMPLOYEE"))
                        .with(csrf())
                        .param("nums", "goods_100001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/goods"))
                .andExpect(flash().attribute("error", "주문 이력이나 문의가 남아 있는 상품은 삭제할 수 없습니다. goods_100001"));
    }

    @Test
    void withdraw_blocksRequestWithoutCsrf() throws Exception {
        mockMvc.perform(post("/member/withdraw")
                        .with(user("member1").authorities(() -> "ROLE_MEMBER"))
                        .param("memberPw", "rawPw"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(memberService);
    }

    @Test
    void withdraw_logsOutMemberWhenRequestIsValid() throws Exception {
        mockMvc.perform(post("/member/withdraw")
                        .with(user("member1").authorities(() -> "ROLE_MEMBER"))
                        .with(csrf())
                        .param("memberPw", "rawPw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?message=withdrawSuccess"));

        verify(memberService).withdrawMember("member1", "rawPw");
    }
}
