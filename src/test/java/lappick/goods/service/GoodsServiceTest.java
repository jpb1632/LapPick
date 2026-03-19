package lappick.goods.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import lappick.admin.employee.mapper.EmployeeMapper;
import lappick.common.service.AutoNumService;
import lappick.goods.dto.GoodsRequest;
import lappick.goods.dto.GoodsResponse;
import lappick.goods.mapper.GoodsMapper;

@ExtendWith(MockitoExtension.class)
class GoodsServiceTest {

    @Mock
    private GoodsMapper goodsMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private AutoNumService autoNumService;

    private GoodsService goodsService;

    @BeforeEach
    void setUp() {
        goodsService = new GoodsService(goodsMapper, employeeMapper, autoNumService);
        ReflectionTestUtils.setField(goodsService, "fileDir", System.getProperty("java.io.tmpdir"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("employee1", "pw"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createGoods_generatesGoodsNumberWhenSaving() {
        GoodsRequest request = new GoodsRequest();
        request.setGoodsName("테스트 노트북");
        request.setGoodsPrice(1500000);
        request.setGoodsContents("상품 설명");
        request.setGoodsBrand("LG");
        request.setGoodsPurpose("OFFICE");
        request.setGoodsScreenSize(156);
        request.setGoodsWeight(125);
        request.setGoodsSellerInfo("LapPick");
        request.setInitialStock(5);
        request.setGoodsMainImage(new MockMultipartFile(
                "goodsMainImage",
                "main.jpg",
                "image/jpeg",
                "image-data".getBytes()
        ));

        when(autoNumService.nextIdFromSequence("GOODS_NUM_SEQ", "goods_")).thenReturn("goods_100123");
        when(employeeMapper.getEmpNum("employee1")).thenReturn("emp_100001");

        goodsService.createGoods(request);

        ArgumentCaptor<GoodsResponse> goodsCaptor = ArgumentCaptor.forClass(GoodsResponse.class);
        verify(goodsMapper).goodsInsert(goodsCaptor.capture());
        verify(goodsMapper).insertGoodsIpgo("goods_100123", 5, "신규 등록");

        GoodsResponse savedGoods = goodsCaptor.getValue();
        assertThat(savedGoods.getGoodsNum()).isEqualTo("goods_100123");
        assertThat(savedGoods.getEmpNum()).isEqualTo("emp_100001");
    }

    @Test
    void deleteGoods_rejectsGoodsWithPurchaseOrQnaHistory() {
        GoodsResponse blockedGoods = new GoodsResponse();
        blockedGoods.setGoodsNum("goods_100001");
        blockedGoods.setGoodsName("LG전자 15 그램");

        when(goodsMapper.selectDeleteBlockedGoods(List.of("goods_100001")))
                .thenReturn(List.of(blockedGoods));

        assertThatThrownBy(() -> goodsService.deleteGoods(new String[]{"goods_100001"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("주문 이력이나 문의가 남아 있는 상품은 삭제할 수 없습니다.")
                .hasMessageContaining("goods_100001");

        verify(goodsMapper, never()).deleteGoodsIpgo(anyList());
        verify(goodsMapper, never()).goodsDelete(anyList());
    }
}
