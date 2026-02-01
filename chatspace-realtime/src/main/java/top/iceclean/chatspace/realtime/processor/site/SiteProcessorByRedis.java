package top.iceclean.chatspace.realtime.processor.site;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.SpaceCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.common.Monitor;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;

import java.util.*;
import java.util.concurrent.*;

/**
 * 用户坐标更新处理器
 * @author : Ice'Clean
 * @date : 2022-11-24
 */
@Slf4j
@Service
public class SiteProcessorByRedis implements SiteProcessor {
    /** 空间消息处理器 */
    private final SpaceProcessor spaceProcessor;
    /** 地理位置处理器 */
    private final GeoProcessor geoProcessor;
    /** 区域处理器 */
    private final ZoneProcessor zoneProcessor;

    /** 地理位置缓存 */
    private final GeoCache geoCache;
    /** 空间缓存 */
    private final SpaceCache spaceCache;
    /** 用户缓存 */
    private final UserCache userCache;

    /** 用户移动事件处理线程池 */
    private final ThreadPoolExecutor userMoveEventPool;
    /** 哈希表，存储用户已处理的最新时间戳，旧事件将被丢弃 */
    private final ConcurrentMap<Integer, Long> lastHandledMap;

    public SiteProcessorByRedis(SpaceProcessor spaceProcessor, GeoProcessor geoProcessor, ZoneProcessor zoneProcessor,
                                GeoCache geoCache, SpaceCache spaceCache, UserCache userCache) {
        this.spaceProcessor = spaceProcessor;
        this.geoProcessor = geoProcessor;
        this.zoneProcessor = zoneProcessor;
        this.geoCache = geoCache;
        this.spaceCache = spaceCache;
        this.userCache = userCache;
        this.userMoveEventPool = new ThreadPoolExecutor(32, 64, 8, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(), r -> new Thread(r, "userMoveEventPool"));
        this.lastHandledMap = new ConcurrentHashMap<>();
    }

    @Override
    public void enterSpace() {
        // 获取已初始化的用户位置对象，并向该用户发送初始化消息，并放入缓存
        SiteVO siteVO = SessionRepository.getSite();
        spaceProcessor.sendSpaceInitMessage(siteVO);
        geoCache.update(siteVO, 0);
        log.info("用户进入空间：" + siteVO);

        // 将用户缓存到目标空间中
        int spaceId = siteVO.getSpaceId();
        int userId = siteVO.getUserId();
        spaceCache.spaceIn(spaceId, userId);
        userCache.setVisionAndExist(userId, siteVO.getVision(), siteVO.getExist());

        // 进入区域检查
        int[] zoneId = zoneProcessor.zoneCheck(userId, null, siteVO);

        // 获取用户视野内的其他用户，并向该用户发送发现其他用户的通知
        Set<Integer> visionSet = geoProcessor.getVisionSet(siteVO, zoneId[1]);
        spaceProcessor.changeSiteState(userId, WsType.SITE_CHANGE, visionSet);

        // 获取用户存在感内的其他用户，并向其他用户发送发现该用户的通知
        Set<Integer> existCover = geoProcessor.getExistSet(siteVO, zoneId[1]);
        spaceProcessor.changeSiteState(existCover, WsType.SITE_CHANGE, userId);
    }

    @Override
    public void exitSpace(SiteVO siteVO) {
        // 用户退出，先获取位置信息
        int spaceId = siteVO.getSpaceId();
        int userId = siteVO.getUserId();

        // 退出区域检查
        int[] zoneId = zoneProcessor.zoneCheck(userId, siteVO, null);

        // 获取能看到该用户的其他用户，并向他们发送消失通知
        Set<Integer> existSet = geoProcessor.getExistSet(siteVO, zoneId[1]);
        spaceProcessor.changeSiteState(existSet, WsType.SITE_REMOVE, userId);

        // 最后将用户移除（使用的是旧的区域，即退出时的区域）
        geoCache.remove(siteVO, zoneId[0]);
        spaceCache.spaceOut(spaceId, userId);
    }

    @Override
    public void updateSite(SiteDTO siteDTO) {
        // 直接放入线程池，异步处理
        int userId = SessionRepository.getUser().getUserId();
        userMoveEventPool.execute(new MoveEventTask(userId, siteDTO));
    }

    @Override
    public void updateSite(int[] zoneIds) {
        // 没有变化
        if (zoneIds.length == 0) {
            return;
        }
        // 新旧为同一个位置
        SiteVO siteVO = SessionRepository.getSite();
        updateSite(siteVO, siteVO, zoneIds);
    }

