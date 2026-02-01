package top.iceclean.chatspace.gateway.loadbalance.listener;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.gateway.loadbalance.hash.ConsistentHashing;
import top.iceclean.chatspace.gateway.utils.RedissonUtils;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Netty 服务上下线监听，用户下线监听
 * @author : Ice'Clean
 * @date : 2023-01-24
 */
@Slf4j
// @Component
public class NettyServiceListener implements ApplicationRunner {

    /** Nacos 地址 */
    @Value("${spring.cloud.nacos.server-addr}")
    private String serverUrl;
    /** 一致性哈希环 */
    private final ConsistentHashing consistentHashing;
    /** Redisson */
    private final Redisson redisson;
    /** 更新 哈希环/元数据 的分布式锁 */
    private static final String UPDATE_LOCK = "chatspace:server:update:lock";

    public NettyServiceListener(ConsistentHashing consistentHashing, Redisson redisson) {
        this.consistentHashing = consistentHashing;
        this.redisson = redisson;
    }

    @Override
    public void run(ApplicationArguments args) throws NacosException {
        serverListener();
        configListener();
    }

    /** 存储最后一次更新的实例状态，用于下一次判断服务更变情况 */
    private Map<String, Instance> lastInstanceMap = new HashMap<>(0);
    /** 服务上下线与元数据变化监听 */
    private void serverListener() throws NacosException {
        NamingService namingService = NacosFactory.createNamingService(serverUrl);
        namingService.subscribe("chatspace-netty", event -> {
            // 先尝试获取锁，成功则执行服务更新，否则放弃
            RLock lock = redisson.getLock(UPDATE_LOCK);
            if (lock.tryLock()) {
                try {
                    updateServer(event);
                    log.info("成功获取到分布式锁，执行服务更新");
                } finally {
                    lock.unlock();
                }
            } else {
                log.info("获取分布式锁失败，放弃更新");
            }
        });
    }

    private void updateServer(Event event) {
        // 本次实例情况
        List<Instance> instances = ((NamingEvent) event).getInstances();
        // 本次服务映射
        Map<String, Instance> nowInstanceMap = new HashMap<>(instances.size());

        // 筛选出发生改变的服务
        List<Instance> newInstances = new ArrayList<>(instances.size());
        List<Instance> removedInstances = new ArrayList<>(instances.size());
        List<Instance> metadataUpdates = new ArrayList<>(instances.size());
        for (Instance i : instances) {
            String id = i.getIp() + ":" + i.getPort();
            nowInstanceMap.put(id, i);
            // 从旧实例中取出并删除
            Instance lastI = lastInstanceMap.remove(id);
            if (lastI == null) {
                // 实例不存在，则为新增的
                newInstances.add(i);
            } else if (!lastI.getMetadata().equals(i.getMetadata())) {
                // 实例存在且元数据发生改变
                metadataUpdates.add(i);
            }
        }
        // 最后剩下的实例即为删除的
        removedInstances.addAll(lastInstanceMap.values());
        lastInstanceMap = nowInstanceMap;

        log.info("新增的实例（{}）：{}", newInstances.size(), newInstances);
        log.info("移除的实例（{}）：{}", removedInstances.size(), removedInstances);
        log.info("配置发生变化的实例（{}）：{}", metadataUpdates.size(), metadataUpdates);

        // 更新哈希环
        consistentHashing.serverAdd(newInstances);
        consistentHashing.serverRemove(removedInstances);
        consistentHashing.serverMetadataUpdate(metadataUpdates);
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
