package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 区域初始化消息响应
 * @author : Ice'Clean
 * @date : 2022-12-22
 */
@Data
@AllArgsConstructor
public class ZoneInitVO {
    /** 区域内的区域数据 */
    private List<ZoneVO> zoneList;
    /** 因进入区域而消失的父级区域内其他子区域 ID 集合 */
    private List<Integer> zoneRemoveList;
}
