package top.iceclean.chatspace.infrastructure.dto;

import lombok.Data;
import top.iceclean.chatspace.infrastructure.constant.WsType;

/**
 * websocket 消息接收对象
 * @author : Ice'Clean
 * @date : 2022-06-01
 */
@Data
public class WsMessageDTO {
    /** 消息类型 */
    private WsType wsType;
    /** 消息主体 */
    private Object wsContent;
    /** 生成该消息的时间戳 */
    private Long timestamp;

    public WsMessageDTO(WsType wsType, Object wsContent) {
        this.wsType = wsType;
        this.wsContent = wsContent;
        this.timestamp = System.currentTimeMillis();
    }

    public WsType getWsTypeEnum() {
        return wsType;
    }

    public String getWsType() {
        return wsType.name();
    }

    public Object getWsContent() {
        return wsContent;
    }
}
