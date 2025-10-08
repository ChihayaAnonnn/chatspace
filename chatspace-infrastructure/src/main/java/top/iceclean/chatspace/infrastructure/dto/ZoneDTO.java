package top.iceclean.chatspace.infrastructure.dto;

import lombok.Data;

/**
 * 区域数据对象
 * @author : Ice'Clean
 * @date : 2022-10-27
 */
@Data
public class ZoneDTO {
    /** 区域的名称 */
    private String zoneName;
    /** 区域所属的群组 */
    private Integer groupId;
    /** 区域横纵方向的跨度 */
    private Integer sizeX;
    private Integer sizeY;
}
