package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : Ice'Clean
 * @date : 2022-12-17
 */
@Data
@TableName("t_box_hunt")
public class BoxHunt {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long huntSeq;
    private Integer userId;
    private Integer itemId;
    private Integer amount;
    private LocalDateTime createTime;
}
