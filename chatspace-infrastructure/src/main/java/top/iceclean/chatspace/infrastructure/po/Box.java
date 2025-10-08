package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;

import java.time.LocalDateTime;
import java.util.Date;

/**
 *  箱子实体
 * @author : Ice'Clean
 * @date : 2022-12-13
 */
@Data
@TableName("t_box")
@NoArgsConstructor
public class Box {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer zoneId;
    private Integer boxType;
    private Integer lockStat;
    private Long huntSeq;
    private LocalDateTime unlockTime;
    private LocalDateTime lockTime;

    public Box(int zoneId) {
        this.zoneId = zoneId;
    }

    public void setBoxLock(String time, boolean lock) {
        if (lock) {
            lockTime = DateUtils.parseDateString(time);
        } else {
            unlockTime = DateUtils.parseDateString(time);
        }
    }
}
