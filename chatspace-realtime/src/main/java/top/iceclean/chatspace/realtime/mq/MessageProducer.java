package top.iceclean.chatspace.realtime.mq;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.QueueConst;
import top.iceclean.chatspace.infrastructure.dto.MessageDTO;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.realtime.server.ServerProperties;
import top.iceclean.feign.UserClient;

/**
 * 发送 MQ 消息
 * @author : Ice'Clean
 * @date : 2022-12-03
 */
@Slf4j
@Component
public class MessageProducer {
    private final AmqpTemplate amqpTemplate;
    private final UserCache userCache;

    private final ServerProperties serverProperties;

    public MessageProducer(AmqpTemplate amqpTemplate, UserCache userCache, ServerProperties serverProperties) {
        this.amqpTemplate = amqpTemplate;
        this.userCache = userCache;
        this.serverProperties = serverProperties;
    }

    /** 系统中产生了新的聊天消息（供保存等） */
    public void newMessage(MessageDTO messageDTO) {
        amqpTemplate.convertAndSend("new-message", JSON.toJSONString(messageDTO));
    }

    /** 将消息广播到消息队列，供其他节点消费 */
    public void castMessage(int userId, String msg) {
        // 查找用户所在节点
        String serverNode = userCache.getServerNode(userId);
        if (serverNode != null && !serverNode.equals(serverProperties.getServerId())) {
            amqpTemplate.convertAndSend(QueueConst.NODE_CONSUMER_QUEUE + "-" + serverNode,
                    JSON.toJSONString(new NodeMessage(NodeMessage.Type.USER_MESSAGE, userId, msg)));
        } else {
            log.warn("消息推送失败，目标节点为空或者为本身");
        }
    }
}
