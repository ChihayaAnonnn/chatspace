package top.iceclean.chatspace.cache;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.common.UserState;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.Space;
import top.iceclean.chatspace.infrastructure.utils.Pair;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.infrastructure.vo.SpaceVO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-12-03
 */
@Component
public class SpaceCache {

    private final RedissonUtils redissonUtils;
    /** 用户所在空间的状态缓存 */
    private final UserState userState;
    /** 位置缓存 */
    private final GeoCache geoCache;

    public SpaceCache(RedissonUtils redissonUtils, UserState userState, GeoCache geoCache) {
        this.redissonUtils = redissonUtils;
        this.userState = userState;
        this.geoCache = geoCache;
    }

    /** 用户进入空间 */
    public void spaceIn(int spaceId, int userId) {
        // 将用户 ID 缓存到指定区域，并标记缓存
        redissonUtils.setAdd(RedisKey.SPACE_USER_SET + spaceId, userId);
        userState.set(userId, RedisKey.SPACE_SESSION, spaceId);
    }

    /** 用户离开空间 */
    public void spaceOut(int spaceId, int userId) {
        // 将用户从区域中移除，并清除标记
        redissonUtils.setRemove(RedisKey.SPACE_USER_SET + spaceId, userId);
        userState.clean(userId, RedisKey.SPACE_SESSION);
    }

    /** 判断用户是否在指定空间中 */
    public boolean containsUser(int spaceId, int userId) {
        return redissonUtils.setContains(RedisKey.SPACE_USER_SET + spaceId, userId);
    }

    /** 获取用户所在的空间 */
    public Integer getUserSpaceId(int userId) {
        return userState.get(userId, RedisKey.SPACE_SESSION);
    }

    /** 获取用户当前的位置（哪个空间，哪个坐标） */
    public Site getUserSite(int userId) {
        return geoCache.getUserSite(userId);
    }

    /** 获取空间中的所有用户 */
    public Set<Integer> getSpaceUsers(int spaceId) {
        return redissonUtils.setGet(RedisKey.SPACE_USER_SET + spaceId)
                .stream().map(id -> Integer.parseInt(id.toString())).collect(Collectors.toSet());
    }

    /** 获取空间的用户数量 */
    public int onlineUserNum(int spaceId) {
        return redissonUtils.setSize(RedisKey.SPACE_USER_SET + spaceId);
    }

    /**
     * 将空间包装为空间响应对象，添加了空间的在线人数
     * @param space 空间实体
     * @return 添加了空间在线人数的响应对象
     */
    public SpaceVO wrapSpace(Space space) {
        return new SpaceVO(space, onlineUserNum(space.getId()));
    }

    private static final String NODE_SPACE = "access_space";
    private static final String NODE_MAX_USERS = "max_user_num";
    private static final String NODE_USERS = "user_num";
    private static final String SERVER_NODE_PATTERN = "chatspace:server:node:*";

    /**
     * 分析所有空间的情况
     * @return 空间 ID => 最大人数、负载人数
     */
    public Map<String, int[]> analysesSpaceLoad() {
        // 空间 -> 最大人数, 负载人数
        Map<String, int[]> loadMap = new HashMap<>();
        // 扫描所有的服务节点，统计出每个空间的最大人数和已负载的人数
        for (String serverKey : redissonUtils.keyScan(SERVER_NODE_PATTERN)) {
            Map<Object, Object> dataMap = redissonUtils.hashMultiGet(serverKey, NODE_SPACE, NODE_MAX_USERS, NODE_USERS);
            int maxNum = Integer.parseInt(dataMap.get(NODE_MAX_USERS).toString());
            int loadNum = Integer.parseInt(Optional.ofNullable(dataMap.get(NODE_USERS)).orElse("0").toString());
            if (dataMap.get(NODE_SPACE) != null) {
                List<String> accessSpace = JSON.parseArray(dataMap.get(NODE_SPACE).toString(), String.class);
                accessSpace.forEach(spaceId -> {
                    loadMap.putIfAbsent(spaceId, new int[]{0, 0});
                    int[] load = loadMap.get(spaceId);
                    load[0] += maxNum;
                    load[1] += loadNum;
                });
            }
        }
        return loadMap;
    }
}
