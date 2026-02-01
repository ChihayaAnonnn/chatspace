package top.iceclean.chatspace.realtime.share;

import io.netty.channel.Channel;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.vo.UserVO;

/**
 * 会话类
 * @author : Ice'Clean
 * @date : 2022-09-29
 */
public class ServerSession {
    /** 用户通道 */
    private final Channel channel;
    /** 用户对象 */
    private final UserVO user;
    /** 用户位置信息 */
    private final SiteVO site;
    /** 用户是否在线，false 即为下线中 */
    private boolean online;

    public ServerSession(Channel channel, User user, Site site) {
        this.channel = channel;
        this.user = new UserVO(user, true);
        this.site = new SiteVO(site, user.getId());
        this.online = true;
    }

    public void offline() {
        this.online = false;
    }

    public UserVO getUser() {
        return online ? user : null;
    }

    public Channel getChannel() {
        return online ? channel : null;
    }

    public SiteVO getSite() {
        return online ? site : null;
    }

    @Override
    public String toString() {
        return "ServerSession{" +
                "channel=" + channel +
                ", user=" + user +
                ", site=" + site +
                '}';
    }
}
