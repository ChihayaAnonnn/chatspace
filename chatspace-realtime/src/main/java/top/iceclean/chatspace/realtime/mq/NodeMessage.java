package top.iceclean.chatspace.realtime.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import top.iceclean.chatspace.infrastructure.utils.Pair;

import java.util.List;

/**
 * @author : Ice'Clean
 * @date : 2023-02-23
 */
@Data
public class NodeMessage {
    /** 消息类型枚举 */
    public enum Type {
        /** 用户连接重置 */
        USER_RESET,
        /** 用户跨节点消息 */
        USER_MESSAGE
    }
    /** 消息类型 */
    Type type;
    /** 目标用户 */
    private int userId;
    /** 数据实体（序列化后） */
    private String data;
    /** 数据实体（批处理的跨节点消息） */
    private List<Pair<Integer, String>> batchData;

    public NodeMessage(Type type, int userId, String data) {
        this.type = type;
        this.userId = userId;
        this.data = data;
    }

    public NodeMessage(Type type, List<Pair<Integer, String>> batchData) {
        this.type = type;
        this.batchData = batchData;
    }
}
