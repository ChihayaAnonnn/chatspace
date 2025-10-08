package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.po.Friend;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;

/**
 * 朋友响应对象
 * @author : Ice'Clean
 * @date : 2022-06-11
 */
@Data
@NoArgsConstructor
public class FriendVO {
    /** 朋友的用户信息 */
    private UserVO user;

    /** 当前用户和该用户的好友会话 ID */
    private Integer friendId;
    /** 当前用户对该朋友的备注 */
    private String markName;
    /** 开始成为朋友的时间 */
    private String startTime;
    /** 用户最后一条已阅消息 */
    private Integer lastMsgId;

    /**
     * 通过用户响应对象和朋友映射构建朋友响应对象
     * @param user 用户响应对象
     * @param friend 朋友映射
     */
    public FriendVO(UserVO user, Friend friend) {
        this.user = user;
        this.friendId = friend.getFriendId();
        this.markName = friend.getMarkName();
        this.startTime = DateUtils.parseDate(friend.getCreateTime());
        this.lastMsgId = friend.getLastMsgId();
    }
}
