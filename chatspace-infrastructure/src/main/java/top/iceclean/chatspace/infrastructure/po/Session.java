package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话类
 * @author : Ice'Clean
 * @date : 2022-06-11
 */
@Data
@TableName("t_session")
@NoArgsConstructor
public class Session {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer type;
    private Integer targetId;
    private Date deleteTime;
    private Date createTime;
    @TableField(update = "now()")
    private Date updateTime;

    public Session(int type, int targetId) {
        this.type = type;
        this.targetId = targetId;
    }
}
