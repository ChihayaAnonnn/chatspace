package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.iceclean.chatspace.infrastructure.constant.ZoneType;
import top.iceclean.chatspace.infrastructure.po.Group;
import top.iceclean.chatspace.infrastructure.po.Zone;

/**
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Data
@NoArgsConstructor
public class ZoneVO {
    /** 区域 ID、所在空间 ID、父级区域 ID 和根区域 ID */
    private Integer zoneId;
    private Integer spaceId;
    private Integer zonePid;
    private Integer zoneRid;

    /** 所属群组 ID、名称和标识 */
    private Integer groupId;
    private String groupName;
    private String groupAvatar;

    /** 区域名称、区域类型*/
    private String zoneName;
    private ZoneType zoneType;

    /** 起始和结束坐标 */
    private Integer startX;
    private Integer startY;
    private Integer endX;
    private Integer endY;

    public ZoneVO(Zone zone, Group group) {
        // 检查关联
        if (!zone.getGroupId().equals(group.getId())) {
            System.err.printf("zone 和 group 关联失败：groupId %s != %s %n", zone.getGroupId(), group.getId());
            return;
        }
        this.zoneId = zone.getId();
        this.spaceId = zone.getSpaceId();
        this.zonePid = zone.getZonePid();
        this.zoneRid = zone.getZoneRid();
        this.zoneName = zone.getZoneName();
        this.zoneType = ZoneType.get(zone.getZoneType());
        this.startX = zone.getStartX();
        this.startY = zone.getStartY();
        this.endX = zone.getEndX();
        this.endY = zone.getEndY();

        this.groupId = group.getId();
        this.groupName = group.getGroupName();
        this.groupAvatar = group.getAvatar();
    }

    public String getZoneType() {
        return zoneType.name();
    }
}
