package top.iceclean.chatspace.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import top.iceclean.chatspace.infrastructure.constant.MessageType;
import top.iceclean.chatspace.infrastructure.constant.SessionType;

/**
 * 聊天消息接收对象
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
@Data
@AllArgsConstructor
public class MessageDTO {
    /** 消息发送者 ID */
    private Integer senderId;

    /** 消息会话类型 */
    private SessionType sessionType;
    /** 消息的目标 ID */
    private Integer targetId;

    /** 消息类型 */
    private MessageType messageType;
    /** 消息主体内容 */
    private String content;
}
