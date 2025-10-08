package top.iceclean.chatspace.group.server;

import top.iceclean.chatspace.infrastructure.dto.ZoneDTO;
import top.iceclean.chatspace.infrastructure.po.Zone;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;

import java.util.List;

/**
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
public interface ZoneService {

    /**
     * 受检查的创建一片区域
     * @param zoneDTO 区域实体
     * @return 创建响应
     */
    Response checkedCreateZone(ZoneDTO zoneDTO);

    /**
     * 获取群组中的所有区域
     * @param groupId 群组 ID
     * @return 所有的区域集合
     */
    Response getGroupZoneList(int groupId);

    /**
     * 内部用的创建一片区域
     * @param zone 区域实体
     * @return 创建成功则返回区域
     */
    Response createZone(Zone zone);

    /**
     * 删除一片区域
     * @param zone 区域
     * @return 删除反馈
     */
    Response deleteZone(Zone zone);

    /**
     * 通过区域 ID 获取区域实体
     * @param zoneId 区域 ID
     * @return 区域实体
     */
    Zone getZoneById(int zoneId);

    /**
     * 获取指定空间中的所有区域
     * @param spaceId 空间 ID
     * @param zonePid 区域的父级 ID
     * @return 区域集合
     */
    List<ZoneVO> getZoneList(int spaceId, int zonePid);

    /**
     * 通过父级区域 ID 集合获取全部的子区域 ID 集合
     * @param spaceId 空间 ID
     * @param zonePid 父级区域 ID
     * @return 子区域 ID 集合
     */
    List<Integer> getChildZoneIds(int spaceId, int zonePid);

    /**
     * 获取指定空间中的所有区域
     * @param spaceId 空间 ID
     * @return 区域集合
     */
    List<Zone> getZones(int spaceId);

    /**
     * 获取指定团组的所有区域
     * @param groupId 群组 ID
     * @return 区域集合
     */
    List<Zone> getGroupZones(int groupId);

    /**
     * 获取指定团组在指定空间的所有区域
     * @param groupId 群组 ID
     * @param spaceId 空间 ID
     * @return 区域集合
     */
    List<Zone> getGroupZones(int groupId, int spaceId);
}
