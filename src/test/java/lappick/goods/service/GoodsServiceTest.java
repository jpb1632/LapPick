package lappick.goods.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        goodsService = new GoodsService(goodsMapper, employeeMapper, autoNumService);
        tempDir = Files.createTempDirectory("goods-service-test");
        ReflectionTestUtils.setField(goodsService, "fileDir", tempDir.toString());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("employee1", "pw"));
    }

    @AfterEach
    void tearDown() throws IOException {
        SecurityContextHolder.clearContext();
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void createGoods_generatesGoodsNumberAndStoresUploadedMainImage() {
        GoodsRequest request = createGoodsRequest();

        when(autoNumService.nextIdFromSequence("GOODS_NUM_SEQ", "goods_")).thenReturn("goods_100123");
        when(employeeMapper.getEmpNum("employee1")).thenReturn("emp_100001");

        goodsService.createGoods(request);

        ArgumentCaptor<GoodsResponse> goodsCaptor = ArgumentCaptor.forClass(GoodsResponse.class);
        verify(goodsMapper).goodsInsert(goodsCaptor.capture());
        verify(goodsMapper).insertGoodsIpgo(eq("goods_100123"), eq(5), anyString());

        GoodsResponse savedGoods = goodsCaptor.getValue();
        assertThat(savedGoods.getGoodsNum()).isEqualTo("goods_100123");
        assertThat(savedGoods.getEmpNum()).isEqualTo("emp_100001");
        assertThat(savedGoods.getGoodsMainStoreImage()).isNotBlank();
        assertThat(savedGoods.getGoodsMainStoreImage()).endsWith(".jpg");
        assertThat(savedGoods.getGoodsMainStoreImage()).isNotEqualTo("main.jpg");
        assertThat(savedGoods.getGoodsScreenSize()).isEqualTo(15.6);
        assertThat(savedGoods.getGoodsWeight()).isEqualTo(1.25);
        assertThat(Files.exists(tempDir.resolve(savedGoods.getGoodsMainStoreImage()))).isTrue();
    }

    @Test
    void updateGoods_replacesMainImageAndDeletesOldStoredFile() throws IOException {
        Path oldFile = tempDir.resolve("old-main.jpg");
        Files.writeString(oldFile, "old-image");

        GoodsResponse existingGoods = new GoodsResponse();
        existingGoods.setGoodsNum("goods_100123");
        existingGoods.setGoodsName("Existing");
        existingGoods.setGoodsPrice(1000);
        existingGoods.setGoodsContents("content");
        existingGoods.setGoodsBrand("LG");
        existingGoods.setGoodsPurpose("OFFICE");
        existingGoods.setGoodsScreenSize(15.6);
        existingGoods.setGoodsWeight(1.25);
        existingGoods.setGoodsSellerInfo("seller");
        existingGoods.setGoodsMainStoreImage("old-main.jpg");
        existingGoods.setGoodsDetailStoreImage("old-main.jpg");

        GoodsRequest command = createGoodsRequest();
        command.setGoodsNum("goods_100123");
        command.setGoodsMainImage(new MockMultipartFile(
                "goodsMainImage",
                "new-main.jpg",
                "image/jpeg",
                "new-image".getBytes()
        ));

        when(goodsMapper.selectOne("goods_100123")).thenReturn(existingGoods);
        when(employeeMapper.getEmpNum("employee1")).thenReturn("emp_100001");

        goodsService.updateGoods(command, null, null);

        ArgumentCaptor<GoodsResponse> captor = ArgumentCaptor.forClass(GoodsResponse.class);
        verify(goodsMapper).goodsUpdate(captor.capture());

        GoodsResponse updatedGoods = captor.getValue();
        assertThat(updatedGoods.getGoodsMainStoreImage()).isNotEqualTo("old-main.jpg");
        assertThat(updatedGoods.getGoodsDetailStoreImage()).contains(updatedGoods.getGoodsMainStoreImage());
        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(tempDir.resolve(updatedGoods.getGoodsMainStoreImage()))).isTrue();
    }

    @Test
    void changeStock_insertsIpgoHistoryAfterDecrease() {
        goodsService.changeStock("goods_100123", -2, "Order #PUR-1");

        verify(goodsMapper).insertGoodsIpgo("goods_100123", -2, "Order #PUR-1");
    }

    @Test
    void deleteGoods_rejectsGoodsWithPurchaseOrQnaHistory() {
        GoodsResponse blockedGoods = new GoodsResponse();
        blockedGoods.setGoodsNum("goods_100001");
        blockedGoods.setGoodsName("LG gram");

        when(goodsMapper.selectDeleteBlockedGoods(List.of("goods_100001")))
                .thenReturn(List.of(blockedGoods));

        assertThatThrownBy(() -> goodsService.deleteGoods(new String[]{"goods_100001"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("goods_100001");

        verify(goodsMapper, never()).deleteGoodsIpgo(anyList());
        verify(goodsMapper, never()).goodsDelete(anyList());
    }

    private GoodsRequest createGoodsRequest() {
        GoodsRequest request = new GoodsRequest();
        request.setGoodsName("Test Notebook");
        request.setGoodsPrice(1_500_000);
        request.setGoodsContents("A notebook for tests");
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
        request.setGoodsDetailImage(new MockMultipartFile[0]);
        request.setGoodsDetail(new MockMultipartFile[0]);
        return request;
    }
}
