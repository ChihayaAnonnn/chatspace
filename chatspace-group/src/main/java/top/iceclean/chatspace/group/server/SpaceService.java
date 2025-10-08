package top.iceclean.chatspace.group.server;

import top.iceclean.chatspace.infrastructure.po.Space;
import top.iceclean.chatspace.infrastructure.pojo.Response;

import java.util.List;

/**
 * @author : Ice'Clean
 * @date : 2022-10-27
 */
public interface SpaceService {

    /**
     * 获取所有的空间实体
     * @return 空间实体列表
     */
    Response getSpaceList();

    /**
     * 获取所有空间的 ID
     * @return 空间 ID 集合
     */
    List<Integer> getSpaceIdList();

    /**
     * 获取空间元数据
     * @param spaceId 空间 ID
     * @return 空间元数据对象
     */
    Space getSpaceData(int spaceId);
}