    /**
     * 位置变化统一处理
     * @param oldSiteVO 所在旧位置
     * @param newSiteVO 所在新位置
     * @param zoneIds 所在的新旧区域
     */
    private void updateSite(SiteVO oldSiteVO, SiteVO newSiteVO, int[] zoneIds) {
        // 先将用户放到新 geo 中（这时候是中间状态，新旧两个位置都有该用户的位置信息）
        long start = System.currentTimeMillis();
        // 优化为批量获取，获取用户原本能看到的，原本能看到该用户的，以及用户现在能看到的，现在能看到该用户的
        List<Set<Integer>> execute = geoProcessor.batchGetCover()
                .addVisionTask(oldSiteVO, zoneIds[0])
                .addExistTask(oldSiteVO, zoneIds[0])
                .addRemove(oldSiteVO, zoneIds[0])
                .addUpdate(oldSiteVO, zoneIds[1])
                .addVisionTask(newSiteVO, zoneIds[1])
                .addExistTask(newSiteVO, zoneIds[1])
                .execute();
        Set<Integer> oldVisionSet = execute.get(0);
        Set<Integer> oldExistSet = execute.get(1);
        Set<Integer> newVisionSet = execute.get(2);
        Set<Integer> newExistSet = execute.get(3);
        Monitor.addGeoTime(System.currentTimeMillis() - start);

        oldSiteVO.updateSite(newSiteVO);
        int userId = newSiteVO.getUserId();

        // 新旧视野集取交集，则是位置不变的情况
        Set<Integer> noChangeVisionSet = new HashSet<>(oldVisionSet);
        noChangeVisionSet.retainAll(newVisionSet);
        // 新老集合移除掉位置不变的，就是消失的或者新增的
        oldVisionSet.removeAll(noChangeVisionSet);
        newVisionSet.removeAll(noChangeVisionSet);

        // 新旧存在取交集，就是能看到该用户位置改变的其他用户
        Set<Integer> changeExistSet = new HashSet<>(oldExistSet);
        changeExistSet.retainAll(newExistSet);
        // 新老集合移除掉可以看到位置发生变化的，剩下的就是其他用户看到该用户消失或者出现
        oldExistSet.removeAll(changeExistSet);
        newExistSet.removeAll(changeExistSet);

        start = System.currentTimeMillis();
        // 为当前用户发送消息
        spaceProcessor.changeSiteState(userId, WsType.SITE_REMOVE, oldVisionSet);
        spaceProcessor.changeSiteState(userId, WsType.SITE_CHANGE, newVisionSet);

        // 为其他用户发送当前用户的消失、位置更新和出现消息
        spaceProcessor.changeSiteState(oldExistSet, WsType.SITE_REMOVE, userId);
        spaceProcessor.changeSiteState(changeExistSet, WsType.SITE_CHANGE, userId);
        spaceProcessor.changeSiteState(newExistSet, WsType.SITE_CHANGE, userId);
        Monitor.addSendMessageTime(System.currentTimeMillis() - start);
    }



    /** 用户移动事件处理任务 */
    private class MoveEventTask implements Runnable, Comparable<MoveEventTask> {
        /** 用户 ID 和位置实体 */
        private final int userId;
        private final SiteDTO siteDTO;
        /** 任务开始时的时间戳 */
        private final long timestamp;

        public MoveEventTask(int userId, SiteDTO siteDTO) {
            this.userId = userId;
            this.siteDTO = siteDTO;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public void run() {
            Monitor.change(Monitor.Type.USER_MOVE_EVENT, userMoveEventPool.getQueue().size());
            if (timestamp < lastHandledMap.getOrDefault(userId, 0L)) {
                log.info("【忽略用户旧移动事件】userId={}", userId);
                return;
            }
            lastHandledMap.put(userId, timestamp);
            // 处理与提交耗时
            long start = System.currentTimeMillis();
            updateSiteAsync(userId, siteDTO);
            Monitor.change(Monitor.Type.USER_MOVE_DELAY, System.currentTimeMillis() - start);
            Monitor.addUserMoveRespTime(System.currentTimeMillis() - timestamp);
            Monitor.finishUserEvent();
        }

        private void updateSiteAsync(int userId, SiteDTO siteDTO) {
            // 将当前线程与用户绑定
            SessionRepository.update(userId);
            // 用户坐标更新，先获取旧的位置信息
            SiteVO oldSiteVO = SessionRepository.getSite();
            if (oldSiteVO == null) {
                log.warn("用户已下线，忽略操作");
                return;
            }
            SiteVO newSiteVO = new SiteVO(siteDTO, oldSiteVO.getSpaceId(), userId);
            // 进出区域检查（获得所处的旧区域和新区域）
            long start = System.currentTimeMillis();
            int[] zoneIds = zoneProcessor.zoneCheck(userId, oldSiteVO, newSiteVO);
            Monitor.addZoneHandleTime(System.currentTimeMillis() - start);
            // 进行统一处理
            updateSite(oldSiteVO, newSiteVO, zoneIds);
        }

        @Override
        public int compareTo(MoveEventTask o) {
            // 按时间戳倒序
            return Long.compare(o.timestamp, this.timestamp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MoveEventTask that = (MoveEventTask) o;
            return userId == that.userId && timestamp == that.timestamp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, timestamp);
        }
    }
}
