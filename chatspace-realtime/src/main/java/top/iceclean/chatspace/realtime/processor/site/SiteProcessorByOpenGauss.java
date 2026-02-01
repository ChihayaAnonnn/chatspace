package top.iceclean.chatspace.realtime.processor.site;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;
import top.iceclean.chatspace.realtime.service.SpaceStatusService;
import top.iceclean.chatspace.realtime.share.SpaceInnerCache;
import top.iceclean.chatspace.realtime.share.SpacePoint;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.feign.GroupClient;

import java.util.*;

/**
 * 用户位置处理器
 * @author : Ice'Clean
 * @date : 2022-09-30
 * @deprecated 应该使用 SiteProcessorByRedis 性能更高
 */
@Component
@Deprecated
public class SiteProcessorByOpenGauss {
    /** 空间状态服务 */
    private final SpaceStatusService spaceStatusService;
    /** 区域服务 */
    private final GroupClient groupClient;
    /** 消息处理器 */
    private final SpaceProcessor spaceProcessor;

    @Autowired
    public SiteProcessorByOpenGauss(SpaceStatusService sss, GroupClient gc, SpaceProcessor sp) {
        this.spaceStatusService = sss;
        this.groupClient = gc;
        this.spaceProcessor = sp;
    }

    /** 服务器刚启动时，进行区域的初始化 */
    public void zoneInit(int spaceId) {
        // 获取所有区域
        List<ZoneVO> zoneList = groupClient.getZoneList(spaceId, 0);
        // 执行初始化
        for (ZoneVO zone : zoneList) {
            spaceStatusService.initZone(zone.getZoneId(), spaceId,
                    rangeScan(zone.getStartX(), zone.getStartY(), zone.getEndX(), zone.getEndY()));
        }
    }

    /** 区域进出检查 */
    public void zoneCheck(int userId, int spaceId, SpacePoint oldPoint, SpacePoint newPoint) {
        // 查看新旧位置的区域数据
        Integer oldZone = oldPoint == null ? null : spaceStatusService.getZone(spaceId, oldPoint);
        Integer newZone = newPoint == null ? null : spaceStatusService.getZone(spaceId, newPoint);

        // 如果新旧数据不相等，则需要发送通知了
        if (!Objects.equals(oldZone, newZone)) {
            // 旧位置不为空，则处理区域离开
            if (oldZone != null) {
                System.out.printf("<-- 用户 %d 退出区域：%d\n", userId, oldZone);
                // 将用户从区域中移出
                groupClient.exitGroup(userId, oldZone);
                // 发送离开区域消息
                spaceProcessor.zoneOut(userId, null, null);
            }

            // 新位置不为空，则处理区域加入
            if (newZone != null) {
                System.out.printf("--> 用户 %d 进入区域：%d\n", userId, newZone);
                // 发送新区域加入通知
                spaceProcessor.zoneIn(userId, newZone);
            }
        }
    }

    public void newSite() {
        // 获取当前用户信息
        int userId = SessionRepository.getUser().getUserId();
        SiteVO siteVO = SessionRepository.getSite();
        SpacePoint newPoint = SpacePoint.get(siteVO);
        int spaceId = siteVO.getSpaceId();

        System.out.println("========= 初始化新用户的位置信息：" + userId);
        long start = System.currentTimeMillis();

        // ① 先销毁可能残留的状态
        spaceStatusService.destroyStatus(userId, spaceId);
        // ② 初始化用户存在感
        changeExist(userId, spaceId, siteVO.getExist(), null, newPoint);
        // ③ 将用户添加到新位置，并收集在新位置中能发现该用户的其他用户
        Set<Integer> otherAdd = spaceStatusService.addUser(userId, spaceId, newPoint);
        // ④ 直接处理添加
        spaceProcessor.changeSiteState(otherAdd, WsType.SITE_ADD, userId);
        // ⑤ 最后将用户的视野添加到新位置，并直接处理
        Set<Integer> add = spaceStatusService.addVision(userId, spaceId, rangeScan(spaceId, siteVO.getVision(), newPoint));
        spaceProcessor.changeSiteState(userId, WsType.SITE_ADD, add);

        System.out.println("========= 初始化耗时：" + (System.currentTimeMillis() - start));

        // 进行区域检查
        zoneCheck(userId, spaceId, null, newPoint);
    }

