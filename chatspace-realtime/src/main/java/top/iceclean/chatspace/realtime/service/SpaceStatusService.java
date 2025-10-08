package top.iceclean.chatspace.realtime.service;

import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.realtime.share.SpacePoint;

import java.util.List;
import java.util.Set;

/**
 * 空间状态数据服务接口（openGauss 专用）
 * @author : Ice'Clean
 * @date : 2022-10-27
 */
public interface SpaceStatusService {
    /**
     * 将指定用户添加到指定空间的指定点
     * 并返回能够看见来到新位置的用户的其他用户的 ID
     * @param userId 目标用户 ID
     * @param spaceId 目标空间 ID
     * @param point 空间内的目标点
     * @return 能够看见来到新位置的用户的其他用户的 ID（其他用户 发现 该用户）
     */
    Set<Integer> addUser(int userId, int spaceId, SpacePoint point);

    /**
     * 将指定用户从指定空间的指定点移除
     * 并返回原本能够看见该位置用户的其他用户的 ID
     * @param userId 目标用户 ID
     * @param spaceId 目标空间 ID
     * @param point 空间内的目标点
     * @return 原本能够看见该位置用户的其他用户的 ID（其他用户 移除 该用户）
     */
    Set<Integer> removeUser(int userId, int spaceId, SpacePoint point);

    /**
     * 在指定空间的指定点集中添加指定用户的视野
     * 并返回在新视野中用户能够发现的其他用户的 ID
     * @param userId 目标用户 ID
     * @param spaceId 目标空间 ID
     * @param visionList 用户视野覆盖到的点集
     * @return 在新视野中用户能够发现的其他用户的 ID（该用户 发现 其他用户）
     */
    Set<Integer> addVision(int userId, int spaceId, List<SpacePoint> visionList);

    /**
     * 在指定空间的指定点集中移除指定用户的视野
     * 并返回该用户原本能够发现的其他用户的 ID
     * @param userId 目标用户 ID
     * @param spaceId 目标空间 ID
     * @param visionList 用户视野覆盖到的点集
     * @return 该用户原本能够发现的其他用户的 ID（该用户 移除 其他用户）
     */
    Set<Integer> removeVision(int userId, int spaceId, List<SpacePoint> visionList);

    /**
     * 在指定空间的指定点集中添加指定用户的存在感
     * @param userId 目标用户 ID
     * @param spaceId 目标空间 ID
     * @param existList 用户存在感覆盖到的点集
     */
    void addExist(int userId, int spaceId, List<SpacePoint> existList);

    /**
     * 在指定空间的指定点集中移除指定用户的存在感
     * @param userId 目标用户 ID
     * @param spaceId 目标空间 ID
     * @param existList 用户存在感覆盖到的点集
     */
    void removeExist(int userId, int spaceId, List<SpacePoint> existList);

    /**
     * 判断用户是否能感知到另一个用户
     * @param spaceId 所处的空间 ID
     * @param fromUser 当前用户
     * @param toUser 目标用户
     * @return 是否能感知到
     */
    boolean canFeel(int spaceId, int fromUser, int toUser);

    /**
     * 销毁用户在指定空间中的状态
     * @param userId 指定用户
     * @param spaceId 指定空间
     */
    void destroyStatus(int userId, int spaceId);

    /**
     * 通过用户 ID 获取位所处位置
     * @param userId 用户 ID
     * @param spaceId 空间 ID
     * @return 位置响应
     */
    SpacePoint getPoint(int userId, int spaceId);

    /**
     * 初始化区域所处位置
     * @param zoneId 区域 ID
     * @param spaceId 空间 ID
     * @param zoneList 区域所处点集
     */
    void initZone(int zoneId, int spaceId, List<SpacePoint> zoneList);

    /**
     * 检查指定坐标是否在某个区域内
     * @param spaceId 空间 ID
     * @param point 指定点
     * @return 如果在区域内，返回该区域的 ID
     */
    Integer getZone(int spaceId, SpacePoint point);
}
