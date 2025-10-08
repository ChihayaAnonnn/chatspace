package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.po.Group;
import top.iceclean.chatspace.infrastructure.po.UserGroup;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;

/**
 * 群聊响应对象
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
@Data
@NoArgsConstructor
public class GroupVO {
    /** 群聊 ID */
    private Integer groupId;
    /** 群主 ID */
    private Integer creatorId;
    /** 群聊名称 */
    private String groupName;
    /** 群聊头像 */
    private String avatar;
    /** 群聊人数 */
    private Integer number;

    /** 当前群聊公告 */
    private String notice;
    /** 群聊在线人数 */
    private Integer onlineNum;

    /** 用户群聊备注 */
    private String markName;
    /** 用户入群时间 */
    private String inTime;
    /** 用户最后一条已阅的消息 ID */
    private Integer lastMsgId;

    public GroupVO(Group group, int onlineNum) {
        this.groupId = group.getId();
        this.creatorId = group.getCreatorId();
        this.groupName = group.getGroupName();
        this.avatar = group.getAvatar();
        this.number = group.getNumber();

        // TODO 加上公告表时，该字段生效
        this.notice = "无";
        this.onlineNum = onlineNum;
    }

    /** 设置用户在这个群聊的详细信息 */
    public GroupVO setUserGroup(UserGroup userGroup) {
        if (userGroup == null) {
            return this;
        }

        this.markName = userGroup.getMarkName();
        this.inTime = DateUtils.getTime();
        this.lastMsgId = userGroup.getLastMsgId();

        return this;
    }
}
