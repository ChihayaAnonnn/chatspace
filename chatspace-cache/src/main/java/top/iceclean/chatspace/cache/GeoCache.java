package top.iceclean.chatspace.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.GeoUnit;
import org.redisson.api.RBatch;
import org.redisson.api.RGeoAsync;
import org.redisson.api.geo.GeoSearchArgs;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.common.UserState;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.utils.GeoUtils;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

import java.util.*;

/**
 * GEO 相关缓存
 * @author : Ice'Clean
 * @date : 2022-11-29
 */
@Slf4j
@Component
public class GeoCache {

    private final RedissonUtils redissonUtils;
    private final Redisson redisson;
    private final UserState userState;

    /** 地图比例 */
    private static final int DEFAULT_RATE = 1;
    /** 误差允许 */
    private static final double ERROR_RATE = 1e-2;
    /** 默认单位 */
    private static final GeoUnit DEFAULT_UNIT = GeoUnit.KILOMETERS;

    public GeoCache(RedissonUtils redissonUtils, Redisson redisson, UserState userState) {
        this.redissonUtils = redissonUtils;
        this.redisson = redisson;
        this.userState = userState;
    }

    /**
     * 更新用户位置信息并放入 GEO
     * @param siteVO 用户的坐标信息
     * @param zoneId 所在的区域 ID
     */
    public void update(SiteVO siteVO, int zoneId) {
        // 将状态更新到用户缓存
        userState.setSite(siteVO);
        // 将坐标转化然后更新到 Geo
        double[] geoSite = GeoUtils.compute(siteVO.getX(), siteVO.getY(), DEFAULT_RATE);
        redisson.getGeo(RedisKey.USER_SITE_HASH.apply(siteVO.getSpaceId(), zoneId))
                .add(geoSite[0], geoSite[1], siteVO.getUserId());
    }

    /**
     * 将用户从 GEO 缓存中移除
     * @param siteVO 用户坐标信息
     */
    public void remove(SiteVO siteVO, int zoneId) {
        remove(siteVO.getSpaceId(), zoneId, siteVO.getUserId());
    }
    public void remove(int spaceId, int zoneId, int userId) {
        redisson.getGeo(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId))
                .remove(userId);
    }

    /**
     * 获取用户的虚拟坐标
     * @param userId 用户 ID
     * @return 虚拟位置
     */
    public Site getUserSite(int userId) {
        return new Site(userState.getSite(userId));
    }

    public Set<SiteVO> batchGetSite(Collection<?> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptySet();
        }
        return userState.batchGetSite(userIds);
    }

    /**
     * 获取指定用户视野内的其他用户
     * @return 该用户视野内所有的用户 ID
     */
    public Map<String, Double> getVisionCover(int spaceId, int zoneId, int userId, double vision) {
        // 查询指定空间，指定用户，指定范围内的所有用户 ID
        return redissonUtils.geoRadius(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), userId, DEFAULT_RATE * vision + ERROR_RATE, DEFAULT_UNIT);
    }

    /**
     * 获取指定用户存在感内的其他用户
     * @return 该用户存在感内所有的用户 ID
     */
    public Map<String, Double> getExistCover(int spaceId, int zoneId, int userId, double exist) {
        // 查询指定空间，指定用户，指定范围内的所有用户 ID
        return redissonUtils.geoRadius(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), userId, DEFAULT_RATE * exist + ERROR_RATE, DEFAULT_UNIT);
    }

    /**
     * 通过覆盖集获取覆盖结果集合
     * @param coverTaskList 覆盖集，保存了每个覆盖获取任务的所有参数
     * @return 覆盖结果集合，每个元素都是（用户 ID -> 与该用户的距离）
     */
    public List<?> batchGetCover(List<GeoCache.CoverTask> coverTaskList) {
        RBatch batch = redisson.createBatch();
        coverTaskList.forEach(coverTask -> {
            RGeoAsync<Object> geo = batch.getGeo(RedisKey.USER_SITE_HASH.apply(coverTask.site.getSpaceId(), coverTask.zoneId));
            switch (coverTask.type) {
                case VISION:
                    geo.searchWithDistanceAsync(GeoSearchArgs.from(coverTask.site.getUserId())
                            .radius(DEFAULT_RATE * coverTask.site.getVision() + ERROR_RATE, DEFAULT_UNIT));
                    break;
                case EXIST:
                    geo.searchWithDistanceAsync(GeoSearchArgs.from(coverTask.site.getUserId())
                            .radius(DEFAULT_RATE * coverTask.site.getExist() + ERROR_RATE, DEFAULT_UNIT));
                    break;
                case REMOVE:
                    geo.removeAsync(coverTask.site.getUserId());
                    break;
                case UPDATE:
                    double[] geoSite = GeoUtils.compute(coverTask.site.getX(), coverTask.site.getY(), DEFAULT_RATE);
                    geo.addAsync(geoSite[0], geoSite[1], coverTask.site.getUserId());
                    break;
                case SITE:
                    userState.setSite(coverTask.site, batch);
                    break;
                default:
            }
        });
        return batch.execute().getResponses();
    }

    /**
     * 获取两个用户之间的距离
     * @param userId1
     * @param userId2
     * @return
     */
    public double getDist(int spaceId, int zoneId, int userId1, int userId2) {
        return redissonUtils.geoDist(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), userId1, userId2, DEFAULT_UNIT);
    }

    /**
     * 判断 user1 在指定范围内是否覆盖到 user2
     * @param spaceId 两个用户所处的空间 ID
     * @param user1 用户 1
     * @param range 以用户 1 为中心的范围
     * @param user2 用户 2
     * @return 能覆盖到则返回 true
     */
    public boolean canCover(int spaceId, int zoneId, int user1, double range, int user2) {
        // 获取两个用户之间的距离
        Double dist = redissonUtils.geoDist(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), user1, user2, DEFAULT_UNIT);
        if (dist == null) {
            return false;
        }
        // 获取方位大小
        range *= DEFAULT_RATE;
        return range - dist > -ERROR_RATE;
    }

    public boolean canCover(double dist, double range) {
        range *= DEFAULT_RATE;
        return range - dist > -ERROR_RATE;
    }

    /** 批量获取覆盖集任务 */
    @AllArgsConstructor
    public static class CoverTask {
        /** 任务类型 */
        public enum Type {
            /** GEO 计算、移除和更新类型 */
            VISION, EXIST, REMOVE, UPDATE, SITE;
        }
        /** 覆盖任务的额外标识（如更新位置） */
        private final Type type;
        /** 用于获取覆盖集或者更新位置信息 */
        private final SiteVO site;
        /** 所处区域 */
        private final Integer zoneId;
        /** 是否忽略该任务的结果 */
        private final boolean ignore;

        public SiteVO getSite() {
            return site;
        }

        public Integer getZoneId() {
            return zoneId;
        }

        public Type getType() {
            return type;
        }

        public boolean isIgnore() {
            return ignore;
        }
    }
}
