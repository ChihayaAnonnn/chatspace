package top.iceclean.chatspace.realtime.processor.message;

import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.GroupCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.realtime.common.DataGenerator;
import top.iceclean.chatspace.realtime.handler.HandlerHelper;
import top.iceclean.chatspace.realtime.server.ServerProperties;
import top.iceclean.feign.GroupClient;
import top.iceclean.feign.UserClient;

/**
 * 用户消息处理器
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Component
public class UserProcessor {
    /** 用户内部服务 */
    private final UserClient userClient;
    /** 群组内部服务 */
    private final GroupClient groupClient;
    /** 用户缓存服务 */
    private final UserCache userCache;
    /** 群组缓存服务 */
    private final GroupCache groupCache;
    /** 服务配置 */
    private final ServerProperties properties;

    @Autowired
    public UserProcessor(UserClient userClient, GroupClient groupClient,
                         UserCache userCache, GroupCache groupCache,
                         ServerProperties properties) {
        this.userClient = userClient;
        this.groupClient = groupClient;
        this.userCache = userCache;
        this.groupCache = groupCache;
        this.properties = properties;
    }

    /**
     * 用户在线状态改变处理，在用户上线时返回用户实体
     * @param userId 用户 ID
     * @param online 新的在线状态
     */
    public User changeOnline(int userId, boolean online) {
        // 设置用户上线标记
        userCache.setOnline(userId, online);
        // 设置用户所处的所有群组的上线标记
        groupClient.getGroupIdList(userId).forEach(groupKey ->
                groupCache.setUserOnline(groupKey, userId, online));
        // 向所有好友发送用户在线状态改变通知
        // BaseProcessor.castMessage(WsType.USER_ONLINE, new DataGenerator.UserOnline(userId, online));

        if (online) {
            // 设置用户所在服务节点
            userCache.setServerNode(userId, properties.getServerId());
            return userCache.getUserById(userId);
        } else {
            // 移除用户信息
            // userCache.removeLoginCache(userId);
        }
        return null;
    }

    /**
     * 向指定通道的用户发送强制下线消息
     */
    public void forcedOffline(Channel channel, String msg) {
        BaseProcessor.castMessage(WsType.USER_FORCED_OFFLINE, channel, msg);
    }
}
