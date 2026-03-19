package lappick.goods.dto;

import java.util.Date;
import lombok.Data;

@Data
public class StockHistoryResponse {
    private String ipgoNum;
    private String goodsNum;
    private Integer ipgoQty;
    private Date ipgoDate;
    private String ipgoMemo;
}
