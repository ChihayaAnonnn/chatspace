package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.constant.SessionType;

/**
 * 用户回应聊天请求响应对象
 * @author : Ice'Clean
 * @date : 2022-10-16
 */
public class ChatRequestVO {
    @Data
    public static class RequestSyn {
        /** 请求的会话类型 */
        private SessionType type;
        /** 请求用户的位置信息 */
        private SiteVO site;

        public String getType() {
            return type.name();
        }
    }

    @Data
    public static class SynAck {
        /** 响应的用户信息 */
        private UserVO user;
        /** 是否同意 */
        private Boolean access;
        /** 生成的会话 ID */
        private Integer sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinAck {
        /** 离开的用户信息 */
        private UserVO user;
        /** 所处的会话 ID */
        private Integer sessionId;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
