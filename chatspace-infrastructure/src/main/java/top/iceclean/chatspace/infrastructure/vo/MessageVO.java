package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.constant.MessageType;
import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.infrastructure.dto.MessageDTO;
import top.iceclean.chatspace.infrastructure.po.Message;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;

/**
 * 消息响应对象
 * @author : Ice'Clean
 * @date : 2022-05-26 18:00:00
 */
@Data
@NoArgsConstructor
public class MessageVO {
    /** 消息发送者 */
    private UserVO sender;
    /** 消息会话类型 */
    private SessionType sessionType;
    /** 消息的目标 ID */
    private Integer targetId;
    /** 消息类型 */
    private MessageType messageType;
    /** 消息内容 */
    private String content;
    /** 消息发送时间 */
    private String sendTime;

    /** 当前用户是否为消息发送者 */
    private Boolean self;

    /**
     * 构建消息响应对象
     * @param message 目标消息
     * @param session 消息所属会话
     * @param sender 消息发送者
     * @param self 消息是否为当前用户发送
     */
    public MessageVO(Message message, SessionVO session, UserVO sender, boolean self) {
        // 消息元数据
        this.sender = sender;
        this.sessionType = session.getSessionType();
        this.targetId = session.getTargetId();
        this.messageType = MessageType.get(message.getType());
        this.content = message.getContent();
        this.sendTime = DateUtils.parseDate(message.getCreateTime());

        // 用户个性化数据
        this.self = self;
    }

    public MessageVO(MessageDTO messageDTO, UserVO sender) {
        this.sender = sender;
        this.sessionType = messageDTO.getSessionType();
        this.targetId = messageDTO.getTargetId();
        this.messageType = messageDTO.getMessageType();
        this.content = messageDTO.getContent();
        this.sendTime = DateUtils.getTime();
    }

    public MessageVO setSelf(int userId) {
        this.self = sender != null && sender.getUserId() == userId;
        return this;
    }

    /**
     * 精简消息发送时间
     * 如果是今天以内的，缩短为小时和分钟
     * 如果是今天之前的，缩短为月数和天数
     * 如果是今年之前的，缩短为年份和月份
     * @param want 是否需要
     */
    public MessageVO shortTime(boolean want) {
        // 在需要时精简
        if (want) {
            // 重解析时间为数组
            int[] times = DateUtils.reparseTime(sendTime);
            // 获取年
            int year = Integer.parseInt(DateUtils.getYear());
            int day = Integer.parseInt(DateUtils.getDay());
            // 精简日期
            if (year > times[0]) {
                sendTime = String.format("%d年%d月", times[0], times[1]);
            } else if (day > times[2]) {
                sendTime = String.format("%d月%d日", times[1], times[2]);
            } else {
                sendTime = String.format("%02d:%02d", times[3], times[4]);
            }
        }
        return this;
    }

    public SessionType sessionType() {
        return this.sessionType;
    }

    public String getSessionType() {
        return sessionType.name();
    }

    public String getMessageType() {
        return messageType.name();
    }
}
