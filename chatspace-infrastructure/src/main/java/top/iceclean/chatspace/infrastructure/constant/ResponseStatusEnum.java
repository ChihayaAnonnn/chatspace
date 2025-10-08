package top.iceclean.chatspace.infrastructure.constant;

/**
 * @author : Ice'Clean
 * @date : 2022-02-07
 */
public enum ResponseStatusEnum {
    /** 标准状态码 */
    OK(0, "ok"),
    BAD_REQUEST(400, "请求失败"),
    INTERNAL_SERVER_ERROR(500, "服务器出错"),

    /** 系统错误码 */
    UNKNOWN_ERROR(100, "未知异常"),
    AUTHENTICATION_ERROR(101,"认证异常："),
    AUTHORITY_ERROR(102,"权限异常："),
    DATABASE_ERROR(111, "数据库错误"),
    ILLEGAL_OPERATION(120, "非法操作"),

    /** 错误码 */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    USER_NOT_EXIST(1002, "用户不存在"),
    USERNAME_INVALID(1003, "用户名已被占用"),
    CODE_INVALID(1004, "验证码无效或邮箱被修改"),
    PASSWORD_INVALID(1005, "旧密码不匹配"),
    REQUEST_PARAM_ILLEGAL(1006, "参数校验失败"),
    NOT_FRIEND_ERROR(1007, "当前用户与目标用户非好友关系"),
    ITEM_AMOUNT_LACK(1008, "所需物品数量不足"),
    ILLEGAL_ZONE_COVER(1009, "区域非法覆盖"),
    OVER_ZONE_COVER(1010, "区域覆盖范围超过根区域");


    /** 状态码 */
    private final int value;

    /** 附加信息 */
    private final String msg;

    ResponseStatusEnum(int value, String msg) {
        this.value = value;
        this.msg = msg;
    }

    public int value() {
        return value;
    }

    public String msg() {
        return msg;
    }
}
