package top.iceclean.chatspace.infrastructure.dto;

import lombok.Data;
import top.iceclean.chatspace.infrastructure.constant.SessionType;

/**
 * 聊天请求对象
 * @author : Ice'Clean
 * @date : 2022-10-16
 */
public class ChatRequestDTO {
    /** 用户请求建立聊天 */
    @Data
    public static class RequestSyn {
        /** 请求的会话类型 */
        private SessionType type;
        /** 请求的目标用户 ID */
        private Integer targetId;
    }

    /** 用户请求退出聊天 */
    @Data
    public static class RequestFin {
        /** 请求的会话 ID */
        private Integer targetId;
    }

    /** 用户回应聊天 */
    @Data
    public static class SynAck {
        /** 针对的用户 */
        private Integer targetId;
        /** 会话类型 */
        private SessionType type;
        /** 是否同意 */
        private Boolean access;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
