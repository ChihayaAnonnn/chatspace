package top.iceclean.chatspace.gateway.loadbalance.hash;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import top.iceclean.chatspace.gateway.utils.RedissonUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsistentHashingByRedisTest {
    Redisson redis;
    ConsistentHashingByRedis hash;
    AmqpTemplate mq;
    List<Instance> nodes;
    List<ServiceInstance> services;

    @BeforeEach void setup() {
        String address = System.getProperty("hash.test.redis");
        Assumptions.assumeTrue(address != null, "需要独立测试 Redis，禁止连接归档中的远端环境");
        Config config = new Config();
        config.setCodec(new StringCodec());
        config.useSingleServer().setAddress(address);
        redis = (Redisson) Redisson.create(config);
        redis.getKeys().flushdb();
        mq = mock(AmqpTemplate.class);
        hash = new ConsistentHashingByRedis(new RedissonUtils(redis), mq);
        nodes = new ArrayList<>(); services = new ArrayList<>();
    }
    @AfterEach void close() { if (redis != null) redis.shutdown(); }
    Instance add(int n, int capacity, String spaces) {
        Instance i = new Instance(); i.setIp("10.77.0." + n); i.setPort(19019);
        Map<String,String> m = new HashMap<>(); m.put("max-user-num", "" + capacity); m.put("access-space", spaces); i.setMetadata(m);
        nodes.add(i); services.add(new DefaultServiceInstance("n"+n, "netty", i.getIp(), i.getPort(), false));
        hash.serverAdd(Collections.singletonList(i)); return i;
    }
    ServiceInstance pick(int uid) { return hash.getInstance(services, ""+uid, "1"); }
    int load(String node) { return Integer.parseInt(redis.getMap("chatspace:server:node:"+node).get("user_num").toString()); }

    @Test void repeatedRoutingDoesNotConsumeAnotherSlot() {
        add(1, 2, "[\"1\"]"); assertNotNull(pick(1)); assertNotNull(pick(1));
        assertEquals(1, load("10.77.0.1:19019")); assertNotNull(pick(2)); assertNull(pick(3));
    }
    @Test void delayedSameNodeCloseCannotReleaseNewConnectionLease() {
        add(1, 2, "[\"1\"]");
        hash.getInstance(services, "1", "1", "old");
        hash.getInstance(services, "1", "1", "new");
        hash.userRemove("10.77.0.1:19019", 1, "old");
        assertEquals(1, load("10.77.0.1:19019"));
        hash.userRemove("10.77.0.1:19019", 1, "new");
        assertEquals(0, load("10.77.0.1:19019"));
    }
    @Test void concurrentAdmissionsNeverExceedCapacityAcrossGateways() throws Exception {
        add(1, 1, "[\"1\"]");
        ConsistentHashingByRedis other = new ConsistentHashingByRedis(new RedissonUtils(redis), mq);
        ExecutorService pool = Executors.newFixedThreadPool(24); CountDownLatch start = new CountDownLatch(1);
        List<Future<ServiceInstance>> futures = new ArrayList<>();
        try {
            for (int n=0;n<24;n++) { final int uid=n+1; futures.add(pool.submit(() -> { start.await(); return (uid%2==0?hash:other).getInstance(services,""+uid,"1"); })); }
            start.countDown(); int admitted=0; for(Future<ServiceInstance> f:futures) if(f.get(20,TimeUnit.SECONDS)!=null)admitted++;
            assertEquals(1,admitted); assertEquals(1,load("10.77.0.1:19019"));
        } finally {pool.shutdownNow();}
    }
    @Test void expansionNotifiesOldOwnersAndPreservesUnaffectedUsers() {
        add(1,10000,"[\"1\"]");
        for(int uid=200000;uid<200100;uid++) {
            redis.getMap("chatspace:user:login:"+uid).put("space_id","1");
            redis.getMap("chatspace:user:login:"+uid).put("server-node","10.77.0.1:19019"); assertNotNull(pick(uid));
        }
        clearInvocations(mq); add(2,10000,"[\"1\"]");
        verify(mq,atLeastOnce()).convertAndSend(eq("node-consumer-10.77.0.1:19019"),any(Object.class));
        int remaining=redis.getScoredSortedSet("chatspace:user:ring").size();
        assertTrue(remaining>0 && remaining<100); assertEquals(remaining,load("10.77.0.1:19019")); assertEquals(0,load("10.77.0.2:19019"));
    }
    @Test void delayedOldNodeCloseCannotDeleteNewAssignment() {
        Instance old=add(1,10000,"[\"1\"]"); assertNotNull(pick(1));
        hash.serverRemove(Collections.singletonList(old)); services.clear(); add(2,10000,"[\"1\"]"); assertNotNull(pick(1));
        hash.userRemove("10.77.0.1:19019",1);
        assertEquals(1,redis.getScoredSortedSet("chatspace:user:ring").size()); assertEquals(1,load("10.77.0.2:19019"));
        hash.userRemove("10.77.0.2:19019",1); hash.userRemove("10.77.0.2:19019",1);
        assertEquals(0,load("10.77.0.2:19019"));
    }
    @Test void removingLiveNodeNotifiesItsUsers() {
        Instance old=add(1,100,"[\"1\"]"); pick(1); clearInvocations(mq);
        hash.serverRemove(Collections.singletonList(old));
        verify(mq).convertAndSend(eq("node-consumer-10.77.0.1:19019"),any(Object.class));
        assertNull(pick(1)); assertEquals(0,redis.getScoredSortedSet("chatspace:user:ring").size());
    }
    @Test void emptyRingAndUnknownSpaceReturnNoNode() {
        assertNull(pick(1)); add(1,100,"[\"2\"]"); assertNull(pick(1));
        assertNotNull(hash.getInstance(services,"1","2"));
    }
    @Test void freshGatewayReconcilesNodesRemovedWhileItWasOffline() {
        add(1, 100, "[\"1\"]");
        Instance live = add(2, 100, "[\"1\"]");
        ConsistentHashingByRedis restarted = new ConsistentHashingByRedis(new RedissonUtils(redis), mq);
        restarted.serverSync(Collections.singletonList(live));
        assertEquals(100, redis.getScoredSortedSet("chatspace:server:ring").size());
        assertFalse(redis.getBucket("chatspace:server:node:10.77.0.1:19019").isExists());
    }
    @Test void removingSpaceNotifiesItsExistingUsers() {
        Instance i=add(1,100,"[\"1\"]"); pick(1); clearInvocations(mq);
        i.getMetadata().put("access-space","[\"2\"]"); hash.serverMetadataUpdate(Collections.singletonList(i));
        verify(mq).convertAndSend(eq("node-consumer-10.77.0.1:19019"),any(Object.class)); assertNull(pick(2));
    }
}
