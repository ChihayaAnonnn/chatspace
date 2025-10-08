package top.iceclean.chatspace.infrastructure.vo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import lombok.Data;
import top.iceclean.chatspace.infrastructure.constant.ItemType;
import top.iceclean.chatspace.infrastructure.po.Item;

import java.util.Map;

/**
 * 物品项响应对象
 * @author : Ice'Clean
 * @date : 2023-02-01
 */
@Data
public class ItemVO {
    private Integer itemId;
    private String itemName;
    private String aliasName;
    private String itemCate;
    private String itemIntro;

    private Map<String, Object> itemExtend;
    private String parsedExtend;

    public ItemVO(Item item) {
        this.itemId = item.getId();
        this.itemName = item.getItemName();
        this.itemCate = item.getItemCate();
        this.itemIntro = item.getItemIntro();
        this.itemExtend = JSON.parseObject(item.getItemExtend(), Map.class);
        parseExtend(ItemType.get(item.getMetaItemId()));
    }

    private void parseExtend(ItemType type) {
        if (itemExtend != null) {
            // 设置别名
            if (itemExtend.containsKey("name")) {
                this.aliasName = itemExtend.get("name").toString();
            }
            StringBuilder parsed = new StringBuilder();
            switch (type) {
                case PAPER:
                    parsed.append("还能写下 ").append(itemExtend.get("word")).append(" 个字<br>")
                            .append(itemExtend.get("content"));
                    break;
                case ELECTRONIC_CARD:
                    parsed.append("0".equals(itemExtend.get("auth")) ? itemExtend.get("deny") :
                            itemExtend.get("access"));
                    break;
                case KEY:
                    parsed.append(itemExtend.get("content"));
                    break;
                default:
            }
            parsedExtend = parsed.toString();
        }
    }
}
