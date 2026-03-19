package lappick.goods.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lappick.admin.employee.mapper.EmployeeMapper;
import lappick.common.dto.FileDTO;
import lappick.common.dto.PageData;
import lappick.common.service.AutoNumService;
import lappick.goods.dto.GoodsFilterRequest;
import lappick.goods.dto.GoodsPageResponse;
import lappick.goods.dto.GoodsRequest;
import lappick.goods.dto.GoodsResponse;
import lappick.goods.dto.GoodsSalesResponse;
import lappick.goods.dto.GoodsStockResponse;
import lappick.goods.dto.StockHistoryPageResponse;
import lappick.goods.dto.StockHistoryResponse;
import lappick.goods.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(timeout = 10)
@RequiredArgsConstructor
public class GoodsService {

    private static final Logger log = LoggerFactory.getLogger(GoodsService.class);
    private static final String DEFAULT_SHIPPING_INFO = "999,000원 이상 무료배송";

    @Value("${file.upload.dir}")
    private String fileDir;

    private final GoodsMapper goodsMapper;
    private final EmployeeMapper employeeMapper;
    private final AutoNumService autoNumService;

    @Transactional(readOnly = true)
    public GoodsPageResponse getGoodsListPage(GoodsFilterRequest filter, int limit) {
        long startRow = (filter.getPage() - 1L) * limit + 1;
        long endRow = filter.getPage() * 1L * limit;
        filter.setStartRow(startRow);
        filter.setEndRow(endRow);

        List<GoodsResponse> list = goodsMapper.allSelect(filter);
        int total = goodsMapper.goodsCount(filter);
        int totalPages = (int) Math.ceil(total / (double) limit);

        return GoodsPageResponse.builder()
                .items(list)
                .page(filter.getPage())
                .size(limit)
                .total(total)
                .totalPages(totalPages)
                .searchWord(filter.getSearchWord())
                .build();
    }

    @Transactional(readOnly = true)
    public GoodsResponse getGoodsDetail(String goodsNum) {
        return goodsMapper.selectOne(goodsNum);
    }

    public void createGoods(GoodsRequest command) {
        validateFileCount(command);

        GoodsResponse dto = new GoodsResponse();
        String goodsNum = autoNumService.nextIdFromSequence("GOODS_NUM_SEQ", "goods_");

        applyGoodsFields(dto, command);
        dto.setGoodsNum(goodsNum);
        dto.setEmpNum(getCurrentEmployeeNum());

        FileDTO mainImage = uploadFile(command.getGoodsMainImage());
        if (mainImage == null) {
            throw new IllegalArgumentException("대표 이미지를 등록해주세요.");
        }

        dto.setGoodsMainImage(mainImage.getOrgFile());
        dto.setGoodsMainStoreImage(mainImage.getStoreFile());

        List<String> detailStoreImages = new ArrayList<>();
        detailStoreImages.add(mainImage.getStoreFile());
        String extraDetailImages = uploadMultipleFiles(command.getGoodsDetailImage());
        if (extraDetailImages != null && !extraDetailImages.isBlank()) {
            detailStoreImages.addAll(Arrays.asList(extraDetailImages.split("/")));
        }
        dto.setGoodsDetailStoreImage(detailStoreImages.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining("/")));
        dto.setGoodsDetailStore(uploadMultipleFiles(command.getGoodsDetail()));

        goodsMapper.goodsInsert(dto);

