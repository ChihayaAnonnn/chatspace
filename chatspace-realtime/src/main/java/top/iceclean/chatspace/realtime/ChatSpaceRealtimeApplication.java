package top.iceclean.chatspace.realtime;

import org.springframework.context.ConfigurableApplicationContext;
import top.iceclean.chatspace.infrastructure.annotation.EnableInfrastructure;
import top.iceclean.chatspace.realtime.processor.message.SpaceProcessor;
import top.iceclean.chatspace.realtime.server.WebSocketServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import top.iceclean.feign.*;
import top.iceclean.feign.annotation.EnableInnerCall;

/**
 * @author : Ice'Clean
 * @date : 2022-10-08
 */
@EnableInnerCall
@EnableInfrastructure
@EnableFeignClients(clients = {
        UserClient.class, MessageClient.class,
        GroupClient.class, FriendClient.class, SessionClient.class})
@SpringBootApplication
public class ChatSpaceRealtimeApplication {
    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext run = SpringApplication.run(ChatSpaceRealtimeApplication.class, args);
        // 缓存每个空间的区域信息
        run.getBean(SpaceProcessor.class).zoneInit();
        // 启动 Netty 服务
        run.getBean(WebSocketServer.class).start();
    }
}
