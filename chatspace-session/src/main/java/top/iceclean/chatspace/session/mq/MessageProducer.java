package top.iceclean.chatspace.session.mq;

import com.alibaba.fastjson.JSON;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.po.SessionRequest;

import java.util.Scanner;

/**
 * 消息队列的消息生产者
 * @author : Ice'Clean
 * @date : 2022-10-10
 */
@Component
public class MessageProducer {
    @Autowired
    private AmqpTemplate amqpTemplate;

    public void sessionRequest(SessionRequest request) {
        amqpTemplate.convertAndSend("session-request", JSON.toJSONString(request));
    }
}
