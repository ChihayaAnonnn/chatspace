package top.iceclean.chatspace.infrastructure.constant;



/**
 * websocket 通讯类型
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
public enum WsType {
    /** 用户心跳 */
    HEART_BEAT,
    /** 连接重置 */
    USER_RESET,

    /** 用户上下线信息 */
    USER_ONLINE,
    /** 用户强制下线消息 */
    USER_FORCED_OFFLINE,

    /** 发送消息 */
    SEND_MESSAGE,
    /** 会话请求 */
    SESSION_REQUEST,
    /** Token 失效 */
    TOKEN_EXPIRED,

    /** 空间初始化消息 */
    SPACE_INIT,
    /** 区域初始化消息 */
    ZONE_INIT,

    /** 位置更新请求 */
    SITE_UPDATE,
    /** 视野集合更新 */
    VISION_UPDATE,
    /** 位置用户增加响应 */
    SITE_ADD,
    /** 位置用户更改响应 */
    SITE_CHANGE,
    /** 位置用户消失响应 */
    SITE_REMOVE,

    /** 请求建立聊天 */
    CHAT_REQUEST_SYN,
    /** 请求失败，对方看不见当前用户，请求不可达 */
    CHAT_REQUEST_UNREACHABLE,
    /** 请求建立聊天回复消息 */
    CHAT_REQUEST_SYN_ACK,
    /** 请求退出聊天 */
    CHAT_REQUEST_FIN,
    /** 请求退出聊天回复消息 */
    CHAT_REQUEST_FIN_ACK,

    /** 进入区域的通知消息 */
    ZONE_IN,
    /** 禁止进入区域的通知消息 */
    ZONE_IN_DENY,
    /** 进入区域的确认消息 */
    ZONE_IN_ACK,
    /** 离开区域的通知消息 */
    ZONE_OUT,
    /** 所在区域已被删除消息 */
    ZONE_DELETED,
    /** 主动退出区域消息 */
    EXIT_ZONE,
    /** 没有区域可以进入消息 */
    NON_ZONE,
    /** 新区域消息 */
    NEW_ZONE,

    /** 语音聊天 */
    RTC_CANDIDATE,
    RTC_OFFER,
    RTC_ANSWER;
}
