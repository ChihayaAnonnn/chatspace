package top.iceclean.chatspace.realtime.mq;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户位置消息处理器
 * @author : Ice'Clean
 * @date : 2023-01-12
 */
@Component
public class SiteMessageHandler {

    private final AmqpTemplate rabbitTemplate;

    public SiteMessageHandler(AmqpTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

//    public void userSiteChange(SiteDTO siteDTO) {
//        rabbitTemplate.convertAndSend("new-message", JSON.toJSONString(messageDTO));
//    }
}
