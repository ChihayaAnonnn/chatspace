package top.iceclean.chatspace.cache.common;

import com.google.common.collect.Sets;
import org.redisson.Redisson;
import org.redisson.api.BatchResult;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户各种状态的缓存，给外部用
 * @author : Ice'Clean
 * @date : 2023-04-03
 */
@Component
public class UserState {
    /** 用户位置查询 field 静态集 */
    public static final Set<Object> SITE_FIELDS = Sets.newHashSet(
            RedisKey.SPACE_SESSION, RedisKey.X, RedisKey.Y, RedisKey.VISION, RedisKey.EXIST);
    public static final Set<Object> VISION_FIELDS = Sets.newHashSet(
            RedisKey.USER_ID, RedisKey.VISION);
    public static final Set<Object> EXIST_FIELDS = Sets.newHashSet(
            RedisKey.USER_ID, RedisKey.EXIST);

    /** 缓存操作 */
    private final Redisson redisson;

    public UserState(Redisson redisson) {
        this.redisson = redisson;
    }

    private RMap<Object, Object> userMap(int userId) {
        return redisson.getMap(RedisKey.USER_LOGIN_HASH + userId);
    }

    /** 设置指定用户该状态的当前值 */
    public void set(int userId, String stateKey, Object value) {
        if (value != null) {
            userMap(userId).put(stateKey, value);
        }
    }

    /** 获取指定用户指定状态的整型值 */
    public Integer get(int userId, String stateKey) {
        Object value = userMap(userId).get(stateKey);
        return objectToInteger(value);
    }

    /** 获取指定用户指定状态的字符串值 */
    public String getString(int userId, String stateKey) {
        Object value = userMap(userId).get(stateKey);
        return value == null ? null : value.toString();
    }

    /** 获取指定用户多个状态的整型值 */
    public Integer[] multiGet(int userId, String ... stateKeys) {
        // 批量获取
        Map<Object, Object> resMap = userMap(userId)
                .getAll(Arrays.stream(stateKeys).collect(Collectors.toSet()));
        // 将值转化为数组
        Integer[] res = new Integer[stateKeys.length];
        for (int i=0; i<res.length; i++) {
            Object value = resMap.get(stateKeys[i]);
            res[i] = objectToInteger(value);
        }
        return res;
    }

    public void multiSet(int userId, Map<?, ?> map) {
        userMap(userId).putAll(map);
    }

    /** 清除指定用户的该状态 */
    public void clean(int userId, String stateKey) {
        userMap(userId).remove(stateKey);
    }

    /** 清楚所有状态 */
    public void cleanAll(int userId) {
        redisson.getKeys().delete(RedisKey.USER_LOGIN_HASH + userId);
    }

    /** 设置过期时间 */
    public void expire(int userId, long timeout, TimeUnit unit) {
        redisson.getKeys().expire(RedisKey.USER_LOGIN_HASH + userId, timeout, unit);
    }

    /**
     * 更新用户位置信息
     * @param site 用户位置信息
     */
    public void setSite(SiteVO site) {
        userMap(site.getUserId()).putAll(buildSiteUpdateMap(site));
    }
    public void setSite(SiteVO site, RBatch batch) {
        batch.getMap(RedisKey.USER_LOGIN_HASH + site.getUserId())
                .putAllAsync(buildSiteUpdateMap(site));
    }
    private Map<String, Object> buildSiteUpdateMap(SiteVO site) {
        // 构造数据集
        Map<String, Object> map = new HashMap<>(4);
        map.put(RedisKey.X, site.getX());
        map.put(RedisKey.Y, site.getY());
        map.put(RedisKey.VISION, site.getVision());
        map.put(RedisKey.EXIST, site.getExist());
        return map;
    }

    /**
     * 获取用户位置信息
     * @param userId 用户 ID
     * @return 用户位置信息
     */
    public SiteVO getSite(int userId) {
        return parseDataMap(userMap(userId).getAll(SITE_FIELDS));
    }

    /**
     * 批量获取用户位置信息
     * @param userIds 用户 ID 集合
     * @return 用户位置信息集合
     */
    public Set<SiteVO> batchGetSite(Collection<?> userIds) {
        RBatch batch = redisson.createBatch();
        // 构造数据集
        userIds.forEach(userId -> batch
                .getMap(RedisKey.USER_LOGIN_HASH + userId)
                .getAllAsync(SITE_FIELDS));
        // 批量执行并解析为位置信息集合
        return batch.execute().getResponses().stream()
                .map(this::parseDataMap)
                .filter(siteVO -> siteVO.getUserId() == null)
                .collect(Collectors.toSet());
    }

    /**
     * 批量获取用户视野或存在感
     * @param userIds 用户 ID 集合
     * @return 用户 ID 和存在感映射
     */
    @SuppressWarnings("unchecked")
    public Map<Integer, Integer> batchGetVisionOrExist(Collection<?> userIds, boolean vision) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        RBatch batch = redisson.createBatch();
        userIds.forEach(userId -> batch.getMap(RedisKey.USER_LOGIN_HASH + userId)
                .getAllAsync(vision ? VISION_FIELDS : EXIST_FIELDS));
        List<?> responses = batch.execute().getResponses();
        // 构造结果映射
        Map<Integer, Integer> dataMap = new HashMap<>(responses.size());
        String key = vision ? RedisKey.VISION : RedisKey.EXIST;
        responses.forEach(resp -> {
            Map<Object, Object> values = (Map<Object, Object>) resp;
            dataMap.put(objectToInteger(values.get(RedisKey.USER_ID)),
                    objectToInteger(values.get(key)));
        });
        return dataMap;
    }

    /**
     * 解析 Redis hash 批量获取的数据为实体
     * @param dataMapResp 数据映射响应
     * @return 用户位置信息
     */
    @SuppressWarnings("unchecked")
    private <D> SiteVO parseDataMap(D dataMapResp) {
        Map<Object, Object> dataMap = (Map<Object, Object>) dataMapResp;
        SiteVO siteVO = new SiteVO();
        siteVO.setSpaceId(objectToInteger(dataMap.get(RedisKey.SPACE_SESSION)));
        siteVO.setX(objectToInteger(dataMap.get(RedisKey.X)));
        siteVO.setY(objectToInteger(dataMap.get(RedisKey.Y)));
        siteVO.setVision(objectToInteger(dataMap.get(RedisKey.VISION)));
        siteVO.setExist(objectToInteger(dataMap.get(RedisKey.EXIST)));
        return siteVO;
    }

    /**
     * 将 Object 转成 Integer，如果 Object 为 null 则返回 null
     * @param target 目标转化对象
     * @return 转化结果
     */
    private Integer objectToInteger(Object target) {
        return target == null ? null : Integer.parseInt(target.toString());
    }
}
