package top.iceclean.chatspace.cache;

import com.alibaba.fastjson.JSON;
import org.redisson.cache.LRUCacheMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.infrastructure.vo.UserVO;
import top.iceclean.feign.FriendClient;
import top.iceclean.feign.UserClient;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户相关缓存
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Component
public class UserCache {

    /** 用户各种整型状态的缓存，给外部用 */
    public static class State {
        /** 缓存操作 */
        private final RedissonUtils redissonUtils;

        public State(RedissonUtils redissonUtils) {
            this.redissonUtils = redissonUtils;
        }

        /** 设置指定用户该状态的当前值 */
        public void set(int userId, String stateKey, Object value) {
            if (value != null) {
                redissonUtils.hashSet(RedisKey.USER_LOGIN_HASH + userId, stateKey, value);
            }
        }

        /** 获取指定用户指定状态的整型值 */
        public Integer get(int userId, String stateKey) {
            Object value = redissonUtils.hashGet(RedisKey.USER_LOGIN_HASH + userId, stateKey);
            return value == null ? null : Integer.parseInt(value.toString());
        }

        /** 获取指定用户指定状态的字符串值 */
        public String getString(int userId, String stateKey) {
            Object value = redissonUtils.hashGet(RedisKey.USER_LOGIN_HASH + userId, stateKey);
            return value == null ? null : value.toString();
        }

        /** 获取指定用户多个状态的整型值 */
        public Integer[] multiGet(int userId, String ... stateKeys) {
            Integer[] res = new Integer[stateKeys.length];
            Map<Object, Object> resMap = redissonUtils.hashMultiGet(RedisKey.USER_LOGIN_HASH + userId, stateKeys);
            for (int i=0; i<res.length; i++) {
                Object value = resMap.get(stateKeys[i]);
                res[i] = value == null ? null : Integer.parseInt(value.toString());
            }
            return res;
        }

        public void multiSet(int userId, Map<?, ?> map) {
            redissonUtils.hashMultiSet(RedisKey.USER_LOGIN_HASH + userId, map);
        }

        /** 清除指定用户的该状态 */
        public void clean(int userId, String stateKey) {
            redissonUtils.hashDel(RedisKey.USER_LOGIN_HASH + userId, stateKey);
        }
    }

    private final RedissonUtils redissonUtils;
    private FriendClient friendClient;
    private UserClient userClient;
    /** 用户状态缓存操作接口 */
    private final State userState;

    /** 用户视野和存在感的二级缓存 */
    private final LRUCacheMap<Integer, Integer> userVisionCache;
    private final LRUCacheMap<Integer, Integer> userExistCache;

    public UserCache(RedissonUtils redissonUtils) {
        this.redissonUtils = redissonUtils;
        userState = new UserCache.State(redissonUtils);
        userVisionCache = new LRUCacheMap<>(200, 30 * 1000, 20 * 1000);
        userExistCache = new LRUCacheMap<>(200, 30 * 1000, 20 * 1000);
    }

    @Autowired
    public void setFriendClient(FriendClient friendClient) {
        this.friendClient = friendClient;
    }

    @Autowired
    public void setUserClient(UserClient userClient) {
        this.userClient = userClient;
    }

    /** 用户登录缓存 */
    public void cachedLogin(User user, String token, String authorityString) {
        String loginKey = RedisKey.USER_LOGIN_HASH + user.getId();
        // 首先删除之前可能遗留的缓存
        redissonUtils.delete(loginKey);
        // 设置缓存状态
        redissonUtils.hashSet(loginKey, RedisKey.TOKEN, token);
        redissonUtils.hashSet(loginKey, RedisKey.AUTHORITIES, authorityString);
        userState.set(user.getId(), RedisKey.USER_BOX, user.getBoxId());
        redissonUtils.expire(loginKey, 3, TimeUnit.HOURS);
    }

    /** 批量获取用户信息缓存 */
    public Integer[] getUserStates(int userId, String ... stateKeys) {
        return userState.multiGet(userId, stateKeys);
    }

    /**
     * 同时设置视野和存在感（两次缓存操作降为一次，可以减少一半的时间）
     * @param userId 用户 ID
     * @param vision 用户视野大小
     * @param exist 用户存在感大小
     */
    public void setVisionAndExist(int userId, int vision, int exist) {
        Map<String, Integer> map = new HashMap<>(2);
        map.put(RedisKey.VISION, vision);
        map.put(RedisKey.EXIST, exist);
        userState.multiSet(userId, map);
    }
    /** 获取用户存在感 */
    public Integer getExist(int userId) {
        // 先从二级缓存中获取。否则从 Redis 中获取，并存入二级缓存
        return userExistCache.computeIfAbsent(userId, f -> userState.get(userId, RedisKey.EXIST));
    }

    /** 获取用户视野 */
    public Integer getVision(int userId) {
        // 先从二级缓存中获取。否则从 Redis 中获取，并存入二级缓存
        return userVisionCache.computeIfAbsent(userId, f -> userState.get(userId, RedisKey.VISION));
    }

