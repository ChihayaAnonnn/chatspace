package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.po.Space;

/**
 * @author : Ice'Clean
 * @date : 2022-12-03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceVO {
    /** 空间 ID、名称、尺寸 */
    private Integer spaceId;
    private String spaceName;
    private Integer sizeX;
    private Integer sizeY;

    /** 空间中的在线用户数 */
    private Integer onlineNum;

    public SpaceVO(Space space, int onlineNum) {
        this.spaceId = space.getId();
        this.spaceName = space.getSpaceName();
        this.sizeX = space.getSizeX();
        this.sizeY = space.getSizeY();
        this.onlineNum = onlineNum;
    }
}
