package top.iceclean.chatspace.gateway.loadbalance.listener;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.gateway.loadbalance.hash.ConsistentHashing;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Netty 服务上下线监听，用户下线监听
 * @author : Ice'Clean
 * @date : 2023-01-24
 */
@Slf4j
@Component
public class NettyServiceListener implements ApplicationRunner {

    /** Nacos 地址 */
    @Value("${spring.cloud.nacos.server-addr}")
    private String serverUrl;
    /** 一致性哈希环 */
    private final ConsistentHashing consistentHashing;
    public NettyServiceListener(ConsistentHashing consistentHashing) {
        this.consistentHashing = consistentHashing;
    }

    @Override
    public void run(ApplicationArguments args) throws NacosException {
        serverListener();
        configListener();
    }

    private NamingService namingService;
    private final java.util.concurrent.ScheduledExecutorService reconciler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "netty-topology-reconcile"); t.setDaemon(true); return t;
            });

    /** 推送用于及时更新；定期取健康实例快照，弥补漏推与网关离线期间的变化。 */
    private void serverListener() throws NacosException {
        namingService = NacosFactory.createNamingService(serverUrl);
        namingService.subscribe("chatspace-netty", event -> reconcile(((NamingEvent) event).getInstances()));
        reconciler.scheduleWithFixedDelay(() -> {
            try { reconcile(namingService.getAllInstances("chatspace-netty", false)); }
            catch (Exception e) { log.error("通信节点拓扑对账失败，将在下一轮重试", e); }
        }, 0, 5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void reconcile(List<Instance> instances) {
        List<Instance> healthy = new ArrayList<>();
        for (Instance i : instances) if (i.isHealthy() && i.isEnabled()) healthy.add(i);
        consistentHashing.serverSync(healthy);
    }

    @javax.annotation.PreDestroy
    public void shutdown() throws NacosException {
        reconciler.shutdownNow();
        if (namingService != null) namingService.shutDown();
    }

    /** 配置中心数据变化监听 */
    private void configListener() throws NacosException {
        String nettyConfig = "chatspace-netty.yml";
        String nettyGroup = "DEFAULT_GROUP";

        ConfigService configService = NacosFactory.createConfigService(serverUrl);
        String content = configService.getConfig(nettyConfig, nettyGroup, 5000);
        log.info("配置中心数据：{}", content);
        configService.addListener(nettyConfig, nettyGroup, new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("感知到配置变化：{}", configInfo);
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        });
    }
}
