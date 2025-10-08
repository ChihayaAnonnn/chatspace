package top.iceclean.chatspace.gateway.loadbalance.entry;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

/**
 * 用户节点信息
 * @author : Ice'Clean
 * @date : 2023-01-25
 */
@Getter
@AllArgsConstructor
public class UserEntry {
    /** 用户 ID */
    private final Integer userId;
    /** 所处空间 ID */
    private final Integer spaceId;
    /** 所在节点 ID */
    private final String serverId;

    @Override
    public String toString() {
        return "UserEntry<" + userId + ", " + spaceId + "> serverId='" + serverId;
    }
}
