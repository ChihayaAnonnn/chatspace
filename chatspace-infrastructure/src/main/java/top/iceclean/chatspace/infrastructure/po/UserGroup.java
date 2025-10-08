package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户群聊映射实体
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
@Data
@TableName("t_user_group")
@NoArgsConstructor
public class UserGroup {
    @TableId(value = "map_id", type = IdType.AUTO)
    private Integer mapId;
    private Integer userId;
    private Integer groupId;
    private Integer lastMsgId;
    private String markName;
    private Date deleteTime;
    private Date createTime;

    public UserGroup(Integer userId, Integer groupId, Integer lastMsgId) {
        this.userId = userId;
        this.groupId = groupId;
        this.lastMsgId = lastMsgId;
    }
}