    public void removeSite() {
        // 获取当前用户信息
        int userId = SessionRepository.getUser().getUserId();
        SiteVO siteVO = SessionRepository.getSite();
        SpacePoint oldPoint = SpacePoint.get(siteVO);
        int spaceId = siteVO.getSpaceId();

        System.out.println("========= 销毁用户位置信息：" + userId);
        long start = System.currentTimeMillis();

        // ① 将用户从旧位置移除，并收集原本能发现该用户的其他用户，并直接处理
        Set<Integer> otherRemove = spaceStatusService.removeUser(userId, spaceId, oldPoint);
        spaceProcessor.changeSiteState(otherRemove, WsType.SITE_REMOVE, userId);
        // ② 移除用户的存在感
        changeExist(userId, spaceId, siteVO.getExist(), oldPoint, null);
        // ③ 移除用户的视野，并直接处理
        spaceStatusService.removeVision(userId, spaceId, rangeScan(spaceId, siteVO.getVision(), oldPoint));

        System.out.println("========= 销毁耗时：" + (System.currentTimeMillis() - start));

        // 进行区域检查
        zoneCheck(userId, spaceId, oldPoint, null);
    }

    /**
     * 更新位置信息
     * @param content 位置信息串
     */
    public void updateSite(String content) {
        // 获取当前用户信息
        int userId = SessionRepository.get().getUser().getUserId();
        SiteVO siteVO = SessionRepository.getSite();
        int spaceId = siteVO.getSpaceId();

        // 解析消息，获取旧空间点并更新为新的空间点
        SiteDTO siteDTO = JSONObject.parseObject(content, SiteDTO.class);
        SpacePoint oldPoint = SpacePoint.get(siteVO);
        siteVO.updateSite(new SiteVO(siteDTO, spaceId, userId));
        SpacePoint newPoint = SpacePoint.get(siteVO);

        System.out.println("========= 更新用户坐标：" + userId);
        long start = System.currentTimeMillis();

        // ① 先收集视野的变化点集
        List<SpacePoint> visionRemoveList = new ArrayList<>();
        List<SpacePoint> visionAddList = new ArrayList<>();
        rangeMoveScan(spaceId, siteVO.getVision(), oldPoint, newPoint, visionRemoveList, visionAddList);
        // ② 将用户的视野从旧位置移除，并收集原本用户能发现的其他用户，用于移除
        Set<Integer> userRemove = spaceStatusService.removeVision(userId, spaceId, visionRemoveList);
        // ③ 将用户从旧位置移除，并收集原本能够发现该用户的其他用户
        Set<Integer> otherRemove = spaceStatusService.removeUser(userId, spaceId, oldPoint);
        // ④ 将用户的存在感更新到新的位置
        changeExist(userId, spaceId, siteVO.getExist(), oldPoint, newPoint);
        // ⑤ 将用户添加到新位置，并收集在新位置中能发现该用户的其他用户
        Set<Integer> otherAdd = spaceStatusService.addUser(userId, spaceId, newPoint);
        // ⑥ 将用户的视野添加到新的位置，并收集用户现在能发现的其他用户，用于发现
        Set<Integer> userAdd = spaceStatusService.addVision(userId, spaceId, visionAddList);
        // ⑦ 合并其他用户的删除和添加集合（有交集的则是更新）
        mergeSiteChange(userId, otherRemove, otherAdd);
        // ⑧ 直接处理用户移除和发现其他用户
        spaceProcessor.changeSiteState(userId, WsType.SITE_REMOVE, userRemove);
        spaceProcessor.changeSiteState(userId, WsType.SITE_ADD, userAdd);

        System.out.println("========= 更新耗时：" + (System.currentTimeMillis() - start));

        // 进行区域检查
        zoneCheck(userId, spaceId, oldPoint, newPoint);
    }

    private void changeExist(int userId, int spaceId, int exist, SpacePoint oldPoint, SpacePoint newPoint) {
        if (oldPoint != null && newPoint != null) {
            // 有新旧位置的话，执行位置移动
            List<SpacePoint> removeList = new ArrayList<>();
            List<SpacePoint> addList = new ArrayList<>();
            // 收集点集
            rangeMoveScan(spaceId, exist, oldPoint, newPoint, removeList, addList);
            // 执行存在感的移除和新增
            spaceStatusService.removeExist(userId, spaceId, removeList);
            spaceStatusService.addExist(userId, spaceId, addList);
        } else if (newPoint != null){
            // 只有新位置的话，执行位置的初始化
            spaceStatusService.addExist(userId, spaceId, rangeScan(spaceId, exist, newPoint));
        } else if (oldPoint != null) {
            // 只有旧位置的话，执行简单的清除
            spaceStatusService.removeExist(userId, spaceId, rangeScan(spaceId, exist, oldPoint));
        }
    }

