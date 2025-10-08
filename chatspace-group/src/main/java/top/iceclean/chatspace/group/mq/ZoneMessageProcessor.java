package top.iceclean.chatspace.group.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.group.config.DelayQueueConfig;
import top.iceclean.chatspace.group.server.BoxService;
import top.iceclean.chatspace.infrastructure.constant.QueueConst;
import top.iceclean.chatspace.infrastructure.dto.UserHuntDTO;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;

/**
 * 箱子的消息队列处理器
 * 箱子锁使用延时队列实现
 * @author : Ice'Clean
 * @date : 2022-12-15
 */
@Slf4j
@Component
public class ZoneMessageProcessor {

    private final RabbitTemplate rabbitTemplate;
    @Lazy
    private BoxService boxService;

    public ZoneMessageProcessor(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Autowired
    public BoxService getBoxService() {
        return boxService;
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
        boxService.handleBoxLock(boxId, lock);
    }

    /**
     * 向消息队列中发送一条新的锁消息
     * @param targetTime 处理锁消息的目标时间
     * @param lock 1 为上锁，0 为解锁
     */
    public void newLockMsg(String targetTime, int boxId, boolean lock) {
        // 构造并序列化消息
        byte[] content = (boxId + "-" + lock).getBytes(StandardCharsets.UTF_8);
        // 计算要延迟的时间
        long delayTime = -DateUtils.timeInterval(targetTime, ChronoUnit.MILLIS);
        // 构建消息
        Message msg = MessageBuilder.withBody(content).setHeader("x-delay", delayTime).build();
        log.info("发送锁消息 " + msg);
        // 放到延时队列
        rabbitTemplate.convertAndSend(QueueConst.BOX_EXCHANGE, QueueConst.BOX_LOCK_QUEUE, msg);
    }


    @RabbitListener(queuesToDeclare = @Queue(QueueConst.BOX_HUNT_QUEUE))
    public void userHunt(String info) throws InterruptedException {
        UserHuntDTO userHuntDTO = JSON.parseObject(info, UserHuntDTO.class);
        log.info("\n消费者1 - 处理用户夺宝：" + userHuntDTO);
        Thread.sleep(500);
    }

    @RabbitListener(queuesToDeclare = @Queue(QueueConst.BOX_HUNT_QUEUE))
    public void userHunt2(String info) throws InterruptedException {
        UserHuntDTO userHuntDTO = JSON.parseObject(info, UserHuntDTO.class);
        log.info("\n消费者2 - 处理用户夺宝：" + userHuntDTO);
        Thread.sleep(500);
    }

    public void newUserHunt(int userId, int huntId) {
        rabbitTemplate.convertAndSend(QueueConst.BOX_HUNT_QUEUE,
                JSON.toJSONString(new UserHuntDTO(userId, huntId)));
    }

    /**
     * 向消息队列中发送一条区域创建消息
     * @param zoneVO 新的区域响应对象
     */
    public void newZone(ZoneVO zoneVO) {
        log.info("发送区域新建消息：{}", zoneVO);
        rabbitTemplate.convertAndSend(QueueConst.ZONE_CREATED_QUEUE, JSON.toJSONString(zoneVO));
    }
}