        if (command.getInitialStock() != null && command.getInitialStock() > 0) {
            goodsMapper.insertGoodsIpgo(goodsNum, command.getInitialStock(), "신규 등록");
        }
    }

    public void updateGoods(GoodsRequest command, List<String> imagesToDelete, List<String> detailDescImagesToDelete) {
        validateFileCountForUpdate(command, imagesToDelete);

        GoodsResponse dto = goodsMapper.selectOne(command.getGoodsNum());
        if (dto == null) {
            throw new IllegalArgumentException("수정할 상품 정보를 찾을 수 없습니다.");
        }

        applyGoodsFields(dto, command);
        dto.setUpdateEmpNum(getCurrentEmployeeNum());

        String previousMainStoreImage = dto.getGoodsMainStoreImage();
        String currentMainStoreImage = previousMainStoreImage;
        List<String> filesToDeleteAfterSave = new ArrayList<>();

        if (command.getGoodsMainImage() != null && !command.getGoodsMainImage().isEmpty()) {
            FileDTO mainImage = uploadFile(command.getGoodsMainImage());
            if (mainImage == null) {
                throw new IllegalArgumentException("대표 이미지를 등록해주세요.");
            }

            currentMainStoreImage = mainImage.getStoreFile();
            dto.setGoodsMainImage(mainImage.getOrgFile());
            dto.setGoodsMainStoreImage(mainImage.getStoreFile());

            if (previousMainStoreImage != null && !previousMainStoreImage.isBlank()) {
                filesToDeleteAfterSave.add(previousMainStoreImage);
            }
        }

        dto.setGoodsDetailStoreImage(mergeThumbnailFiles(
                dto.getGoodsDetailStoreImage(),
                imagesToDelete,
                command.getGoodsDetailImage(),
                currentMainStoreImage,
                previousMainStoreImage,
                filesToDeleteAfterSave
        ));

        dto.setGoodsDetailStore(mergeFileList(
                dto.getGoodsDetailStore(),
                detailDescImagesToDelete,
                command.getGoodsDetail(),
                filesToDeleteAfterSave
        ));

        goodsMapper.goodsUpdate(dto);
        filesToDeleteAfterSave.stream().distinct().forEach(this::deleteFile);
    }

    @Transactional
    public void deleteGoods(String[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("삭제할 상품을 선택해주세요.");
        }

        List<String> goodsNumList = Arrays.stream(nums)
                .filter(num -> num != null && !num.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (goodsNumList.isEmpty()) {
            throw new IllegalArgumentException("삭제할 상품을 선택해주세요.");
        }

        List<GoodsResponse> blockedGoods = goodsMapper.selectDeleteBlockedGoods(goodsNumList);
        if (!blockedGoods.isEmpty()) {
            throw new IllegalStateException(buildDeleteBlockedMessage(blockedGoods));
        }

        List<GoodsResponse> goodsForDelete = goodsMapper.selectGoodsByNumList(goodsNumList);

        goodsMapper.deleteGoodsIpgo(goodsNumList);
        goodsMapper.goodsDelete(goodsNumList);
        deleteGoodsFiles(goodsForDelete);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            timeout = 5,
            rollbackFor = Exception.class
    )
    public void changeStock(String goodsNum, int quantity, String memo) {
        if (quantity == 0) {
            throw new IllegalArgumentException("변경 수량은 0이 될 수 없습니다.");
        }

        String finalMemo = memo;
        if (quantity > 0 && (memo == null || memo.isBlank())) {
            finalMemo = "정기 입고";
        } else if (quantity < 0 && (memo == null || memo.isBlank())) {
            throw new IllegalArgumentException("재고 차감 시에는 변경 사유를 반드시 입력해야 합니다.");
        }

        goodsMapper.insertGoodsIpgo(goodsNum, quantity, finalMemo);
    }

    @Transactional(readOnly = true, timeout = 3)
    public GoodsStockResponse getGoodsDetailWithStock(String goodsNum) {
        return goodsMapper.selectOneWithStock(goodsNum);
    }

    @Transactional(readOnly = true)
    public StockHistoryPageResponse getStockHistoryPage(String goodsNum, int page, int size) {
        int total = goodsMapper.countIpgoHistory(goodsNum);
        int totalPages = (int) Math.ceil(total / (double) size);

        long startRow = (page - 1L) * size + 1;
        long endRow = page * 1L * size;

        Map<String, Object> params = new HashMap<>();
        params.put("goodsNum", goodsNum);
        params.put("startRow", startRow);
        params.put("endRow", endRow);

        List<StockHistoryResponse> items = goodsMapper.selectIpgoHistoryPaged(params);

        int paginationRange = 5;
        int startPage = (int) (Math.floor((page - 1) / (double) paginationRange) * paginationRange + 1);
        int endPage = Math.min(startPage + paginationRange - 1, totalPages);

        return StockHistoryPageResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .totalPages(totalPages)
                .startPage(startPage)
                .endPage(endPage)
                .hasPrev(startPage > 1)
                .hasNext(endPage < totalPages)
                .build();
    }

    @Transactional(readOnly = true)
    public List<GoodsResponse> getAllGoodsForFilter() {
        return goodsMapper.selectAllForFilter();
    }

    @Transactional(readOnly = true)
    public List<GoodsResponse> getBestGoodsList() {
        return goodsMapper.selectBestGoodsList();
    }

    @Transactional(readOnly = true)
    public PageData<GoodsSalesResponse> getGoodsSalesStatusPage(String sortBy, String sortDir, String searchWord, int page, int size) {
        String direction = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        Map<String, Object> params = new HashMap<>();
        params.put("sortBy", sortBy);
        params.put("sortDir", direction);
        params.put("searchWord", searchWord);

        int total = goodsMapper.countGoodsSalesStatus(params);

        long startRow = (page - 1L) * size + 1;
        long endRow = page * 1L * size;
        params.put("startRow", startRow);
        params.put("endRow", endRow);

        List<GoodsSalesResponse> items = goodsMapper.findGoodsSalesStatusPaginated(params);
        return new PageData<>(items, page, size, total, searchWord);
    }

    private void applyGoodsFields(GoodsResponse dto, GoodsRequest command) {
        dto.setGoodsName(command.getGoodsName());
        dto.setGoodsPrice(command.getGoodsPrice());
        dto.setGoodsContents(command.getGoodsContents());
        dto.setGoodsBrand(command.getGoodsBrand());
        dto.setGoodsPurpose(command.getGoodsPurpose());
        dto.setGoodsKeyword1(command.getGoodsKeyword1());
        dto.setGoodsKeyword2(command.getGoodsKeyword2());
        dto.setGoodsKeyword3(command.getGoodsKeyword3());
        dto.setGoodsShippingInfo(
                command.getGoodsShippingInfo() == null || command.getGoodsShippingInfo().isBlank()
                        ? DEFAULT_SHIPPING_INFO
                        : command.getGoodsShippingInfo()
        );
        dto.setGoodsSellerInfo(command.getGoodsSellerInfo());

        if (command.getGoodsScreenSize() != null) {
            dto.setGoodsScreenSize(command.getGoodsScreenSize() / 10.0);
        }
        if (command.getGoodsWeight() != null) {
            dto.setGoodsWeight(command.getGoodsWeight() / 100.0);
        }
    }

    private void validateFileCount(GoodsRequest command) {
        if (command.getGoodsDetailImage() != null && command.getGoodsDetailImage().length > 10) {
            throw new IllegalArgumentException("상세이미지(썸네일)는 최대 10개까지 등록 가능합니다.");
        }
        if (command.getGoodsDetail() != null && command.getGoodsDetail().length > 5) {
            throw new IllegalArgumentException("상세 설명 이미지는 최대 5개까지 등록 가능합니다.");
        }
    }

    private void validateFileCountForUpdate(GoodsRequest command, List<String> imagesToDelete) {
        int existingDetailCount = 0;
        if (command.getGoodsDetailImage() != null) {
            existingDetailCount += command.getGoodsDetailImage().length;
        }

        GoodsResponse dto = goodsMapper.selectOne(command.getGoodsNum());
        if (dto == null) {
            throw new IllegalArgumentException("수정할 상품 정보를 찾을 수 없습니다.");
        }

        if (dto.getGoodsDetailStoreImage() != null && !dto.getGoodsDetailStoreImage().isEmpty()) {
            List<String> existingList = new ArrayList<>(Arrays.asList(dto.getGoodsDetailStoreImage().split("/")));
            if (imagesToDelete != null) {
                existingList.removeAll(imagesToDelete);
            }
            existingDetailCount += existingList.size();
        }

        if (existingDetailCount > 11) {
            throw new IllegalArgumentException("상세이미지(썸네일)는 최대 10개, 대표 이미지 포함 총 11개까지 등록 가능합니다.");
        }
        if (command.getGoodsDetail() != null && command.getGoodsDetail().length > 5) {
            throw new IllegalArgumentException("상세 설명 이미지는 최대 5개까지 등록 가능합니다.");
        }
    }

    private String uploadMultipleFiles(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return null;
        }

        return Arrays.stream(files)
                .filter(file -> file != null && !file.isEmpty())
                .map(this::uploadFile)
                .map(FileDTO::getStoreFile)
                .collect(Collectors.joining("/"));
    }

    private String mergeThumbnailFiles(String existingFileNames,
                                       List<String> filesToDelete,
                                       MultipartFile[] newFiles,
                                       String currentMainImage,
                                       String previousMainImage,
                                       List<String> filesToDeleteAfterSave) {
        List<String> fileList = new ArrayList<>();

        if (currentMainImage != null && !currentMainImage.isBlank()) {
            fileList.add(currentMainImage);
        }

        if (existingFileNames != null && !existingFileNames.isEmpty()) {
            Arrays.stream(existingFileNames.split("/"))
                    .filter(img -> img != null && !img.isBlank())
                    .forEach(img -> {
                        boolean removedByMainChange = previousMainImage != null
                                && !previousMainImage.equals(currentMainImage)
                                && previousMainImage.equals(img);
                        boolean removedExplicitly = filesToDelete != null && filesToDelete.contains(img);
                        boolean isCurrentMainImage = img.equals(currentMainImage);

                        if (removedByMainChange || removedExplicitly) {
                            filesToDeleteAfterSave.add(img);
                            return;
                        }
                        if (!isCurrentMainImage) {
                            fileList.add(img);
                        }
                    });
        }

        if (newFiles != null && newFiles.length > 0) {
            Arrays.stream(newFiles)
                    .filter(file -> file != null && !file.isEmpty())
                    .map(this::uploadFile)
                    .map(FileDTO::getStoreFile)
                    .forEach(fileList::add);
        }

        return fileList.stream().distinct().collect(Collectors.joining("/"));
    }

    private String mergeFileList(String existingFileNames,
                                 List<String> filesToDelete,
                                 MultipartFile[] newFiles,
                                 List<String> filesToDeleteAfterSave) {
        List<String> fileList = new ArrayList<>();
        if (existingFileNames != null && !existingFileNames.isEmpty()) {
            fileList.addAll(Arrays.stream(existingFileNames.split("/"))
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toList()));
        }

        if (filesToDelete != null) {
            for (String fileToDelete : filesToDelete) {
                if (fileList.remove(fileToDelete)) {
                    filesToDeleteAfterSave.add(fileToDelete);
                }
            }
        }

        if (newFiles != null && newFiles.length > 0) {
            Arrays.stream(newFiles)
                    .filter(file -> file != null && !file.isEmpty())
                    .map(this::uploadFile)
                    .map(FileDTO::getStoreFile)
                    .forEach(fileList::add);
        }

        return fileList.stream().distinct().collect(Collectors.joining("/"));
    }

    private void deleteGoodsFiles(List<GoodsResponse> goodsForDelete) {
        for (GoodsResponse goods : goodsForDelete) {
            deleteFile(goods.getGoodsMainStoreImage());

            if (goods.getGoodsDetailStoreImage() != null && !goods.getGoodsDetailStoreImage().isBlank()) {
                Arrays.stream(goods.getGoodsDetailStoreImage().split("/"))
                        .forEach(this::deleteFile);
            }

            if (goods.getGoodsDetailStore() != null && !goods.getGoodsDetailStore().isBlank()) {
                Arrays.stream(goods.getGoodsDetailStore().split("/"))
                        .forEach(this::deleteFile);
            }
        }
    }

    private String buildDeleteBlockedMessage(List<GoodsResponse> blockedGoods) {
        String goodsSummary = blockedGoods.stream()
                .limit(3)
                .map(goods -> goods.getGoodsName() + "(" + goods.getGoodsNum() + ")")
                .collect(Collectors.joining(", "));

        if (blockedGoods.size() > 3) {
            goodsSummary += " 외 " + (blockedGoods.size() - 3) + "개";
        }

        return "주문 이력이나 문의가 남아 있는 상품은 삭제할 수 없습니다. " + goodsSummary;
    }

    private void deleteFile(String storeFileName) {
        if (storeFileName == null || storeFileName.isBlank()) {
            return;
        }

        File file = new File(fileDir, storeFileName);
        if (file.exists() && !file.delete()) {
            log.warn("파일 삭제에 실패했습니다: {}", storeFileName);
        }
    }

    private FileDTO uploadFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        String originalFile = multipartFile.getOriginalFilename();
        String safeOriginalFile = (originalFile == null || originalFile.isBlank()) ? "upload-file" : originalFile;
        String extension = "";
        int extensionIndex = safeOriginalFile.lastIndexOf('.');
        if (extensionIndex >= 0) {
            extension = safeOriginalFile.substring(extensionIndex);
        }

        String storeFileName = UUID.randomUUID().toString().replace("-", "") + extension;
        File file = new File(fileDir, storeFileName);

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            multipartFile.transferTo(file);
            return new FileDTO(safeOriginalFile, storeFileName);
        } catch (IOException e) {
            if (file.exists() && !file.delete()) {
                log.warn("실패한 업로드 파일 정리에 실패했습니다: {}", storeFileName);
            }
            log.error("파일 업로드 중 오류가 발생했습니다: {}", safeOriginalFile, e);
            throw new IllegalStateException("파일 업로드 중 오류가 발생했습니다.", e);
        }
    }

    private String getCurrentEmployeeNum() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return employeeMapper.getEmpNum(auth.getName());
    }
}
