package top.iceclean.chatspace.realtime.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.redisson.cache.LRUCacheMap;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.QueueConst;
import top.iceclean.chatspace.infrastructure.dto.MessageDTO;
import top.iceclean.chatspace.infrastructure.utils.Pair;
import top.iceclean.chatspace.infrastructure.common.Monitor;
import top.iceclean.chatspace.realtime.server.ServerProperties;

import java.util.*;
import java.util.concurrent.*;

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

    /** 二级缓存，缓存用户所在的结点 */
    private final LRUCacheMap<Integer, String> userNodeCache;
    /** Redis 查不到时，回种空值 */
    private static final String REDIS_NULL = "";
    /** 哈希表，存储每个节点要发送的数据 */
    private final ConcurrentMap<String, List<Pair<Integer, String>>> messageMap;

    public MessageProducer(AmqpTemplate amqpTemplate, UserCache userCache, ServerProperties serverProperties) {
        this.amqpTemplate = amqpTemplate;
        this.userCache = userCache;
        this.serverProperties = serverProperties;
        this.userNodeCache = new LRUCacheMap<>(500, 10_000, 30_000);
        this.messageMap = new ConcurrentHashMap<>();
        // 启动批量消息发送任务
        batchMessageSendTask();
    }

    private void batchMessageSendTask() {
        // 定时线程池，用来批量发送消息队列消息
        ScheduledExecutorService messageCastPool = Executors.newScheduledThreadPool(
                4, r -> new Thread(r, "messageCast-" + r.toString()));
        messageCastPool.scheduleAtFixedRate(() -> {
            long start = System.currentTimeMillis();
            // 取出节点和对应的消息列表
            Set<String> nodeSet = messageMap.keySet();
            for (String serverNode : nodeSet) {
                List<Pair<Integer, String>> messageList = messageMap.remove(serverNode);
                NodeMessage nodeMessage = new NodeMessage(NodeMessage.Type.USER_MESSAGE, messageList);
                // 批量发送到消息队列
                log.info("【批量发送 MQ 消息】{} -> {} 条", serverNode, messageList.size());
                amqpTemplate.convertAndSend(QueueConst.NODE_CONSUMER_QUEUE + "-" + serverNode, JSON.toJSONString(nodeMessage));
            }
            if (!nodeSet.isEmpty()) {
                Monitor.addMqSendTime(System.currentTimeMillis() - start);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    /** 系统中产生了新的聊天消息（供保存等） */
    public void newMessage(MessageDTO messageDTO) {
        amqpTemplate.convertAndSend("new-message", JSON.toJSONString(messageDTO));
    }

    /** 将消息广播到消息队列，供其他节点消费 */
    public void castMessage(int userId, String msg) {
        // 查找用户所在节点（二级缓存优先，Redis）
        String serverNode = userNodeCache.computeIfAbsent(userId, f ->
                Optional.ofNullable(userCache.getServerNode(userId)).orElse(REDIS_NULL));
        if (!serverNode.equals(REDIS_NULL) && !serverNode.equals(serverProperties.getServerId())) {
            // 优化，小消息多次发送会有性能损耗，先将要发送的消息收集起来，再批量发送到各个节点能提高性能
            messageMap.putIfAbsent(serverNode, new ArrayList<>());
            messageMap.get(serverNode).add(new Pair<>(userId, msg));
//            amqpTemplate.convertAndSend(QueueConst.NODE_CONSUMER_QUEUE + "-" + serverNode,
//                    JSON.toJSONString(new NodeMessage(NodeMessage.Type.USER_MESSAGE, userId, msg)));
        } else {
            log.warn("消息推送失败，目标节点为空或者为本身 userId={}（二级缓存:{}）", userId, userNodeCache.size());
        }
    }
}
