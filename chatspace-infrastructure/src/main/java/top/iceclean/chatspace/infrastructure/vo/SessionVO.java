package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.constant.SessionType;

/**
 * 会话响应对象
 * @author : Ice'Clean
 * @date : 2022-06-11
 */
@Data
@NoArgsConstructor
public class SessionVO {
    /** 会话 ID */
    private Integer sessionId;
    /** 会话类型 */
    private SessionType sessionType;
    /** 会话目标 ID */
    private Integer targetId;
    /** 会话的名称 */
    private String name;
    /** 会话的头像 */
    private String avatar;
    /** 会话的总人数，群聊时生效 */
    private Integer number;

    /** 当前会话的在线人数，会话类型为好友时，0/1 表示在线或离线，类型为群聊则是在线人数 */
    private Integer online;
    /** 当前用户在该会话内的未读消息数 */
    private Integer badgeNum;

    /** 确定一个会话，只需要会话 ID 和会话类型 */
    public SessionVO(Integer sessionId, SessionType sessionType, int targetId) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.targetId = targetId;
    }

    /** 设置类型为群聊的会话的详细信息 */
    public void setDetail(GroupVO group, int badgeNum) {
        this.name = group.getGroupName();
        this.avatar = group.getAvatar();
        this.online = group.getOnlineNum();
        this.number = group.getNumber();
        this.badgeNum = badgeNum;
    }

    /** 设置类型为私聊的会话的详细信息 */
    public void setDetail(FriendVO friend, int badgeNum) {
        this.name = friend.getMarkName() != null ? friend.getMarkName() : friend.getUser().getNickName();
        this.avatar = friend.getUser().getAvatar();
        this.online = friend.getUser().getOnline() ? 1 : 0;
        this.badgeNum = badgeNum;
    }
}
