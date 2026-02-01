package top.iceclean.chatspace.realtime.processor.message;

import com.alibaba.fastjson.JSON;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.WsMessageDTO;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;
import top.iceclean.chatspace.realtime.common.DataGenerator;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.mq.MessageProducer;
import top.iceclean.chatspace.realtime.share.ServerSession;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

/**
 * 基本消息处理器（统一消息交互器）
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Slf4j
@Component
public class BaseProcessor {
    /** 子消息类型 */
    private enum MessageType {
        /** 分别表示：生成器类型、用户类型、通道类型 */
        GENERATOR, USER, CHANNEL;
    }
    /** 内部子消息 */
    @Getter
    private static class QueueMessage {
        /** 消息发送时间 */
        private final LocalDateTime sendTime = LocalDateTime.now();
        /** 消息的处理类型 */
        private final MessageType type;
        /** websocket 消息类型 */
        private final WsType wsType;
        /** 消息生成器（type=1） */
        private DataGenerator.Generator generator;
        /** 目标用户（type=2） */
        private int userId;
        /** 目标管道（type=3） */
        private Channel channel;
        /** 目标消息（type=2/3） */
        private Object message;

        public QueueMessage(WsType wsType, DataGenerator.Generator generator) {
            this.type = MessageType.GENERATOR;
            this.wsType = wsType;
            this.generator = generator;
        }

        public QueueMessage(WsType wsType, int userId, Object message) {
            this.type = MessageType.USER;
            this.wsType = wsType;
            this.userId = userId;
            this.message = message;
        }

        public QueueMessage(WsType wsType, Channel channel, Object message) {
            this.type = MessageType.CHANNEL;
            this.wsType = wsType;
            this.channel = channel;
            this.message = message;
        }
    }

    /** 消息顺序投递队列 */
    private static final BlockingQueue<QueueMessage> MESSAGE_QUEUE = new LinkedBlockingQueue<>();
    /** 消息队列推送 */
    private static MessageProducer messageProducer;

    /** 工具类构造器私有 */
    private BaseProcessor() {

    }

    @Autowired
    public void setMessageProducer(MessageProducer messageProducer) {
        BaseProcessor.messageProducer = messageProducer;
    }

    @PostConstruct
    @SuppressWarnings("InfiniteLoopStatement")
    private void handleMessage() {
        // 单线程消费队列中的消息
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        log.info("初始化内部消息处理线程");
        executorService.submit(() -> {
            while (true) {
                // 从队列中获取消息
                QueueMessage message = MESSAGE_QUEUE.take();
                long delay = DateUtils.timeInterval(message.getSendTime(), ChronoUnit.MILLIS);
                log.info("【处理消息】（延迟：{}ms）（积压：{}）", delay, MESSAGE_QUEUE.size());
                // 处理消息
                switch (message.getType()) {
                    case GENERATOR:
                        // 将消息按照用户情况封装再发送给用户
                        DataGenerator.Generator generator = message.getGenerator();
                        generator.target().forEach(userId ->
                                sendMessage(message.getWsType(), userId, generator.exec(userId)));
                        break;
                    case USER:
                        // 将消息按照用户情况封装再发送给用户
                        sendMessage(message.getWsType(), message.getUserId(), message.getMessage());
                        break;
                    case CHANNEL:
                        // 将消息直接发送到管道中
                        sendMessage(message.getWsType(), message.getChannel(), message.getMessage());
                        break;
                    default:
                        // 提示错误
                        log.error("【错误的消息类型】{}", message.getType());
                }
            }
        });
        log.info("处理完毕");
    }

    /**
     * 广播消息
     * @param wsType 消息类型
     * @param generator 消息对象生成器
     */
    public static void castMessage(WsType wsType, DataGenerator.Generator generator) {
        // 将消息按照用户情况封装再发送给用户
        generator.target().forEach(userId -> sendMessage(wsType, userId, generator.exec(userId)));
    }

    /**
     * 广播消息（指定用户）
     * @param wsType 消息类型
     * @param userId 目标用户
     * @param msg 目标消息
     */
    public static void castMessage(WsType wsType, int userId, Object msg) {
        // 将消息按照用户情况封装再发送给用户
        sendMessage(wsType, userId, msg);
    }

    /**
     * 向指定管道发送消息
     * @param wsType 消息类型
     * @param channel 目标管道
     * @param msg 目标消息
     */
    public static void castMessage(WsType wsType, Channel channel, Object msg) {
        // 将消息直接发送到管道中
        sendMessage(wsType, channel, msg);
    }

    /**
     * 真正发送一条消息
     * @param wsType 消息类型
     * @param channel 目标管道
     * @param msg 目标消息
     */
    private static void sendMessage(WsType wsType, Channel channel, Object msg) {
        // 直接发送到指定管道
        if (channel!= null && channel.isOpen()) {
            channel.writeAndFlush(getMessageFrame(wsType, msg));
        }
    }

    /** 重载：通过 userId 发送 */
    private static void sendMessage(WsType wsType, int userId, Object msg) {
        ServerSession session;
        // 先检查用户是否在该节点
        if ((session = SessionRepository.get(userId)) != null) {
            sendMessage(wsType, session.getChannel(), msg);
        } else {
            // 否则推送到消息队列中，供相应的节点消费
            messageProducer.castMessage(userId, JSON.toJSONString(new WsMessageDTO(wsType, msg)));
        }
    }

    /**
     * 生成一条消息
     * @param wsType 消息类型
     * @param wsContent 消息主体内容
     * @return json 格式的消息实体
     */
    private static TextWebSocketFrame getMessageFrame(WsType wsType, Object wsContent) {
        return new TextWebSocketFrame(JSON.toJSONString(new WsMessageDTO(wsType, wsContent)));
    }
}
