package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 群聊实体
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
@Data
@TableName("t_group")
@NoArgsConstructor
public class Group {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer creatorId;
    private Integer groupType;
    private String groupName;
    private String avatar;
    private Integer number;
    private Date deleteTime;
    private Date createTime;
    @TableField(update = "now()")
    private Date updateTime;

    public Group(int creatorId, String groupName) {
        this.creatorId = creatorId;
        this.groupName = groupName;
    }
}