    /** 获取用户登录时的 token */
    public String getToken(int userId) {
        Object tokenCache = redissonUtils.hashGet(RedisKey.USER_LOGIN_HASH + userId, RedisKey.TOKEN);
        return tokenCache == null ? null : tokenCache.toString();
    }

    /** 获取用户登陆时的权限 */
    public String getAuthorityString(String userId) {
        Object authorityCache = redissonUtils.hashGet(RedisKey.USER_LOGIN_HASH + userId, RedisKey.AUTHORITIES);
        return authorityCache == null ? null : authorityCache.toString();
    }

    /** 获取用户信息 */
    public User getUserById(int userId) {
        // 先获取用户的认证信息
        String authorityCache = getAuthorityString("" + userId);
        if (authorityCache == null) {
            // 等于空的话，则访问用户服务获取
            return userClient.getUserById(userId);
        }
        // 已存在缓存中，则解析出用户
        Object user = JSON.parseObject(authorityCache).get("user");
        return JSON.parseObject(user.toString(), User.class);
    }

    /** 获取用户随身空间箱的 ID */
    public Integer getUserBoxId(int userId) {
        return userState.get(userId, RedisKey.USER_BOX);
    }

    /** 获取验证码缓存 */
    public String getCaptcha(String userName) {
        Object code = redissonUtils.get(RedisKey.USER_CODE + userName);
        if (code != null) {
            return code.toString();
        }
        return null;
    }

    /**
     * 设置用户验证码
     * @param userName 用户注册的用户名名
     * @param content 验证码内容（邮箱+六位验证码）绑定
     * @param expire 过期时间（单位：分钟）
     */
    public void setCaptcha(String userName, String content, int expire) {
        redissonUtils.set(RedisKey.USER_CODE + userName, content, expire, TimeUnit.MINUTES);
    }

    /** 设置用户上下线标记 */
    public void setOnline(int userId, boolean online) {
        redissonUtils.setBit(RedisKey.USER_ONLINE_BIT, userId, online);
    }

    /** 判断用户是否在线 */
    public boolean isOnline(int userId) {
        return redissonUtils.getBit(RedisKey.USER_ONLINE_BIT, userId);
    }

    /** 获取系统在先总人数 */
    public long onlineUserNum() {
        return redissonUtils.countBit(RedisKey.USER_ONLINE_BIT);
    }

    /** 获取所有在线的用户 */
    public Set<Integer> getAllUsers() {
        return redissonUtils.getBitValueSet(RedisKey.USER_ONLINE_BIT);
    }

    /**
     * 将用户包装为用户响应对象，添加了用户的在线状态
     * @param user 用户实体
     * @return 添加了用户在线状态的响应对象
     */
    public UserVO wrapUser(User user) {
        return new UserVO(user, isOnline(user.getId()));
    }

    /** 获取两个用户的会话 ID */
    public Integer getFriendSession(int userId1, int userId2) {
        Integer friendId = friendClient.getFriendId(userId1, userId2);
        // 将 sessionId 所对应的两个用户的映射缓存起来，加速后面的发送消息
        redissonUtils.hashSet(RedisKey.FRIEND_USER_HASH, friendId, userId1 + "-" + userId2);
        // 同时在两个用户的缓存中，设置该好友会话，便于后期的查询
        userState.set(userId1, RedisKey.FRIEND_SESSION, friendId);
        userState.set(userId2, RedisKey.FRIEND_SESSION, friendId);
        return friendId;
    }

    /** 获取用户当前所在的好友会话 */
    public Integer getUserFriendSession(int userId) {
        return userState.get(userId, RedisKey.FRIEND_SESSION);
    }

    /** 移除好友会话缓存 */
    public void removeFriendSession(int friendId) {
        // 获取好友会话中的用户，并清除他们缓存中的好友会话
        getFriendSessionUsers(friendId).forEach(uId -> userState.clean(uId, RedisKey.FRIEND_SESSION));
        // 将好友会话移除
        redissonUtils.hashDel(RedisKey.FRIEND_USER_HASH, friendId);
    }

    /** 获取好友会话的用户 */
    public Set<Integer> getFriendSessionUsers(int friendId) {
        Object usersCache = redissonUtils.hashGet(RedisKey.FRIEND_USER_HASH, friendId);
        if (usersCache == null) {
            // 为空的话，重新查询出来
            HashSet<Integer> friendIds = new HashSet<>(friendClient.getFriendUserIdList(friendId));
            // 将其缓存起来
            redissonUtils.hashSet(RedisKey.FRIEND_USER_HASH, friendId,
                    friendIds.stream().map(Object::toString).collect(Collectors.joining("-")));
            return friendIds;
        }
        // 否则直接返回
        return Arrays.stream(usersCache.toString().split("-"))
                .map(Integer::parseInt).collect(Collectors.toSet());
    }

    /** 获取用户所在节点 */
    public String getServerNode(int userId) {
        return userState.getString(userId, RedisKey.SERVER_NODE);
    }

    public void setServerNode(int userId, String serverId) {
        userState.set(userId, RedisKey.SERVER_NODE, serverId);
    }
}
