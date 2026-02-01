package top.iceclean.chatspace.infrastructure.constant;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 缓存中的键名
 * @author : Ice'Clean
 * @date : 2022-06-08
 */
public class RedisKey {
    /** 对内通用头 */
    private static final String HEAD    = "chatspace:";
    private static final String SPACE   = "space:";
    private static final String ZONE    = "zone:";
    private static final String GROUP   = "group:";
    private static final String FRIEND  = "friend:";
    private static final String USER    = "user:";
    private static final String TEMP    = "temp:";
    private static final String SITE    = "site:";
    private static final String LOCK    = "lock:";
    private static final String POINT   = "point:";

    /** 用户缓存通用键（token、认证、所在临时会话） */
    public static final String USER_ID = "id";
    public static final String TOKEN  = "token";
    public static final String AUTHORITIES = "authorities";
    public static final String SERVER_NODE = "server-node";
    public static final String TEMP_SESSION = "temp_id";
    public static final String FRIEND_SESSION = "friend_id";
    public static final String SPACE_SESSION = "space_id";
    public static final String EXIST = "exist";
    public static final String VISION = "vision";
    public static final String X = "x";
    public static final String Y = "y";

    /** 用户未 ACK 的区域进入 */
    public static final String TEMP_ZONE_IN = "temp_zone_in";
    public static final String USER_BOX = "user_box";
    /** 会话 ID 分发通用键 */
    public static final String TEMP_SESSION_ID = HEAD + TEMP + "id";

    /** 空间实体缓存 */
    public static final String SPACE_HASH   = HEAD + SPACE;
    /** 群组实体缓存 */
    public static final String GROUP_HASH   = HEAD + GROUP;
    /** 区域实体缓存 */
    public static final String ZONE_HASH    = HEAD + ZONE;

    /** 用户注册验证码缓存 */
    public static final String USER_CODE = HEAD + USER + "code:";
    /** 用户登录缓存 */
    public static final String USER_LOGIN_HASH = HEAD + USER + "login:";
    /** 用户所在嵌套区域缓存 */
    public static final String USER_ZONE_LIST = HEAD + USER + ZONE;
    /** 在线用户统计，位图偏移量为用户 ID，值表是否在线 */
    public static final String USER_ONLINE_BIT = HEAD + USER + "online";

    /** 空间用户坐标 */
    public static final BiFunction<Integer, Integer, String> USER_SITE_HASH =
            (spaceId, zoneId) -> HEAD + USER + SITE + spaceId + ":" + zoneId;
    /** 空间区域坐标 */
    public static final String ZONE_POINT_HASH = HEAD + ZONE + POINT;
    /** 空间区域覆盖 */
    public static final String ZONE_SITE_HASH = HEAD + ZONE + SITE;

    /** 空间中的用户缓存 */
    public static final String SPACE_USER_SET = HEAD + SPACE + USER;
    /** 群组中的用户缓存 */
    public static final String GROUP_USER_SET = HEAD + GROUP + USER;
    /** 区域中的用户缓存 */
    public static final String ZONE_USER_SET = HEAD + ZONE + USER;
    /** 临时会话中的用户缓存 */
    public static final String TEMP_USER_SET = HEAD + TEMP + USER;
    /** 好友会话中的用户缓存 */
    public static final String FRIEND_USER_HASH = HEAD + FRIEND + USER;

    /** 修改用户状态的分布式锁 */
    public static final String LOCK_USER_KEY = HEAD + LOCK + USER;
    /** 判断用户位置的分布式锁 */
    public static final String LOCK_SITE_KEY = HEAD + LOCK + SITE;

    private RedisKey() {

    }


}
