package top.iceclean.chatspace.gateway.loadbalance.entry;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * websocket 消息接收对象
 * @author : Ice'Clean
 * @date : 2022-06-01
 */
@Data
@AllArgsConstructor
public class WsMessageDTO {
    /** 消息类型 */
    private WsType wsType;

    /** 消息主体 */
    private Object wsContent;

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
