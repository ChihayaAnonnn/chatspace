package top.iceclean.chatspace.realtime.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.constant.QueueConst;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.WsMessageDTO;
import top.iceclean.chatspace.infrastructure.po.SessionRequest;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.BaseProcessor;
import top.iceclean.chatspace.realtime.processor.message.ChatProcessor;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;
import top.iceclean.chatspace.realtime.server.ServerProperties;
import top.iceclean.chatspace.realtime.share.ServerSession;

import javax.xml.soap.Node;

/**
 * 消息队列的消息接收者器
 * @author : Ice'Clean
 * @date : 2022-10-10
 */
@Slf4j
@Component
public class MessageReceiver {

    private final ChatProcessor chatProcessor;
    private final SpaceProcessor spaceProcessor;
    private final ServerProperties properties;

    public MessageReceiver(ChatProcessor chatProcessor, SpaceProcessor spaceProcessor,
                           ServerProperties properties, AmqpAdmin amqpAdmin,
                           SimpleRabbitListenerContainerFactory containerFactory) {
        this.chatProcessor = chatProcessor;
        this.spaceProcessor = spaceProcessor;
        this.properties = properties;
        nodeConsumer(containerFactory, properties, amqpAdmin);
    }



    /**
     * 会话请求消息
     * @param msg 消息
     */
    @RabbitListener(queuesToDeclare = @Queue("session-request"))
    public void sessionRequest(String msg) {
        log.info("【消息队列】会话请求消息：" + msg);
        SessionRequest sessionRequest = JSON.parseObject(msg, SessionRequest.class);
        log.info(sessionRequest.toString());
        chatProcessor.requestMsg(sessionRequest);
    }
    
    /**
     * 箱子锁消息消费者
     * @param lockMsg 锁消息内容
     */
    @RabbitListener(queues = QueueConst.BOX_LOCK_QUEUE)
    public void lockConsume(String lockMsg) {
        log.info("处理锁消息：" + lockMsg);
        String[] split = lockMsg.split("-");
        int boxId = Integer.parseInt(split[0]);
        boolean lock = Boolean.parseBoolean(split[1]);
        spaceProcessor.handleBoxLock(boxId, lock);
    }

    /**
     * 区域创建消费者
     * @param zoneString 区域响应对象
     */
    @RabbitListener(queuesToDeclare = @Queue(QueueConst.ZONE_CREATED_QUEUE))
    public void zoneCreated(String zoneString) {
        ZoneVO zoneVO = JSON.parseObject(zoneString, ZoneVO.class);
        log.info("【消息队列】区域创建消息：{}", zoneVO);
        spaceProcessor.castZoneCreated(zoneVO);
    }

    /**
     * 动态创建的消费者
     * ① 处理该节点的用户连接重置消息
     * ② 处理发送给该节点用户的消息
     */
    public void nodeConsumer(SimpleRabbitListenerContainerFactory containerFactory,
                             ServerProperties properties, AmqpAdmin amqpAdmin) {
        // 声明队列
        org.springframework.amqp.core.Queue queue = new org.springframework.amqp.core.Queue(
                QueueConst.NODE_CONSUMER_QUEUE + "-" + properties.getServerId());
        amqpAdmin.declareQueue(queue);
        SimpleMessageListenerContainer container = containerFactory.createListenerContainer();
        container.setQueues(queue);
        container.setMessageListener(message -> {
            // 反序列化出消息
            NodeMessage nodeMessage = JSON.parseObject(new String(message.getBody()), NodeMessage.class);
            int userId = nodeMessage.getUserId();
            switch (nodeMessage.getType()) {
                case USER_RESET: userResetHandle(userId); break;
                case USER_MESSAGE: userMessageHandle(userId, nodeMessage.getData()); break;
                default:
            }
        });
        container.start();
    }

    private void userResetHandle(int userId) {
        log.info("收到用户连接重置消息：{}", userId);
        // 将用户的本地状态发送出去，供无感知切换连接继承状态值
        ServerSession session = SessionRepository.get(userId);
        // 目前直接断开连接，由客户端重连
        if (session != null) {
            session.getChannel().close();
//            BaseProcessor.castMessage(WsType.USER_RESET, session.getChannel(), session.getSite());
        }
    }

    private void userMessageHandle(int userId, String data) {
        WsMessageDTO wsMessageDTO = JSON.parseObject(data, WsMessageDTO.class);
        switch (wsMessageDTO.getWsTypeEnum()) {
            case SITE_ADD: break;
            case SITE_REMOVE: break;
        }
        BaseProcessor.castMessage(wsMessageDTO.getWsTypeEnum(), userId, wsMessageDTO.getWsContent());

    }
}
