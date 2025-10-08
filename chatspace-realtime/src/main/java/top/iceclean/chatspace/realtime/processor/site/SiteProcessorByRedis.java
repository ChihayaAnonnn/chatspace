package top.iceclean.chatspace.realtime.processor.site;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.SpaceCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;

import java.util.*;

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

    public SiteProcessorByRedis(SpaceProcessor spaceProcessor, GeoProcessor geoProcessor, ZoneProcessor zoneProcessor,
                                GeoCache geoCache, SpaceCache spaceCache, UserCache userCache) {
        this.spaceProcessor = spaceProcessor;
        this.geoProcessor = geoProcessor;
        this.zoneProcessor = zoneProcessor;
        this.geoCache = geoCache;
        this.spaceCache = spaceCache;
        this.userCache = userCache;
    }

    @Override
    public void enterSpace() {
        // 获取已初始化的用户位置对象，并向该用户发送初始化消息，并放入缓存
        SiteVO siteVO = SessionRepository.getSite();
        spaceProcessor.sendSpaceInitMessage(siteVO);
        geoCache.putUser(siteVO, 0);
        log.info("用户进入空间：" + siteVO);

        // 将用户缓存到目标空间中
        int spaceId = siteVO.getSpaceId();
        int userId = siteVO.getUser().getUserId();
        spaceCache.spaceIn(spaceId, userId);
        userCache.setVisionAndExist(userId, siteVO.getVision(), siteVO.getExist());

        // 进入区域检查
        int[] zoneId = zoneProcessor.zoneCheck(userId, null, siteVO);

        // 获取用户视野内的其他用户，并向该用户发送发现其他用户的通知
        Set<Integer> visionSet = geoProcessor.getVisionSet(spaceId, zoneId[1], userId, siteVO.getVision());
        spaceProcessor.changeSiteState(userId, WsType.VISION_UPDATE, visionSet);

//        // 获取用户存在感内的其他用户，并向其他用户发送发现该用户的通知
//        Set<Integer> existCover = geoProcessor.getExistSet(spaceId, userId, siteVO.getExist(), true);
//        spaceProcessor.changeSiteState(existCover, WsType.SITE_ADD, userId);


    }

    @Override
    public void updateSite(SiteDTO siteDTO) {
        long start = System.currentTimeMillis();
        // 用户坐标更新，先获取旧的位置信息
        SiteVO oldSiteVO = SessionRepository.getSite();
        int spaceId = oldSiteVO.getSpaceId();
        int userId = oldSiteVO.getUser().getUserId();
        siteDTO.setSpaceId(spaceId);

        // 进出区域检查（获得所处的旧区域和新区域）
        int[] zoneId = zoneProcessor.zoneCheck(userId, oldSiteVO, new SiteVO(siteDTO, null));
        if (userId == 1) {
            log.info("【进出区域检查】{} ms", System.currentTimeMillis() - start);
        }

        // 视野或存在感发生变化，则进行更新
        if (!Objects.equals(oldSiteVO.getVision(), siteDTO.getVision()) ||
                !Objects.equals(oldSiteVO.getExist(), siteDTO.getExist())) {
            userCache.setVisionAndExist(userId, siteDTO.getVision(), siteDTO.getExist());
        }
        if (userId == 1) {
            log.info("【更新存在感和视野】{} ms", System.currentTimeMillis() - start);
        }

        // 获取用户原本能看到的其他用户，以及原本能看到该用户的其他用户
        // ServerSession session = SessionRepository.get(userId);
        // 先获取分布式锁，取得前后的集合
        Set<Integer> oldVisionSet = geoProcessor.getVisionSet(spaceId, zoneId[0], userId, oldSiteVO.getVision());
        Set<Integer> oldExistSet = geoProcessor.getExistSet(spaceId, zoneId[0], userId, oldSiteVO.getExist());
        if (userId == 1) {
            log.info("【获取旧视野和旧存在集合】{} ms", System.currentTimeMillis() - start);
        }

        // 更新用户位置
        if (zoneId[0] != zoneId[1]) {
            // 区域发生了变化，则需要清除用户在旧区域的位置信息
            geoCache.removeUser(oldSiteVO, zoneId[0]);
        }
        geoCache.putUser(siteDTO, userId, zoneId[1]);
        oldSiteVO.updateSite(siteDTO);
        if (userId == 1) {
            log.info("【更新用户位置】{} ms", System.currentTimeMillis() - start);
        }

        // 获取用户当前能看到的其他用户，以及当前能看到该用户的其他用户
        Set<Integer> newVisionSet = geoProcessor.getVisionSet(spaceId, zoneId[1], userId, siteDTO.getVision());
        Set<Integer> newExistSet = geoProcessor.getExistSet(spaceId, zoneId[1], userId, siteDTO.getExist());
        if (userId == 1) {
            log.info("【获取新视野和新存在集合】{} ms", System.currentTimeMillis() - start);
        }

        // 新旧视野集取交集，则是位置不变的情况
        Set<Integer> noChangeVisionSet = new HashSet<>(oldVisionSet);
        noChangeVisionSet.retainAll(newVisionSet);
        // 新老集合移除掉位置不变的，就是消失的或者新增的
        oldVisionSet.removeAll(noChangeVisionSet);
        newVisionSet.removeAll(noChangeVisionSet);
        if (userId == 1) {
            log.info("【用户视野变动计算】{} ms", System.currentTimeMillis() - start);
        }
        // 为当前用户发送消息
        spaceProcessor.changeSiteState(userId, WsType.SITE_REMOVE, oldVisionSet);
        spaceProcessor.changeSiteState(userId, WsType.SITE_ADD, newVisionSet);
        if (userId == 1) {
            log.info("【用户视野变动消息发送】{} ms", System.currentTimeMillis() - start);
        }

        // 新旧存在取交集，就是能看到该用户位置改变的其他用户
        Set<Integer> changeExistSet = new HashSet<>(oldExistSet);
        changeExistSet.retainAll(newExistSet);
        // 新老集合移除掉可以看到位置发生变化的，剩下的就是其他用户看到该用户消失或者出现
        oldExistSet.removeAll(changeExistSet);
        newExistSet.removeAll(changeExistSet);
        if (userId == 1) {
            log.info("【其他用户视野变动计算】{} ms", System.currentTimeMillis() - start);
        }
        // 为其他用户发送当前用户的消失、位置更新和出现消息
        spaceProcessor.changeSiteState(oldExistSet, WsType.SITE_REMOVE, userId);
        spaceProcessor.changeSiteState(changeExistSet, WsType.SITE_CHANGE, userId);
        spaceProcessor.changeSiteState(newExistSet, WsType.SITE_ADD, userId);
        if (userId == 1) {
            log.info("【其他用户视野变动消息发送】{} ms【终】", System.currentTimeMillis() - start);
        }
    }

    @Override
    public void updateVision() {
        long start = System.currentTimeMillis();
        SiteVO nowSiteVO = SessionRepository.getSite();
        int spaceId = nowSiteVO.getSpaceId();
        int userId = nowSiteVO.getUser().getUserId();
        int vision = nowSiteVO.getVision();
        log.info("开始更新视野：{}  （{} ms）", userId, System.currentTimeMillis() - start);
        // 获取用户能看见的集合
        int zoneId = zoneProcessor.getUserZoneId(userId);
        Set<Integer> newVisionSet = geoProcessor.getVisionSet(spaceId, zoneId, userId, vision);
        log.info("更新视野完成：{} （{} ms）", userId, System.currentTimeMillis() - start);
        spaceProcessor.changeSiteState(userId, WsType.VISION_UPDATE, newVisionSet);
        log.info("视野信息推送完成：{} （{} ms）", userId, System.currentTimeMillis() - start);
    }

    @Override
    public void exitSpace() {
        // 用户退出，先获取位置信息
        SiteVO siteVO = SessionRepository.getSite();
        int spaceId = siteVO.getSpaceId();
        int userId = siteVO.getUser().getUserId();

//        // 获取能看到该用户的其他用户，并向他们发送消失通知
//        int exist = siteVO.getExist();
//        Set<Integer> existSet = geoProcessor.getExistSet(spaceId, userId, exist, false);
//        spaceProcessor.changeSiteState(existSet, WsType.SITE_REMOVE, userId);

        // 退出区域检查
        int[] zoneId = zoneProcessor.zoneCheck(userId, siteVO, null);

        // 最后将用户移除（使用的是旧的区域，即退出时的区域）
        geoCache.removeUser(siteVO, zoneId[0]);
        spaceCache.spaceOut(spaceId, userId);
    }

    @Override
    public Site getUserSite(int spaceId, int userId) {
        return spaceCache.getUserSite(userId);
    }
}
