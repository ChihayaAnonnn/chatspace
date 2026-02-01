package top.iceclean.chatspace.realtime.processor.site;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.common.Monitor;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.service.SiteService;

import java.util.*;

/**
 * 通过 Geo 缓存进行用户位置的计算
 * @author : Ice'Clean
 * @date : 2023-01-08
 */
@Slf4j
@Component
public class GeoProcessor {

    /** 地理位置缓存 */
    protected final GeoCache geoCache;
    /** 用户缓存 */
    private final UserCache userCache;
    /** 区域缓存 */
    private final ZoneCache zoneCache;
    /** 用户位置服务 */
    private final SiteService siteService;

    public GeoProcessor(GeoCache geoCache, UserCache userCache, ZoneCache zoneCache, SiteService siteService) {
        this.geoCache = geoCache;
        this.userCache = userCache;
        this.zoneCache = zoneCache;
        this.siteService = siteService;
    }

    public CoverTaskBuilder batchGetCover() {
        return new CoverTaskBuilder(this);
    }
    @SuppressWarnings("unchecked")
    protected List<Set<Integer>> batchGetCover(List<GeoCache.CoverTask> coverTaskList) {
        // 批量执行
        List<?> resList = geoCache.batchGetCover(coverTaskList);
        List<Set<Integer>> res = new ArrayList<>(resList.size());
        // 遍历任务处理结果
        int len = coverTaskList.size();
        for (int i=0; i<len; i++) {
            GeoCache.CoverTask coverTask = coverTaskList.get(i);
            // 忽略任务结果
            if (coverTask.isIgnore()) {
                continue;
            }
            // 处理任务结果
            SiteVO site = coverTask.getSite();
            Map<String, Double> map = (Map<String, Double>) resList.get(i);
            switch (coverTask.getType()) {
                case VISION:
                    res.add(getVisionSet(map, site.getUserId(), site.getSpaceId(), coverTask.getZoneId()));
                    break;
                case EXIST:
                    res.add(getExistSet(map, site.getUserId(), site.getSpaceId(), coverTask.getZoneId()));
                    break;
                default:
            }
        }
        return res;
    }

    /**
     * 获取用户能看见的其他用户的 ID 集合
     * @param site 用户位置信息
     * @param zoneId 区域 ID
     * @return 用户能见的用户 ID 集合
     */
    public Set<Integer> getVisionSet(SiteVO site, int zoneId) {
        int userId = site.getUserId();
        int spaceId = site.getSpaceId();
        int vision = site.getVision();
        // 获取用户视野内的用户 ID 以及对应的距离
        Map<String, Double> visionMap = geoCache.getVisionCover(spaceId, zoneId, userId, vision);
        return getVisionSet(visionMap, userId, spaceId, zoneId);
    }
    private Set<Integer> getVisionSet(Map<String, Double> visionMap, int userId, int spaceId, int zoneId) {
        visionMap.remove("" + userId);
        // 批量获取视野集用户判断
        long start = System.currentTimeMillis();
        Map<Integer, Integer> existMap = userCache.batchGetExist(visionMap.keySet());
        Monitor.addBatchVisionTime(System.currentTimeMillis() - start);
        // 移除用户本身以及存在感不能覆盖到用户的
        Set<Integer> visionCover = new HashSet<>(existMap.size());
        visionMap.forEach((uIdString, dist) -> {
            int uId = Integer.parseInt(uIdString);
            Integer exist = existMap.get(uId);
            if (exist == null) {
                // 进行修正
                log.warn("用户 {} 存在感 null", uId);
                fixUserState(uId, spaceId, zoneId);
            } else if (geoCache.canCover(dist, exist)) {
                visionCover.add(uId);
            }
        });
        return visionCover;
    }

