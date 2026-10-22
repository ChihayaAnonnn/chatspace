package top.iceclean.chatspace.gateway.loadbalance.hash;

import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.gateway.common.NodeMessage;
import top.iceclean.chatspace.gateway.utils.RedissonUtils;
import java.util.*;
import java.util.function.Supplier;

/** Redis 共享哈希环；连接分配、释放和拓扑变更遵循同一把分布式锁。 */
@Slf4j
@Component
public class ConsistentHashingByRedis implements ConsistentHashing {
    public static final String UPDATE_LOCK = "chatspace:server:route:lock";
    private static final int VIRTUAL_NODES = 100;
    private static final String SERVER_RING = "chatspace:server:ring";
    private static final String USER_RING = "chatspace:user:ring";
    private static final String ASSIGNMENTS = "chatspace:user:route";
    private static final String SERVER_NODE = "chatspace:server:node:";
    private final RedissonUtils utils;
    private final AmqpTemplate amqp;

    public ConsistentHashingByRedis(RedissonUtils utils, AmqpTemplate amqp) {
        this.utils = utils; this.amqp = amqp;
    }
    private <T> T locked(Supplier<T> action) {
        RLock lock = utils.redisson.getLock(UPDATE_LOCK);
        lock.lock();
        try { return action.get(); } finally { lock.unlock(); }
    }
    private Map<String,String> assignment(String uid) {
        Object value = utils.hashGet(ASSIGNMENTS, uid);
        return value == null ? null : JSON.parseObject(value.toString(), new com.alibaba.fastjson2.TypeReference<Map<String,String>>() {});
    }
    private void save(String uid, String node, String space, String lease) {
        Map<String,String> data = new HashMap<>(); data.put("node",node); data.put("space",space); data.put("lease",lease);
        utils.hashSet(ASSIGNMENTS,uid,JSON.toJSONString(data));
        utils.sortedAdd(USER_RING,getHash(uid+"-"+space),uid);
    }
    private Set<String> spaces(String node) {
        Object value=utils.hashGet(SERVER_NODE+node,"access_space");
        return value==null ? Collections.emptySet() : new HashSet<>(JSON.parseArray(value.toString(),String.class));
    }
    private int number(String node,String field) {
        Object value=utils.hashGet(SERVER_NODE+node,field); return value==null?0:Integer.parseInt(value.toString());
    }
    private void increment(String node,int amount) {
        if(utils.keyExist(SERVER_NODE+node)) utils.hashSet(SERVER_NODE+node,"user_num",Math.max(0,number(node,"user_num")+amount));
    }
    private List<String> ring(String uid,String space) {
        double score=getHash(uid+"-"+space);
        List<String> after=new ArrayList<>(),before=new ArrayList<>();
        for(ScoredEntry<String> entry:utils.redisson.<String>getScoredSortedSet(SERVER_RING).entryRange(0,-1)) {
            String node=entry.getValue().split("#")[0];
            (entry.getScore()>score?after:before).add(node);
        }
        after.addAll(before); return new ArrayList<>(new LinkedHashSet<>(after));
    }
    private String preferred(String uid,String space,Set<String> available,String current) {
        for(String node:ring(uid,space)) {
            if(available!=null && !available.contains(node)) continue;
            if(spaces(node).contains(space) && (node.equals(current) || number(node,"user_num")<number(node,"max_user_num"))) return node;
        }
        return null;
    }
    @Override public ServiceInstance getInstance(List<ServiceInstance> instances,String uid,String space) {
        return getInstance(instances,uid,space,"");
    }
    @Override public ServiceInstance getInstance(List<ServiceInstance> instances,String uid,String space,String lease) {
        return locked(() -> {
            Map<String,ServiceInstance> available=new HashMap<>();
            instances.forEach(i->available.put(i.getHost()+":"+i.getPort(),i));
            Map<String,String> old=assignment(uid);
            // 已有合法归属保持粘性，重复握手仅更新连接代次，不重复消耗名额。
            if(old!=null && space.equals(old.get("space")) && available.containsKey(old.get("node")) && spaces(old.get("node")).contains(space)) {
                save(uid,old.get("node"),space,lease); return available.get(old.get("node"));
            }
            if(old!=null) release(uid,old.get("node"),null);
            String node=preferred(uid,space,available.keySet(),null);
            if(node==null) return null;
            save(uid,node,space,lease); increment(node,1); log.debug("分配 uid={} node={} lease={}",uid,node,lease); return available.get(node);
        });
    }
    private void release(String uid,String node,String lease) {
        Map<String,String> owner=assignment(uid);
        if(owner==null || !node.equals(owner.get("node")) || (lease!=null && !lease.equals(owner.get("lease")))) return;
        log.debug("释放 uid={} node={} lease={}",uid,node,lease);
        utils.hashDel(ASSIGNMENTS,uid); utils.sortedRemove(USER_RING,uid); increment(node,-1);
    }
    @Override public void userRemove(String node,Object uid) {
        locked(()->{release(uid.toString(),node,null);return null;});
    }
    @Override public void userRemove(String node,Object uid,String lease) {
        locked(()->{release(uid.toString(),node,lease);return null;});
    }
    /** 先把通知发给真实旧归属，再释放名额；发布抛异常时保留归属供下次对账。 */
    private void reset(String uid,Map<String,String> owner) {
        amqp.convertAndSend("node-consumer-"+owner.get("node"),JSON.toJSONString(new NodeMessage(NodeMessage.Type.USER_RESET,Integer.parseInt(uid),null)));
        release(uid,owner.get("node"),null);
    }
    private void rebalance() {
        for(Object key:new ArrayList<>(utils.hashGetAll(ASSIGNMENTS).keySet())) {
            String uid=key.toString(); Map<String,String> owner=assignment(uid); if(owner==null)continue;
            String preferred=preferred(uid,owner.get("space"),null,owner.get("node"));
            if(!owner.get("node").equals(preferred)) reset(uid,owner);
        }
    }
    private void metadata(Instance i) {
        String key=SERVER_NODE+i.getIp()+":"+i.getPort();
        String raw=i.getMetadata().get("access-space");
        utils.hashSet(key,"access_space",raw==null?"[]":JSON.toJSONString(JSON.parseArray(raw,String.class)));
        utils.hashSet(key,"max_user_num",Integer.parseInt(i.getMetadata().getOrDefault("max-user-num","200")));
    }
    @Override public void serverAdd(List<Instance> instances) {
        locked(()->{
            for(Instance i:instances) {
                String node=i.getIp()+":"+i.getPort();
                if(!utils.keyExist(SERVER_NODE+node)) {
                    utils.hashSet(SERVER_NODE+node,"user_num",0);
                    for(int n=0;n<VIRTUAL_NODES;n++)utils.sortedAdd(SERVER_RING,getHash(node+"#"+n),node+"#"+n);
                }
                metadata(i);
            }
            if(!instances.isEmpty())rebalance(); return null;
        });
    }
    @Override public void serverSync(List<Instance> instances) {
        locked(() -> {
            Set<String> current = new HashSet<>();
            instances.forEach(i -> current.add(i.getIp() + ":" + i.getPort()));
            Set<String> stored = new HashSet<>();
            utils.redisson.<String>getScoredSortedSet(SERVER_RING).readAll()
                    .forEach(v -> stored.add(v.split("#")[0]));
            List<Instance> removed = new ArrayList<>();
            for (String node : stored) {
                if (!current.contains(node)) {
                    String[] address = node.split(":");
                    Instance i = new Instance(); i.setIp(address[0]); i.setPort(Integer.parseInt(address[1])); removed.add(i);
                }
            }
            serverRemove(removed);
            serverAdd(instances);
            return null;
        });
    }
    @Override public void serverRemove(List<Instance> instances) {
        locked(()->{
            Set<String> removed=new HashSet<>();
            for(Instance i:instances) {
                String node=i.getIp()+":"+i.getPort(); removed.add(node);
                for(int n=0;n<VIRTUAL_NODES;n++)utils.sortedRemove(SERVER_RING,node+"#"+n);
            }
            for(Object key:new ArrayList<>(utils.hashGetAll(ASSIGNMENTS).keySet())) {
                String uid=key.toString();Map<String,String> owner=assignment(uid);
                if(owner!=null && removed.contains(owner.get("node")))reset(uid,owner);
            }
            removed.forEach(node->utils.delete(SERVER_NODE+node));return null;
        });
    }
    @Override public void serverMetadataUpdate(List<Instance> instances) {
        locked(()->{instances.forEach(this::metadata);if(!instances.isEmpty())rebalance();return null;});
    }
}
