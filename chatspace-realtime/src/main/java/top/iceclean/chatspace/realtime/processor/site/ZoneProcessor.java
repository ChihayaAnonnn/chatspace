package top.iceclean.chatspace.realtime.processor.site;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.po.Zone;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.vo.ZoneInOutVO;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;
import top.iceclean.feign.GroupClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 区域的进出处理器
 * @author : Ice'Clean
 * @date : 2023-01-09
 */
@Slf4j
@Component
public class ZoneProcessor {
    /** 空间消息处理器 */
    private final SpaceProcessor spaceProcessor;
    /** 地理位置处理器 */
    private final GeoProcessor geoProcessor;

    /** 群组内部服务 */
    private final GroupClient groupClient;
    /** 区域缓存服务 */
    private final ZoneCache zoneCache;
    private final GeoCache geoCache;

    public ZoneProcessor(SpaceProcessor spaceProcessor, GeoProcessor geoProcessor, GroupClient groupClient, ZoneCache zoneCache, GeoCache geoCache) {
        this.spaceProcessor = spaceProcessor;
        this.geoProcessor = geoProcessor;
        this.groupClient = groupClient;
        this.zoneCache = zoneCache;
        this.geoCache = geoCache;
    }

    /** 转接接口，获取用户所在的区域 */
    public int getUserZoneId(int userId) {
        return zoneCache.getUserZoneId(userId);
    }

    /**
     * 处理用户进入区域的操作
     */
    public void enterZone() {
        // 判断用户是否有可以进入的区域
        SiteVO site = SessionRepository.getSite();
        int userId = SessionRepository.getUser().getUserId();
        Integer tempZoneIn = zoneCache.getTempZoneIn(userId);
        if (tempZoneIn != null && tempZoneIn > 0) {
            // 从原来的区域（父区域）退出，进入到临时区域（子区域）
            geoCache.removeUser(site, zoneCache.getUserZoneId(userId));
            geoCache.putUser(site, tempZoneIn);
            // 处理区域进入事件
            enterZone(site, userId, tempZoneIn, -1);
        } else {
            // 否则发送没有区域可以进入的消息
            spaceProcessor.nonZone(userId);
        }
    }

    /**
     * 处理用户退出区域的操作
     */
    public void exitZone(int userId) {
        // 判断用户是否有区域可以退出
        int zoneId = zoneCache.getUserZoneId(userId);
        if (zoneId > 0) {
            // 从原来的区域（子区域）退出
            SiteVO nowSite = SessionRepository.getSite();
            geoCache.removeUser(nowSite, zoneId);
            // 构造用户要退出的区域
            List<Integer> zoneOutList = new ArrayList<>();
            do {
                // 将退出的区域 ID 保存起来
                zoneOutList.add(zoneId);
                // 从缓存中将用户弹出当前区域
                zoneCache.zoneOut(zoneId, userId);
                // 获取父级区域，为用户当前所在区域，直到用户处于当前区域中
                zoneId = zoneCache.getUserZoneId(userId);
            } while (isSiteOutZone(zoneId, nowSite));
            // 进入到父区域
            geoCache.putUser(nowSite, zoneId);
            // 处理用户退出区域
            exitZone(userId, zoneOutList, groupClient.getChildZoneIds(nowSite.getSpaceId(), zoneId));
            // 最后执行从子区域进入到父区域
            enterZone(nowSite, userId, zoneId, zoneOutList.get(zoneOutList.size()-1));
        } else {
            // 否则发送没有区域可以退出的消息
            spaceProcessor.nonZone(userId);
        }
    }

    /**
     * 区域进入处理，由 in 区分两种情况
     * childZoneId 有值，表示是从子区域退出到父区域的
     * 没有值表示是从父区域进入到子区域的
     * @param userId 用户 ID
     * @param zoneId 进入的区域 ID
     * @param childZoneId 是否从父区域进入子区域
     */
    public void enterZone(SiteVO nowSite, int userId, int zoneId, int childZoneId) {
        // 获取用户当前所处空间
        int spaceId = nowSite.getSpaceId();

        // 获取新区域的区域实体和旧区域的区域 ID
        List<ZoneVO> zoneList = new ArrayList<>();
        List<Integer> zoneRemoveList = new ArrayList<>();
        // 获取区域内的所有子区域，为需要增加的（zoneId 为 0 时，表示从根区域退出去，不需要增加）
        if (zoneId > 0) {
            zoneList = groupClient.getZoneList(spaceId, zoneId);
            // 如果是从子区域退出到父区域的，则增加的区域要去除本身
            if (childZoneId > 0) {
                zoneList.removeIf(zone -> zone.getZoneId() == childZoneId);
            }
        }
        if (childZoneId < 0) {
            // 从父区域进入到子区域，父区域的其他区域需要消失
            int userZoneId = zoneCache.getUserZoneId(userId);
            if (userZoneId != 0) {
                zoneRemoveList = groupClient.getChildZoneIds(spaceId, userZoneId);
                zoneRemoveList.remove((Integer) zoneId);
            }
            // 将临时区域清除
            zoneCache.cleanTempZoneIn(userId);
            // 然后将用户加入到新区域中
            zoneCache.zoneIn(zoneId, userId);
        }
        // 发送区域初始化消息
        if (zoneId != 0) {
            spaceProcessor.sendZoneInitMessage(userId, zoneList, zoneRemoveList);
        }
        // 进入区域后（即使是 0 号区域），需要再次进行区域进入检查（进来就在子区域上了）
        zoneInCheck(userId, null, nowSite, zoneId);
    }

