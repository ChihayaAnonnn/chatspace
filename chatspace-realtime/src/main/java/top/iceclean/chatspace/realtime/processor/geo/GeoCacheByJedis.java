package top.iceclean.chatspace.realtime.processor.geo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.utils.GeoUtils;
import top.iceclean.chatspace.infrastructure.utils.RedisTemplateUtils;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.realtime.common.SessionRepository;

import java.util.Set;

/**
 * 用 Jedis 实现的 Redis 单机 GRO 地理坐标缓存
 * @author : Ice'Clean
 * @date : 2022-11-24
 * @deprecated 应该使用 GeoCache（使用 Redisson 实现）
 */
@Component
@Deprecated
public class GeoCacheByJedis {
    /** 使用 Redis 的 GEO 进行处理 */
    private final RedisTemplateUtils redisTemplateUtils;
    /** 地图比例 */
    int DEFAULT_RATE = 1;
    /** 误差允许 */
    double ERROR_RATE = 1e-2;
    /** 默认单位 */
    private static final Metrics DEFAULT_METRICS = Metrics.KILOMETERS;

    @Autowired
    public GeoCacheByJedis(RedisTemplateUtils redisTemplateUtils) {
        this.redisTemplateUtils = redisTemplateUtils;
    }

    public <T> void geoAdd(String key, T member, double lon, double lat) {
        redisTemplateUtils.geoAdd(key, member, lon, lat);
    }

    public <T> double[] geoGet(String key, T member) {
        Point point = redisTemplateUtils.geoGet(key, member);
        return new double[]{point.getX(), point.getY()};
    }

    public Set<Integer> getVisionCover(int spaceId, int zoneId, int userId, double vision) {
        // 查询指定空间，指定用户，指定范围内的所有用户 ID
        return redisTemplateUtils.geoRadius(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), userId, DEFAULT_RATE*vision + ERROR_RATE, DEFAULT_METRICS);
    }

    public Set<Integer> getExistCover(int spaceId, int zoneId, int userId, double exist) {
        // 查询指定空间，指定用户，指定范围内的所有用户 ID
        return redisTemplateUtils.geoRadius(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), userId, DEFAULT_RATE*exist + ERROR_RATE, DEFAULT_METRICS);
    }

    public boolean canCover(int spaceId, int zoneId, int user1, double range, int user2) {
        // 获取两个用户之间的距离
        double dist = redisTemplateUtils.geoDist(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), user1, user2, DEFAULT_METRICS);
        // 获取方位大小
        range *= DEFAULT_RATE;
        return range - dist > -ERROR_RATE;
    }

    /**
     * 将当前用户放入 GEO 缓存
     * @param siteVO 用户的坐标信息
     */
    public void putUser(SiteVO siteVO, int zoneId) {
        // 先进行坐标的转换
        double[] geoSite = GeoUtils.compute(siteVO.getX(), siteVO.getY(), DEFAULT_RATE);
        geoAdd(RedisKey.USER_SITE_HASH.apply(siteVO.getSpaceId(), zoneId), siteVO.getUserId(), geoSite[0], geoSite[1]);
    }
    public void putUser(SiteDTO siteDTO, int zoneId) {
        // 先进行坐标的转换
        double[] geoSite = GeoUtils.compute(siteDTO.getX(), siteDTO.getY(), DEFAULT_RATE);
        geoAdd(RedisKey.USER_SITE_HASH.apply(siteDTO.getSpaceId(), zoneId), SessionRepository.getUser().getUserId(), geoSite[0], geoSite[1]);
    }

    /**
     * 将用户从 CEO 缓存中移除
     * @param siteVO 用户坐标信息
     */
    public void removeUser(SiteVO siteVO, int zoneId) {
        redisTemplateUtils.geoRemove(RedisKey.USER_SITE_HASH.apply(siteVO.getSpaceId(), zoneId), siteVO.getUserId());
    }

    /**
     * 获取用户的虚拟坐标
     * @param spaceId 所处空间 ID
     * @param userId 用户 ID
     * @return 虚拟坐标
     */
    public int[] getUserSite(int spaceId, int zoneId, int userId) {
        // 从缓存中获取实时的物理坐标
        double[] pos = geoGet(RedisKey.USER_SITE_HASH.apply(spaceId, zoneId), userId);
        // 将物理坐标转化为虚拟坐标
        return GeoUtils.recompute(pos[0],pos[1], DEFAULT_RATE);
    }
}