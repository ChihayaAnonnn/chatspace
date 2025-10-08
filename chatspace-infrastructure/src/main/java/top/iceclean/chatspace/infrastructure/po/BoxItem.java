package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 箱子的物品实体
 * @author : Ice'Clean
 * @date : 2022-12-12
 */
@Data
@TableName("t_box_item")
@NoArgsConstructor
public class BoxItem {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer boxId;
    private Integer boxIdx;
    private Integer itemId;
    private Integer amount;
    private Integer lockStat;

    public BoxItem(Integer boxId, Integer boxIdx, Integer itemId, Integer amount) {
        this.boxId = boxId;
        this.boxIdx = boxIdx;
        this.itemId = itemId;
        this.amount = amount;
    }
}
