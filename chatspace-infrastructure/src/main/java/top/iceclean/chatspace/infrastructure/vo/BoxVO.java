package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 箱子响应对象
 * @author : Ice'Clean
 * @date : 2022-12-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoxVO {
    /** 箱子物品项 */
    @Data
    public static class BoxItem {
        /** 物品项 ID */
        private Integer boxItemId;
        /** 物品所在背包的位置 */
        private Integer boxIdx;
        /** 物品的名称 */
        private String itemName;
        /** 物品的别名 */
        private String aliasName;
        /** 物品的数量 */
        private Integer amount;
    }

    /** 箱子 ID */
    private Integer boxId;
    /** 箱子的物品列表 */
    private List<BoxItem> itemList;
}