    /**
     * 获取能看到当前用户的其他用户的 ID 集合
     * 获取用户存在感内能看到该用户的其他用户的 ID 集合
     * @param site 用户位置信息
     * @param zoneId 区域 ID
     * @return 能看到该用户的其他用户 ID 集合
     */
    public Set<Integer> getExistSet(SiteVO site, int zoneId) {
        int userId = site.getUserId();
        int spaceId = site.getSpaceId();
        int exist = site.getExist();
        // 获取用户存在感内的用户 ID 以及对应的距离
        Map<String, Double> existMap = geoCache.getExistCover(spaceId, zoneId, userId, exist);
        return getExistSet(existMap, userId, spaceId, zoneId);
    }
    private Set<Integer> getExistSet(Map<String, Double> existMap, int userId, int spaceId, int zoneId) {
        existMap.remove("" + userId);
        // 批量获取视野集用户判断
        long start = System.currentTimeMillis();
        Map<Integer, Integer> visionMap = userCache.batchGetVision(existMap.keySet());
        Monitor.addBatchVisionTime(System.currentTimeMillis() - start);
        // 移除用户本身以及视野不能覆盖到用户的
        Set<Integer> existCover = new HashSet<>(existMap.size());
        existMap.forEach((uIdString, dist) -> {
            int uId = Integer.parseInt(uIdString);
            Integer vision = visionMap.get(uId);
            if (vision == null) {
                // 进行修正
                log.warn("用户 {} 视野为 null", uId);
                fixUserState(uId, spaceId, zoneId);
            } else if (geoCache.canCover(dist, vision)) {
                existCover.add(uId);
            }
        });
        return existCover;
    }

    /**
     * 补偿操作，修正用户状态
     * 当用户出现：视野或存在感为空，则进行补偿
     * @param userId 用户 ID
     */
    private void fixUserState(int userId, int spaceId, int zoneId) {
        // 先判断用户是否还存活
        if (userCache.getServerNode(userId) != null) {
            // 还存活则修复其存在感和视野
            Site site = siteService.getUserSite(userId);
            userCache.setVisionAndExist(userId, site.getVision(), site.getExist());
            log.info("修复用户视野和存在感：{}", site);
        } else {
            // 判断用户已经离线，则从 GEO 中将其移除
            geoCache.remove(spaceId, zoneId, userId);
            log.info("修复用户 {} GEO 信息，将其移出", userId);
        }
    }

    /**
     * 判断用户是否能看到另一个用户
     * @param fromUserId 当前用户
     * @param toUserId 目标用户
     * @return 能看到则返回 true
     */
    public boolean canSee(int fromUserId, int toUserId) {
        // 获取用户位置信息
        SiteVO site = SessionRepository.getSite(fromUserId);
        int spaceId = site.getSpaceId();
        int zoneId = zoneCache.getUserZoneId(fromUserId);
        int vision = site.getVision();
        // 获取两个用户之间的距离
        double dist = geoCache.getDist(spaceId, zoneId, fromUserId, toUserId);
        // 必须同时满足，from 用户的视野大于该距离，且 to 用户的存在感大于该距离
        return geoCache.canCover(dist, vision) &&
                geoCache.canCover(dist, SessionRepository.getSite(toUserId).getExist());
    }

    public static class CoverTaskBuilder {
        private final List<GeoCache.CoverTask> coverTaskList;
        private final GeoProcessor geoProcessor;

        public CoverTaskBuilder(GeoProcessor geoProcessor) {
            this.coverTaskList = new ArrayList<>();
            this.geoProcessor = geoProcessor;
        }

        public CoverTaskBuilder addVisionTask(SiteVO site, int zoneId) {
            coverTaskList.add(new GeoCache.CoverTask(GeoCache.CoverTask.Type.VISION, site, zoneId, false));
            return this;
        }

        public CoverTaskBuilder addExistTask(SiteVO site, int zoneId) {
            coverTaskList.add(new GeoCache.CoverTask(GeoCache.CoverTask.Type.EXIST, site, zoneId, false));
            return this;
        }

        public CoverTaskBuilder addRemove(SiteVO site, int zoneId) {
            coverTaskList.add(new GeoCache.CoverTask(GeoCache.CoverTask.Type.REMOVE, site, zoneId, true));
            return this;
        }

        public CoverTaskBuilder addUpdate(SiteVO site, int zoneId) {
            coverTaskList.add(new GeoCache.CoverTask(GeoCache.CoverTask.Type.SITE, site, zoneId, true));
            coverTaskList.add(new GeoCache.CoverTask(GeoCache.CoverTask.Type.UPDATE, site, zoneId, true));
            return this;
        }

        public List<Set<Integer>> execute() {
            return geoProcessor.batchGetCover(coverTaskList);
        }
    }
}
