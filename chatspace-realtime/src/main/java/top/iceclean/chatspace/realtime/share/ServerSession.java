package top.iceclean.chatspace.realtime.share;

import io.netty.channel.Channel;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.vo.UserVO;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
    /** 用户的视野集合（用户能看见的其他用户的 ID） */
    private final Set<Integer> visionSet;
    /** 用户的存在感集合（能看见该用户的其他用户的 ID） */
    private final Set<Integer> existSet;

    public ServerSession(Channel channel, User user, Site site) {
        this.channel = channel;
        this.user = new UserVO(user, true);
        this.site = new SiteVO(site, this.user);
        this.visionSet = new HashSet<>();
        this.existSet = new HashSet<>();
    }

    public UserVO getUser() {
        return user;
    }

    public Channel getChannel() {
        return channel;
    }

    public SiteVO getSite() {
        return site;
    }

    public Set<Integer> getVisionSet() {
        return visionSet;
    }

    public Set<Integer> getExistSet() {
        return existSet;
    }

    @Override
    public String toString() {
        return "ServerSession{" +
                "channel=" + channel +
                ", user=" + user +
                ", site=" + site +
                ", visionSet=" + visionSet +
                ", existSet=" + existSet +
                '}';
    }
}