    /**
     * 用户从一个或多个嵌套的子区域离开
     * 并进入当前所处的父级区域
     * @param userId 用户 ID
     * @param zoneOutList 退出的多个嵌套的子区域 ID（都需要移除）
     * @param zoneRemoveList 用户上一个状态所在的区域的所有子区域（都需要移除）
     */
    public void exitZone(int userId, List<Integer> zoneOutList, List<Integer> zoneRemoveList) {
        // 构造区域退出消息（从哪些区域离开了）
        ZoneInOutVO.ZoneOut zoneOutVO;
        if (zoneOutList == null) {
            zoneOutVO = new ZoneInOutVO.ZoneOut(Collections.singletonList(Math.abs(zoneCache.cleanTempZoneIn(userId))));
        } else {
            zoneOutVO = new ZoneInOutVO.ZoneOut(zoneOutList, zoneRemoveList);
        }
        // 推送区域退出消息，列表为空即为退出临时区域，直接发送消息给用户即可（取绝对值兼容禁止区域）
        spaceProcessor.zoneOut(userId, zoneOutVO);
    }

    public int[] zoneCheck(int userId, SiteVO oldSite, SiteVO newSite) {
        // 获取用户上一个状态所在的区域
        int oldZoneId = zoneCache.getUserZoneId(userId);
        // 区域退出检查后，如果区域发生了变化，则说明用户退出到父区域了，将执行父区域的自动进入确定
        int nowZoneId = zoneOutCheck(userId, newSite, oldZoneId);
        // 检查区域进入
        zoneInCheck(userId, oldSite, newSite, nowZoneId);
        // 返回用户当前所在的区域
        return new int[]{oldZoneId, nowZoneId};
    }

    /** 区域退出检查，并返回最终用户所处的区域 */
    private int zoneOutCheck(int userId, SiteVO newSite, int nowZoneId) {
        if (newSite == null) {
            // 如果新位置为空，说明用户离开空间了，清空区域标识
            zoneCache.cleanTempZoneIn(userId);
            zoneCache.cleanUserZone(userId);
        } else if (isSiteOutZone(nowZoneId, newSite)) {
            // 如果用户不在当前区域了，则一定是退出了区域
            // 当前区域的子区域读需要移除
            List<Integer> zoneRemoveList = groupClient.getChildZoneIds(newSite.getSpaceId(), nowZoneId);
            // 迭代判断用户是否在当前区域中，构造区域退出集合
            List<Integer> zoneOutList = new ArrayList<>();
            do {
                // 将退出的区域 ID 保存起来
                zoneOutList.add(nowZoneId);
                // 从缓存中将用户弹出当前区域
                zoneCache.zoneOut(nowZoneId, userId);
                // 获取父级区域，为用户当前所在区域，直到用户处于当前区域中
                nowZoneId = zoneCache.getUserZoneId(userId);
            } while (isSiteOutZone(nowZoneId, newSite));
            // 处理用户退出区域
            exitZone(userId, zoneOutList, zoneRemoveList);
            // 最后执行从子区域进入到父区域
            enterZone(newSite, userId, nowZoneId, zoneOutList.get(zoneOutList.size()-1));
        } else {
            // 如果用户还在当前区域，那么也有可能是从没有进入的子区域中退出来了，获取用户当前所处的临时区域
            Integer tempZoneIn = zoneCache.getTempZoneIn(userId);
            if (tempZoneIn != null) {
                // 如果是禁止进入的区域，要转成真正的区域 ID 进行判断
                tempZoneIn = Math.abs(tempZoneIn);
                ZoneCache.Info tempZoneInfo = zoneCache.getZoneInfo(nowZoneId, newSite);
                if (tempZoneInfo == null || !tempZoneIn.equals(tempZoneInfo.getZoneId())) {
                    // 用户离开临时区域
                    exitZone(userId, null, null);
                }
            }
        }
        return nowZoneId;
    }

    /**
     * 进入区域检查
     * @param userId 用户 ID
     * @param oldSite 用户的旧位置
     * @param newSite 用户所在的新位置
     * @param nowZoneId 用户当前所在的区域 ID
     */
    private void zoneInCheck(int userId, SiteVO oldSite, SiteVO newSite, int nowZoneId) {
        // 区域进入检查
        if (newSite != null) {
            ZoneCache.Info newZone = zoneCache.getZoneInfo(nowZoneId, newSite);
            // 新位置不为空的前提下，旧位置为空或者新旧位置不相同，则判断为进入区域
            boolean realInZone = newZone != null && (oldSite == null || !newZone.equals(zoneCache.getZoneInfo(nowZoneId, oldSite)));
            if (realInZone) {
                // 判断用户是否有进入该区域的权限（有则发送进入区域消息，否则发送区域没有进入权限的通知
                if (zoneCache.permitEnter(newZone.getZoneId(), userId)) {
                    // 设置临时区域并发送区域进入消息
                    zoneCache.zoneInTemp(newZone.getZoneId(), userId);
                    spaceProcessor.zoneIn(userId, newZone.getZoneId());
                } else {
                    // 区域禁止进入，将临时区域存储为负值，并发送区域禁止进入消息
                    zoneCache.zoneInTemp(-newZone.getZoneId(), userId);
                    spaceProcessor.zoneInDeny(userId, newZone.getZoneId());
                }
            }
        }
    }

    /**
     * 判断指定位置是否 不在区域中
     * @param zoneId 区域 ID
     * @param site 位置
     * @return 不在区域中则返回 true
     */
    private boolean isSiteOutZone(int zoneId, SiteVO site) {
        if (zoneId == 0) {
            return false;
        }
        Zone zone = zoneCache.getZone(zoneId);
        return !(site.getX() >= zone.getStartX() && site.getX() <= zone.getEndX() &&
                site.getY() >= zone.getStartY() && site.getY() <= zone.getEndY());
    }
}
