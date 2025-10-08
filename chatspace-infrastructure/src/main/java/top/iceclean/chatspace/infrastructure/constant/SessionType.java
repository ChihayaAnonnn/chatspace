package top.iceclean.chatspace.infrastructure.constant;

/**
 * 接收方类型
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
public enum SessionType {
    /** 临时会话 */
    TEMP(-1),
    /** 好友 */
    FRIEND(0),
    /** 群组 */
    GROUP(1),
    /** 区域 */
    ZONE(2),
    /** 空间 */
    SPACE(3),
    /** 宇宙 */
    UNIVERSE(4);

    private final int value;

    SessionType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /**
     * 通过 type 值获取对应的枚举对象
     * @param typeValue type 值
     * @return 对应的枚举对象
     */
    public static SessionType get(int typeValue) {
        SessionType type;
        switch (typeValue) {
            case -1: type = TEMP; break;
            case 0: type = FRIEND; break;
            case 1: type = GROUP; break;
            case 2: type = ZONE; break;
            case 3: type = SPACE; break;
            case 4: type = UNIVERSE; break;
            default: type = null;
        }
        return type;
    }
}
