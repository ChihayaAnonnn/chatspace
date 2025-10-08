package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.iceclean.chatspace.infrastructure.constant.ItemType;
import top.iceclean.chatspace.infrastructure.constant.ZoneType;
import top.iceclean.chatspace.infrastructure.dto.ZoneDTO;

import java.util.Date;

/**
 * 区域实体
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Data
@Slf4j
@NoArgsConstructor
@TableName("t_zone")
public class Zone {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer groupId;
    private Integer spaceId;
    private Integer zonePid;
    private Integer zoneRid;
    private String zoneName;
    private Integer zoneType;
    private Integer startX;
    private Integer startY;
    private Integer endX;
    private Integer endY;
    private Date deleteTime;
    private Date createTime;
    private String accessKey;

    /** 根据区域数据对象生成 */
    public Zone(ZoneDTO zoneDTO, ZoneType type, Site userSite, int pid, int rid) {
        this.groupId = zoneDTO.getGroupId();
        this.spaceId = userSite.getSpaceId();
        this.zonePid = pid;
        this.zoneRid = rid;
        this.zoneName = zoneDTO.getZoneName();
        this.zoneType = type.value();
        this.startX = userSite.getX();
        this.startY = userSite.getY();
        this.endX = userSite.getX() + zoneDTO.getSizeX() - 1;
        this.endY = userSite.getY() + zoneDTO.getSizeY() - 1;
        // 兼顾方向
        int temp;
        if (endX < startX) {
            temp = startX;
            startX = endX;
            endX = temp;
        }
        if (endY < startY) {
            temp = startY;
            startY = endY;
            endY = temp;
        }
    }

    /**
     * 根据箱子信息生成
     * @param groupId 箱子所处群组
     * @param boxName 箱子名称
     * @param site 用户当前位置
     * @param boxType 箱子的类型
     */
    public Zone(int groupId, int pid, String boxName, Site site, ItemType boxType) {
        this.groupId = groupId;
        this.spaceId = site.getSpaceId();
        this.zonePid = pid;
        // 箱子的根区域等于父区域，即不可以超过父区域
        this.zoneRid = pid;
        this.zoneName = boxName;
        this.zoneType = ZoneType.BOX.value();
        // 以用户当前坐标为起点，根据箱子种类进行扩张
        this.startX = site.getX();
        this.startY = site.getY();
        switch (boxType) {
            case BOX_SOURCE:
                this.endX = this.startX + 1;
                this.endY = this.startY + 1;
                break;
            default:
                log.error("非箱子类型：" + boxType.name());
        }
    }
}
