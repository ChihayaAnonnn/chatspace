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
    public enum State {
        /** 空间为开放 */
        NOT_OPEN,
        /** 没有权限进入 */
        NO_PERMISSION
    }
    /** 空间 ID、名称、尺寸 */
    private Integer spaceId;
    private String spaceName;
    private Integer sizeX;
    private Integer sizeY;

    /** 空间中的在线用户数、负载因子、空间状态 */
    private Integer onlineNum;
    private Integer loadFactor;
    private State state;

    public SpaceVO(Space space, int onlineNum) {
        this.spaceId = space.getId();
        this.spaceName = space.getSpaceName();
        this.sizeX = space.getSizeX();
        this.sizeY = space.getSizeY();
        this.onlineNum = onlineNum;
    }
}
