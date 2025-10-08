package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.po.Space;

import java.util.List;

/**
 * 空间初始化消息
 * @author : Ice'Clean
 * @date : 2022-10-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceInitVO {
    /** 空间数据 */
    private Space space;
    /** 空间的区域数据 */
    private List<ZoneVO> zoneList;
    /** 当前用户位置数据 */
    private SiteVO userSite;

}
