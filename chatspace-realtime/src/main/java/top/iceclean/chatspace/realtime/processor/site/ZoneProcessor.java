package top.iceclean.chatspace.realtime.processor.site;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.infrastructure.po.Zone;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;
import top.iceclean.chatspace.infrastructure.common.Monitor;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;
import top.iceclean.feign.GroupClient;

import java.util.ArrayList;
import java.util.List;

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

    /** 群组内部服务 */
    private final GroupClient groupClient;
    /** 区域缓存服务 */
    private final ZoneCache zoneCache;

    public ZoneProcessor(SpaceProcessor spaceProcessor, GroupClient groupClient, ZoneCache zoneCache) {
        this.spaceProcessor = spaceProcessor;
        this.groupClient = groupClient;
        this.zoneCache = zoneCache;
    }

    /**
     * 处理用户进入区域的操作
     * @return 旧区域 -> 新区域，ID，没有变化则返回 null
     */
    public int[] enterZone() {
        // 判断用户是否有可以进入的区域
        SiteVO site = SessionRepository.getSite();
        int userId = site.getUserId();
        Integer tempZoneId = zoneCache.getTempZoneIn(userId);
        if (tempZoneId != null && tempZoneId > 0) {
            // 从原来的区域（父区域）退出，进入到临时区域（子区域）
            int oldZoneId = zoneCache.getUserZoneId(userId);
            // 处理区域进入事件
            enterHandle(site, tempZoneId, -1);
            return new int[]{oldZoneId, tempZoneId};
        } else {
            // 否则发送没有区域可以进入的消息
            spaceProcessor.nonZone(userId);
            return new int[0];
        }
    }

    /**
     * 处理用户退出区域的操作
     */
    public int[] exitZone(int userId) {
        int zoneId = zoneCache.getUserZoneId(userId);
        if (zoneId > 0) {
            // 有区域退出则执行
            SiteVO nowSite = SessionRepository.getSite();
            int newZoneId = exitHandle(userId, nowSite, zoneId);
            return new int[]{zoneId, newZoneId};
        } else {
            // 否则发送没有区域可以退出的消息
            spaceProcessor.nonZone(userId);
            return new int[0];
        }
    }

    public int[] zoneCheck(int userId, SiteVO oldSite, SiteVO newSite) {
        // 获取用户上一个状态所在的区域
        int oldZoneId = zoneCache.getUserZoneId(userId);
        // ① 如果新位置为空，说明用户离开空间了，清空区域标识
        if (newSite == null) {
            zoneCache.cleanTempZoneIn(userId);
            zoneCache.cleanUserZone(userId);
            return new int[]{oldZoneId, oldZoneId};
        }

        int nowZoneId;
        if (isSiteOutZone(oldZoneId, newSite)) {
            // ② 如果用户不在当前区域了，则一定是退出了区域
            nowZoneId = exitHandle(userId, newSite, oldZoneId);
        } else {
            // ③ 如果用户还在当前区域，那么也有可能是从没有进入的子区域中退出来了
            nowZoneId = oldZoneId;
            // 获取用户当前所处的临时区域，判断新位置下的区域 ID 是否与记录的临时区域 ID 不同
            Integer tempZoneIn = zoneCache.getTempZoneIn(userId);
            if (tempZoneIn != null) {
                // 如果是禁止进入的区域，要转成真正的区域 ID 进行判断
                tempZoneIn = Math.abs(tempZoneIn);
                ZoneCache.Info tempZoneInfo = zoneCache.getZoneInfo(oldZoneId, newSite);
                if (tempZoneInfo == null || !tempZoneIn.equals(tempZoneInfo.getZoneId())) {
                    // 用户离开临时区域
                    spaceProcessor.zoneOut(userId, null, null);
                }
            }
        }
        // 检查区域进入
        zoneInCheck(userId, oldSite, newSite, nowZoneId);
        return new int[]{oldZoneId, nowZoneId};
    }

    /**
     * 区域进入处理，由 in 区分两种情况
     * childZoneId 有值，表示是从子区域退出到父区域的
     * 没有值表示是从父区域进入到子区域的
     * @param zoneId 进入的区域 ID
     * @param childZoneId 是否从父区域进入子区域
     */
    private void enterHandle(SiteVO nowSite, int zoneId, int childZoneId) {
        // 获取用户当前所处空间
        int spaceId = nowSite.getSpaceId();
        int userId = nowSite.getUserId();
        // 获取新区域的区域实体和旧区域的区域 ID
        List<ZoneVO> zoneList = new ArrayList<>();
        List<Integer> zoneRemoveList = new ArrayList<>();

        // 获取区域内的所有子区域，为需要增加的（zoneId 为 0 时，表示从根区域退出去，不需要增加）
        if (zoneId > 0) {
            zoneList.addAll(groupClient.getZoneList(spaceId, zoneId));
            // 如果是从子区域退出到父区域的，则增加的区域要去除本身
            if (childZoneId > 0) {
                zoneList.removeIf(zone -> zone.getZoneId() == childZoneId);
            }
        }
        if (childZoneId < 0) {
            // 从父区域进入到子区域，父区域的其他区域需要消失
            int userZoneId = zoneCache.getUserZoneId(userId);
            if (userZoneId != 0) {
                zoneRemoveList.addAll(groupClient.getChildZoneIds(spaceId, userZoneId));
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
     * 用户在指定位置退出了指定区域
     * @param userId 用户 ID
     * @param nowSite 退出区域时所在的位置
     * @param nowZoneId 退出的区域 ID
     * @return 最后所在的区域 ID
     */
    private int exitHandle(int userId, SiteVO nowSite, int nowZoneId) {
        // 当前区域的子区域读需要移除
        List<Integer> zoneRemoveList = groupClient.getChildZoneIds(nowSite.getSpaceId(), nowZoneId);
        // 迭代判断用户是否在当前区域中，构造区域退出集合
        List<Integer> zoneOutList = new ArrayList<>();
        do {
            // 将退出的区域 ID 保存起来
            zoneOutList.add(nowZoneId);
            // 从缓存中将用户弹出当前区域
            zoneCache.zoneOut(nowZoneId, userId);
            // 获取父级区域，为用户当前所在区域，直到用户处于当前区域中
            nowZoneId = zoneCache.getUserZoneId(userId);
        } while (isSiteOutZone(nowZoneId, nowSite));
        // 处理用户退出区域
        spaceProcessor.zoneOut(userId, zoneOutList, zoneRemoveList);
        // 最后执行从子区域进入到父区域
        enterHandle(nowSite, nowZoneId, zoneOutList.get(zoneOutList.size()-1));
        // 返回新的所在区域
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
