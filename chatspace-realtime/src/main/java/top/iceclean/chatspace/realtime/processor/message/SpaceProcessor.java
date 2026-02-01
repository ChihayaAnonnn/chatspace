package top.iceclean.chatspace.realtime.processor.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.vo.*;
import top.iceclean.chatspace.realtime.common.DataGenerator;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.feign.GroupClient;

import java.util.*;


/**
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Slf4j
@Component
public class SpaceProcessor {
    /** 群组内部服务 */
    private final GroupClient groupClient;
    /** 区域缓存服务 */
    private final ZoneCache zoneCache;

    public SpaceProcessor(GroupClient groupClient, ZoneCache zoneCache) {
        this.groupClient = groupClient;
        this.zoneCache = zoneCache;
    }

    public void zoneInit() {
        // 获取所有空间的所有区域，进行缓存
        groupClient.getSpaceIdList().forEach(spaceId ->
                groupClient.getZones(spaceId).forEach(zoneCache::cachedZoneSite));
    }

    /**
     * 向当前用户发送空间初始化消息
     * @param siteVO 用户位置对象
     */
    public void sendSpaceInitMessage(SiteVO siteVO) {
        // 构建空间初始化消息
        SpaceInitVO spaceInitVO = new SpaceInitVO();
        spaceInitVO.setZoneList(groupClient.getZoneList(siteVO.getSpaceId(), 0));
        spaceInitVO.setSpace(groupClient.getSpaceData(siteVO.getSpaceId()));
        spaceInitVO.setUserSite(siteVO);
        log.info("推送空间初始化消息：{}", siteVO);
        BaseProcessor.castMessage(WsType.SPACE_INIT, siteVO.getUserId(), spaceInitVO);
    }

    public void sendZoneInitMessage(int userId, List<ZoneVO> zoneList, List<Integer> zoneRemoveList) {
        // 构造区域初始化消息
        ZoneInitVO zoneInitVO = new ZoneInitVO(zoneList, zoneRemoveList);
        BaseProcessor.castMessage(WsType.ZONE_INIT, userId, zoneInitVO);
    }

    /**
     * 向一个用户发送一连串的用户位置更改消息
     * @param userId 消息接收者（一个用户）
     * @param wsType 消息类型（发现 / 消失）
     * @param userIdSet 发生更改的用户（一连串用户）
     */
    public void changeSiteState(int userId, WsType wsType, Set<Integer> userIdSet) {
        if (userIdSet != null && !userIdSet.isEmpty()) {
            // 与用户不在同一个节点的，视野集合推迟到接收到转发消息时维护
            BaseProcessor.castMessage(wsType, new DataGenerator.SiteChange(userId, userIdSet));
        }
    }

    /**
     * 向一连串用户发送一个用户的位置更改消息
     * @param userIdSet 消息接收者（一连串用户）
     * @param wsType 消息类型（发现 / 消失 / 更改）
     * @param userId 发生更改的用户（一个用户）
     */
    public void changeSiteState(Set<Integer> userIdSet, WsType wsType, int userId) {
        // 发送给目标用户该用户出现的消息
        if (userIdSet != null && !userIdSet.isEmpty()) {
            // 与用户不在同一个节点的，视野集合推迟到接收到转发消息时维护
            BaseProcessor.castMessage(wsType, new DataGenerator.SiteChange(userIdSet, userId));
        }
    }

    /**
     * 用户进入区域范围消息
     * @param userId 用户 ID
     * @param zoneId 区域 ID
     */
    public void zoneIn(int userId, int zoneId) {
        // 向用户发送进入区域的通知消息
        BaseProcessor.castMessage(WsType.ZONE_IN, userId, new ZoneInOutVO.ZoneIn(zoneCache.getZoneVO(zoneId)));
    }

    /**
     * 用户进入没有权限的区域范围消息
     * @param userId 用户 ID
     * @param zoneId 区域 ID
     */
    public void zoneInDeny(int userId, int zoneId) {
        // 向用户发送禁止进入区域消息
        BaseProcessor.castMessage(WsType.ZONE_IN_DENY, userId, zoneCache.getZoneVO(zoneId));
    }

    /**
     * 没有区域可以进入的消息
     */
    public void nonZone(int userId) {
        BaseProcessor.castMessage(WsType.NON_ZONE, userId, "当前没有区域可操作");
    }

    /**
     * 用户从一个或多个嵌套的子区域离开
     * 并进入当前所处的父级区域
     * @param userId 用户 ID
     * @param zoneOutList 退出的多个嵌套的子区域 ID（都需要移除）
     * @param zoneRemoveList 用户上一个状态所在的区域的所有子区域（都需要移除）
     */
    public void zoneOut(int userId, List<Integer> zoneOutList, List<Integer> zoneRemoveList) {
        // 构造区域退出消息（从哪些区域离开了）
        ZoneInOutVO.ZoneOut zoneOutVO;
        if (zoneOutList == null) {
            Integer tempZoneIn = zoneCache.cleanTempZoneIn(userId);
            zoneOutVO = new ZoneInOutVO.ZoneOut(tempZoneIn == null ? Collections.emptyList() :
                    Collections.singletonList(Math.abs(tempZoneIn)));
        } else {
            zoneOutVO = new ZoneInOutVO.ZoneOut(zoneOutList, zoneRemoveList);
        }
        // 推送区域退出消息，列表为空即为退出临时区域，直接发送消息给用户即可（取绝对值兼容禁止区域）
        BaseProcessor.castMessage(WsType.ZONE_OUT, userId, zoneOutVO);
    }

    /**
     * 当前用户发送空间范围的广播消息
     * @param msg 消息内容
     * @return 响应
     */
    public Response castSpaceMessage(String msg) {
        BaseProcessor.castMessage(WsType.SEND_MESSAGE,
                new DataGenerator.SpaceCast(SessionRepository.getUser(), msg));
        return new Response(ResponseStatusEnum.OK).setMsg("发送空间广播成功");
    }

    /**
     * 当前用户发送世界范围的广播消息
     * @param msg 消息内容
     * @return 响应
     */
    public Response castUniverseMessage(String msg) {
        BaseProcessor.castMessage(WsType.SEND_MESSAGE,
                new DataGenerator.UniverseCast(SessionRepository.getUser(), msg));
        return new Response(ResponseStatusEnum.OK).setMsg("发送世界广播成功");
    }

    /**
     * 处理箱子锁消息，广播箱子加解锁消息
     * @param boxId
     * @param lock
     */
    public void handleBoxLock(int boxId, boolean lock) {

    }

    /**
     * 向新区域所在区域广播区域创建消息
     * @param zoneVO 区域响应对象
     */
    public void castZoneCreated(ZoneVO zoneVO) {
        BaseProcessor.castMessage(WsType.NEW_ZONE, new DataGenerator.NewZoneCast(zoneVO));
    }
}
