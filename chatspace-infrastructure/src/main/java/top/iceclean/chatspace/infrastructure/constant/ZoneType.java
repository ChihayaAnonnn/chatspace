package top.iceclean.chatspace.infrastructure.constant;

/**
 * 区域类型
 * @author : Ice'Clean
 * @date : 2022-11-29
 */
public enum ZoneType {
    /** 无区域（检查用，不实际使用） */
    NON(-1),
    /** 公共区域 */
    PUBLIC(0),
    /** 私有区域 */
    PRIVATE(1),
    /** 宝箱区域 */
    BOX(2),
    /** 传送门区域 */
    PORTAL(3),
    /** 留言板区域 */
    BOARD(4),
    /** 信箱区域 */
    MAILBOX(5);

    private final int value;

    ZoneType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ZoneType get(int type) {
        ZoneType zoneType;
        switch (type) {
            case 0: zoneType = PUBLIC; break;
            case 1: zoneType = PRIVATE; break;
            case 2: zoneType = BOX; break;
            case 3: zoneType = PORTAL; break;
            case 4: zoneType = BOARD; break;
            case 5: zoneType = MAILBOX; break;
            default: zoneType = null;
        }
        return zoneType;
    }
}
