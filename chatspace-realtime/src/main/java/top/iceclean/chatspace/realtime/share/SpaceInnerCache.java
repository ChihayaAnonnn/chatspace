package top.iceclean.chatspace.realtime.share;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.po.Space;
import top.iceclean.feign.GroupClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 对空间中元数据的缓存
 * @author : Ice'Clean
 * @date : 2022-10-27
 * @deprecated 新的 geo 算法不需要频繁用到空间元数据了
 */
@Component
@Deprecated
public class SpaceInnerCache {
    /** 已缓存的空间元数据 */
    private static final ConcurrentHashMap<Integer, Space> SPACE_MAP = new ConcurrentHashMap<>();
    /** 空间服务 */
    private static GroupClient groupClient;

    @Autowired
    public void setSpaceService(GroupClient groupClient) {
        SpaceInnerCache.groupClient = groupClient;
    }

    /**
     * 从缓存中获取指定空间的元数据，如果没有，则到数据库中查询
     * @param spaceId 目标空间 ID
     * @return 目标空间的元数据
     */
    public static Space getSpace(int spaceId) {
        SPACE_MAP.computeIfAbsent(spaceId, sId -> groupClient.getSpaceData(sId));
        return SPACE_MAP.get(spaceId);
    }

    /**
     * 判断指定空间的某个坐标是否合法
     * @param spaceId 空间 ID
     * @param x x 坐标
     * @param y y 坐标
     * @return 是否合法
     */
    public static boolean isLegal(int spaceId, int x, int y) {
        Space space = getSpace(spaceId);
        return x >= 0 && x < space.getSizeX() && y >= 0 && y < space.getSizeY();
    }
}
