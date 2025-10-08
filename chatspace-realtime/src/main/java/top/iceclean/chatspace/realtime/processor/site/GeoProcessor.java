package top.iceclean.chatspace.realtime.processor.site;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.UserCache;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通过 Geo 缓存进行用户位置的计算
 * @author : Ice'Clean
 * @date : 2023-01-08
 */
@Slf4j
@Component
public class GeoProcessor {

    /** 地理位置缓存 */
    private final GeoCache geoCache;
    /** 用户缓存 */
    private final UserCache userCache;

    public GeoProcessor(GeoCache geoCache, UserCache userCache) {
        this.geoCache = geoCache;
        this.userCache = userCache;
    }

    /**
     * 获取用户能看见的其他用户的 ID 集合
     * @param spaceId 空间 ID
     * @param zoneId 区域 ID
     * @param userId 用户 ID
     * @param vision 用户视野
     * @return 用户能见的用户 ID 集合
     */
    public Set<Integer> getVisionSet(int spaceId, int zoneId, int userId, int vision) {
        // 获取用户视野内的用户 ID 以及对应的距离
        Map<Object, Double> visionMap = geoCache.getVisionCover(spaceId, zoneId, userId, vision);

        // 移除用户本身以及存在感不能覆盖到用户的
        Set<Integer> visionCover = new HashSet<>(visionMap.size());
        visionMap.forEach((key, value) -> {
            int uId = Integer.parseInt(key.toString());
            Integer exist = userCache.getExist(uId);
            if (exist == null) {
                log.warn("用户 {} 存在感为 null", uId);
            } else if (geoCache.canCover(value, exist)) {
                visionCover.add(uId);
            }
        });
        visionCover.remove(userId);
        return visionCover;
    }

    /**
     * 获取能看到当前用户的其他用户的 ID 集合
     * 获取用户存在感内能看到该用户的其他用户的 ID 集合
     * @param spaceId 空间 ID
     * @param zoneId 区域 ID
     * @param userId 用户 ID
     * @param exist 用户存在感
     * @return 能看到该用户的其他用户 ID 集合
     */
    public Set<Integer> getExistSet(int spaceId, int zoneId, int userId, int exist) {
        // 获取用户存在感内的用户 ID 以及对应的距离
        Map<Object, Double> existMap = geoCache.getExistCover(spaceId, zoneId, userId, exist);

        // 移除用户本身以及存在感不能覆盖到用户的
        Set<Integer> existCover = new HashSet<>(existMap.size());
        existMap.forEach((key, value) -> {
            int uId = Integer.parseInt(key.toString());
            Integer vision = userCache.getVision(uId);
            if (vision == null) {
                log.warn("用户 {} 视野为 null", uId);
            } else if (geoCache.canCover(value, vision)) {
                existCover.add(uId);
            }
        });
        existCover.remove(userId);
        return existCover;
    }
}
