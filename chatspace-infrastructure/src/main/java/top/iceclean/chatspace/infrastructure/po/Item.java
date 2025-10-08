package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 系统中的物品实体
 * @author : Ice'Clean
 * @date : 2022-12-12
 */
@Data
@TableName(value = "t_item")
public class Item {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer metaItemId;
    private String itemName;
    private String itemCate;
    private String itemIntro;
    private String itemExtend;
    private Date createTime;
    @TableField(update = "now()")
    private Date updateTime;
}