    /**
     * 将位置更改的情况合并，并发送消息
     * @param userId 当前用户 ID
     * @param otherRemove 需要移除当前用户的其他用户 ID 集合
     * @param otherAdd 需要添加当前用户的其他用户 ID 集合
     */
    private void mergeSiteChange(int userId, Set<Integer> otherRemove, Set<Integer> otherAdd) {
        // 从上边两个集合中，挑选出需要更新坐标的用户（先移除了该用户，后边又发现该用户的，则判定为更新）
        if (otherAdd.size() > 0) {
            Set<Integer> update = new HashSet<>();
            Iterator<Integer> iterator = otherRemove.iterator();
            while (iterator.hasNext()) {
                Integer id = iterator.next();
                if (otherAdd.contains(id)) {
                    iterator.remove();
                    otherAdd.remove(id);
                    update.add(id);
                }
            }
            spaceProcessor.changeSiteState(update, WsType.SITE_CHANGE, userId);
        }

        // 将移除和更新的消息发送给其他用户
        spaceProcessor.changeSiteState(otherRemove, WsType.SITE_REMOVE, userId);
        // 将看到新用户的消息发送给其他用户
        spaceProcessor.changeSiteState(otherAdd, WsType.SITE_ADD, userId);
    }

    /**
     * 范围移动扫描，将从就位置移动到新位置的范围变化收集起来
     * 采用的是差值扫描方法，即两个范围重叠的地方不进行扫描
     * @param range 范围的大小
     * @param start 起点坐标
     * @param end 终点坐标
     * @param oldList 旧位置的点集
     * @param newList 新位置的点集
     */
    private static void rangeMoveScan(int spaceId, int range,
                                      SpacePoint start, SpacePoint end,
                                      List<SpacePoint> oldList, List<SpacePoint> newList) {
        // 遍历因子计算
        int len = range * 2 + 1;
        int xMove, yMove;
        int xFlag = 1, yFlag = 1;

        if ((xMove = end.getX() - start.getX()) < 0) {
            xMove = - xMove;
            xFlag = -xFlag;
        }
        if ((yMove = end.getY() - start.getY()) < 0) {
            yMove = -yMove;
            yFlag = -yFlag;
        }

        int oldX = start.getX() - range * xFlag;
        int newX = end.getX() + range * xFlag;
        int oldY = start.getY() - range * yFlag;
        int newY = end.getY() + range * yFlag;

        // 真正的遍历
        for (int x = 0; x < xMove; x++) {
            for (int y = 0; y < len; y++) {
                checkAndAdd(oldList, spaceId, oldX + x * xFlag, oldY + y * yFlag);
                checkAndAdd(newList, spaceId, newX - x * xFlag, newY - y * yFlag);
            }
        }
        for (int y = 0; y < yMove; y++) {
            for (int x = xMove; x < len; x++) {
                checkAndAdd(oldList, spaceId, oldX + x * xFlag, oldY + y * yFlag);
                checkAndAdd(newList, spaceId, newX - x * xFlag, newY - y * yFlag);
            }
        }
    }

    /**
     * 将以指定点为中心，范围内的所有点集收集起来
     * @param spaceId 空间 ID
     * @param range 指定的范围
     * @param center 指定的中心点
     * @return 收集得到的点集
     */
    public static List<SpacePoint> rangeScan(int spaceId, int range, SpacePoint center) {
        List<SpacePoint> pointList = new ArrayList<>();
        int x = center.getX() - range;
        int y = center.getY() - range;
        range = 2 * range + 1;
        for (int i = 0; i < range; i++) {
            for (int j = 0; j < range; j++) {
                checkAndAdd(pointList, spaceId, x+i, y+j);
            }
        }
        return pointList;
    }

    /**
     * 以指定的左上角和右下角顶点，扫描出一个矩阵
     * @return 收集得到的点集
     */
    public static List<SpacePoint> rangeScan(int startX, int startY, int endX, int endY) {
        List<SpacePoint> pointList = new ArrayList<>();
        int temp = startX;
        while (startY <= endY) {
            startX = temp;
            while (startX <= endX) {
                pointList.add(SpacePoint.get(startX, startY));
                startX++;
            }
            startY++;
        }
        return pointList;
    }

    public static void checkAndAdd(List<SpacePoint> list, int spaceId, int x, int y) {
        if (SpaceInnerCache.isLegal(spaceId, x, y)) {
            list.add(SpacePoint.get(x, y));
        }
    }
}
