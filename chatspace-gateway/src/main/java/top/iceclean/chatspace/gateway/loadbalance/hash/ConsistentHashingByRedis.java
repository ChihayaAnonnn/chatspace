package top.iceclean.chatspace.gateway.loadbalance.hash;

import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.gateway.common.NodeMessage;
import top.iceclean.chatspace.gateway.constant.NettyServerConst;
import top.iceclean.chatspace.gateway.utils.RedissonUtils;

import java.util.*;

/**
 * Redis 实现的一致性哈希环，用来完成 Gateway 集群的搭建
 * @author : Ice'Clean
 * @date : 2023-02-26
 */
@Slf4j
@Component
public class ConsistentHashingByRedis implements ConsistentHashing{
    /** 虚拟节点个数 */
    private static final int VIRTUAL_NODES = 100;

    /** Redisson 用于操作哈希环 */
    private final RedissonUtils redissonUtils;
    /** RabbitMQ 用于发送用户连接重置消息 */
    private final AmqpTemplate amqpTemplate;

    /** 需要用到的 Redis Key */
    private static final String SERVER_RING = "chatspace:server:ring";
    private static final String USER_RING = "chatspace:user:ring";
    private static final String SERVER_NODE = "chatspace:server:node:";
    /** SERVER_NODE 的 field */
    private static final String NODE_MAX_USERS = "max_user_num";
    private static final String NODE_USERS = "user_num";
    private static final String NODE_SPACE = "access_space";
    private static final String NODE_VNODE = "vnode_list";
    /** 用户 key 和 field */
    private static final String USER_DATA = "chatspace:user:login:";
    private static final String USER_SPACE_ID = "space_session";
    private static final String USER_SERVER_NODE = "server-node";

    /** 内部用的结果集更变 */
    @AllArgsConstructor
    private static class MetadataUpdate {
        /** 是否有更改 */
        private final boolean updated;
        /** 节点受理的空间 ID 集合 */
        private final Set<String> accessSpaces;
        /** 本次更新元数据，变化的受理空间集 */
        private final Set<String> removeSpaces;
        private final Set<String> newSpaces;
    }

    public ConsistentHashingByRedis(RedissonUtils redissonUtils, AmqpTemplate amqpTemplate) {
        this.redissonUtils = redissonUtils;
        this.amqpTemplate = amqpTemplate;
    }

    @Override
    public ServiceInstance getInstance(List<ServiceInstance> instances, String userId, String spaceId) {
        log.info("为（{}, {}）执行一致性哈希算法", userId, spaceId);
        // 对用户 ID 取哈希
        double userHash = getHash(String.join("-", userId, spaceId));

        // 把遍历到的节点存储起来，防止重复遍历虚拟节点
        Set<String> visited = new HashSet<>();

        // 截取环的偏移量
        int offset = 0;
        // 是否需要开始部分
        boolean scanBegin = true;
        // 取出大于 userHash 值的后 VIRTUAL_NODES 个节点，防止节点无法受理时频繁取值
        List<String> list = redissonUtils.sortedGetLargerKey(SERVER_RING, userHash, offset, VIRTUAL_NODES);
        // 遍历到结尾了，尝试遍历开始部分
        if (list.isEmpty()) {
            log.info("哈希环遍历到末尾，尝试查找开始部分");
            scanBegin = false;
            list = redissonUtils.sortedGet(SERVER_RING, 0, userHash);
        }
        while (!list.isEmpty()) {
            // 获取虚拟节点对应的服务节点 ID（虚拟节点使用 # 隔开加上序号）
            String serverId = list.remove(0).split("#")[0];
            if (judgeNode(visited, serverId, spaceId)) {
                // 将用户放入哈希环
                redissonUtils.sortedAdd(USER_RING, userHash, userId);
                // 增加服务节点当前用户数
                redissonUtils.hashIncrBy(SERVER_NODE + serverId, NODE_USERS, 1);
                // 找到受理空间
                return getInstance(instances, serverId);
            }
            log.info("{} 无法受理，继续寻找下一个", serverId);
            // 遍历完了，则再取 VIRTUAL_NODES 个
            if (list.isEmpty() && scanBegin) {
                offset += VIRTUAL_NODES;
                list = redissonUtils.sortedGetLargerKey(SERVER_RING, userHash, offset, VIRTUAL_NODES);
                // 遍历到结尾了，尝试遍历开始部分
                if (list.isEmpty()) {
                    log.info("哈希环遍历到末尾，尝试查找开始部分");
                    scanBegin = false;
                    list = redissonUtils.sortedGet(SERVER_RING, 0, userHash);
                }
            }
        }

        // 否则说明没有任何节点可以受理这个空间，返回空
        log.info("没有任何节点可以受理，返回空");
        return null;
    }

    /**
     * 判断节点是否能受理
     * @param visited 已遍历的集合
     * @param serverId 节点 ID
     * @param spaceId 需求空间 ID
     * @return 是否能受理
     */
    private boolean judgeNode(Set<String> visited, String serverId, String spaceId) {
        if (!visited.contains(serverId)) {
            visited.add(serverId);
            // 找到能受理的节点则退出
            Object nodeSpace = redissonUtils.hashGet(SERVER_NODE + serverId, NODE_SPACE);
            if (nodeSpace != null) {
                List<String> accessSpace = JSON.parseArray(nodeSpace.toString(), String.class);
                if (accessSpace.contains(spaceId)) {
                    return true;
                }
            }
            log.info("{} 无法受理，继续寻找下一个", serverId);
        }
        return false;
    }

    /**
     * 获取服务节点对应的实例
     * @param instances 现有所有实例列表
     * @param serverId 选中的服务节点 ID
     * @return 服务节点对应的实例
     */
    private ServiceInstance getInstance(List<ServiceInstance> instances, String serverId) {
        log.info("接受受理：{}", serverId);
        // 获取 IP 和端口
        String[] info = serverId.split(":");
        String ip = info[0];
        int port = Integer.parseInt(info[1]);
        // 遍历所有实例，找到 IP 和端口都相同的
        for (ServiceInstance instance : instances) {
            if (ip.equals(instance.getHost()) && port == instance.getPort()) {
                return instance;
            }
        }
        // 寻找到目标节点，却在实例中没有找到，说明该服务实际已经下线，补偿一下
        log.error("服务 {} 已无对应实例却仍存在与哈希环，执行自动下线", serverId);
        serverRemove(serverId);
        return null;
    }

    @Override
    public void serverAdd(List<Instance> newInstances) {
        if (newInstances.isEmpty()) {
            return;
        }
        // 创建服务实体
        for (Instance instance : newInstances) {
            String serverId = instance.getIp() + ":" + instance.getPort();
            String serverKey = SERVER_NODE + serverId;
            if (redissonUtils.keyExist(serverKey)) {
                log.error("节点已经存在，添加失败：{}", serverId);
                continue;
            }
            // 设置节点数据，往哈希环中插入虚拟节点并保存他们的哈希值
            List<Integer> vnodeList = addVirtualNode(serverId);
            redissonUtils.hashSet(serverKey, NODE_MAX_USERS, 0);
            redissonUtils.hashSet(serverKey, NODE_VNODE, JSON.toJSONString(vnodeList));
            // 更新元数据
            MetadataUpdate metadataUpdate = updateMetadata(serverKey, instance.getMetadata());

            // 当新的节点能够受理用户连接，则该用户连接需要重置
            userResetHandle(vnodeList, metadataUpdate);
            log.info("节点增加成功：{}", serverId);
        }
    }

    /**
     * 为节点创建虚拟节点
     * @param serverId 节点 ID
     * @return 虚拟节点哈希值列表
     */
    private List<Integer> addVirtualNode(String serverId) {
        List<Integer> nodeHashList = new ArrayList<>(VIRTUAL_NODES);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            String vnodeId = serverId + "#" + i;
            int hash = getHash(vnodeId);
            nodeHashList.add(hash);
            redissonUtils.sortedAdd(SERVER_RING, hash, vnodeId);
        }
        return nodeHashList;
    }

    @Override
    public void serverRemove(List<Instance> removeInstances) {
        if (removeInstances.isEmpty()) {
            return;
        }
        // 简单地将节点从哈希环中去除即可，由客户端进行重连
        removeInstances.forEach(i -> serverRemove(i.getIp() + ":" + i.getPort()));
    }

    /**
     * 通过节点 ID 直接移除节点
     * @param serverId 节点 ID
     */
    private void serverRemove(String serverId) {
        // 获取节点所有的虚拟节点
        Object vnodeObject = redissonUtils.hashGet(SERVER_NODE + serverId, NODE_VNODE);
        if (vnodeObject != null) {
            // 移除掉节点信息
            redissonUtils.delete(SERVER_NODE + serverId);
            // 从哈希环上移除掉该节点的所有虚拟节点
            JSON.parseArray(vnodeObject.toString()).forEach(vnodeId ->
                    redissonUtils.sortedRemove(SERVER_RING, vnodeId));
            log.info("成功移除节点：{}", SERVER_NODE + serverId);
        } else {
            // 没有可移除的
            log.error("没有可移除的节点：{}", SERVER_NODE + serverId);
        }
    }

    @Override
    public void serverMetadataUpdate(List<Instance> metadataUpdates) {
        if (metadataUpdates.isEmpty()) {
            return;
        }
        for (Instance instance : metadataUpdates) {
            String id = instance.getIp() + ":" + instance.getPort();
            String serverKey = SERVER_NODE + id;
            Object vnodeObject = redissonUtils.hashGet(serverKey, NODE_VNODE);
            if (vnodeObject == null) {
                log.error("更新配置的服务节点不存在：{}", id);
                continue;
            }
            // 获取虚拟节点列表
            List<Integer> vnodeList = JSON.parseArray(vnodeObject.toString(), Integer.class);
            // 受理空间发生改变，需要更改用户连接
            MetadataUpdate metadataUpdate = updateMetadata(serverKey, instance.getMetadata());
            if (metadataUpdate.updated) {
                // 处理移除的受理空间
                Set<String> removeSpaces = metadataUpdate.removeSpaces;
                if (!removeSpaces.isEmpty()) {
                    log.info("{} 移除受理空间：{}", id, removeSpaces);
                    // 若用户连接的空间从本节点移除掉了，则该用户连接需要重置
                    userResetHandle(vnodeList, metadataUpdate);
                }
                // 处理新增的受理空间
                Set<String> newSpaces = metadataUpdate.newSpaces;
                if (!newSpaces.isEmpty()) {
                    log.info("{} 新增受理空间：{}", id, newSpaces);
                    // 当用户连接的空间是本节点可以受理了，则用户连接也需要重置
                    userResetHandle(vnodeList, metadataUpdate);
                }
            }
        }
    }

    /**
     * 更新元数据配置，并在受理空间发生改变时返回 true
     * @param serverKey 服务节点键
     * @param metadata 新的元数据配置
     * @return 受理空间集合发生改变时，返回 变化集
     */
    private MetadataUpdate updateMetadata(String serverKey, Map<String, String> metadata) {
        // 更新最大用户负载，默认配置为 200
        String newMaxUserNum = metadata.get(NettyServerConst.MAX_USER_NUM);
        redissonUtils.hashSet(serverKey, NODE_MAX_USERS, newMaxUserNum == null ? 200 : Integer.parseInt(newMaxUserNum));

        // 获取当前受理空间
        Set<String> accessSpaces = new HashSet<>();
        Object nodeSpace = redissonUtils.hashGet(serverKey, NODE_SPACE);
        if (nodeSpace != null) {
            accessSpaces.addAll(JSON.parseArray(nodeSpace.toString(), String.class));
        }

        // 存储变化集
        Set<String> removeSpaces;
        Set<String> newSpaces = Collections.emptySet();

        // 更新受理空间集
        String newAccessSpace = metadata.get(NettyServerConst.ACCESS_SPACE);
        if (newAccessSpace == null && !accessSpaces.isEmpty()) {
            // 原有的受理空间被移除
            removeSpaces = accessSpaces;
            redissonUtils.hashSet(serverKey, NODE_SPACE, "");
            return new MetadataUpdate(true, accessSpaces, removeSpaces, newSpaces);
        }
        Set<String> newAccessSpaces = new HashSet<>(JSON.parseArray(newAccessSpace, String.class));
        if (!newAccessSpaces.equals(accessSpaces)) {
            // 被移除的
            removeSpaces = new HashSet<>(accessSpaces);
            removeSpaces.removeAll(newAccessSpaces);
            // 新增的
            newSpaces = new HashSet<>(newAccessSpaces);
            newSpaces.removeAll(accessSpaces);
            // 当前的
            accessSpaces.removeAll(removeSpaces);
            accessSpaces.addAll(newSpaces);
            redissonUtils.hashSet(serverKey, NODE_SPACE, accessSpaces);
            return new MetadataUpdate(true, accessSpaces, removeSpaces, newSpaces);
        }
        return new MetadataUpdate(false, accessSpaces, null, null);
    }

    /**
     * 扫描指定节点的所有需要重置连接的候选用户
     * 即所有虚拟节点到前一个结点的所有用户
     * 并根据元数据更新情况筛选出需要处理的用户（新增结点、元数据更改）
     * @param vnodeList 目标服务结点的虚拟节点列表
     * @param metadataUpdate 元数据更新情况
     */
    private void userResetHandle(List<Integer> vnodeList, MetadataUpdate metadataUpdate) {
        // 收集需要更改的用户 ID
        List<String> resetUserList = new ArrayList<>();
        vnodeList.forEach(hash -> {
            // 找到该虚拟节点的前一个结点
            Double beforeHash = redissonUtils.sortedGetBeforeScore(SERVER_RING, hash);
            // 收集要移除的用户连接
            List<String> removeUserList;
            if (beforeHash == null) {
                // 如果前一个结点为 null，则说明该结点处在最开始的那段，最后一个键的尾部和到该键的头部是需要搜索的范围
                double lastServerHash = redissonUtils.sortedGetLastScore(SERVER_RING);
                removeUserList = redissonUtils.sortedGet(USER_RING, lastServerHash, Double.MAX_VALUE);
                removeUserList.addAll(redissonUtils.sortedGet(USER_RING, 0, hash));
            } else {
                // 否则，beforeHash 和 hash 为要搜索的范围
                removeUserList = redissonUtils.sortedGet(USER_RING, beforeHash, hash);
            }

            // 移除掉不需要更改的
            removeUserList.removeIf(userId -> {
                // 获取用户所在空间
                Object spaceId = redissonUtils.hashGet(USER_DATA + userId, USER_SPACE_ID);
                return !(spaceId != null && metadataUpdate.accessSpaces.contains(spaceId.toString()));
            });
            // 收集起来准备通知，并从用户环中移除这些用户
            resetUserList.addAll(removeUserList);
            removeUserList.forEach(this::userRemove);
            redissonUtils.sortedRemoveAll(USER_RING, removeUserList);
        });
        // 发送用户连接更变消息到消息队列，由各个节点自行处理
        log.info("需要更改连接的用户：{} = {}", resetUserList.size(), resetUserList);
        for (String userId : resetUserList) {
            Object node = redissonUtils.hashGet(USER_DATA, USER_SERVER_NODE);
            if (node != null) {
                amqpTemplate.convertAndSend("node-consumer-" + node.toString(),
                        JSON.toJSONString(new NodeMessage(NodeMessage.Type.USER_RESET, Integer.parseInt(userId), null)));
            }
        }

    }

    @Override
    public void userRemove(Object userId) {
        if (redissonUtils.sortedRemove(USER_RING, userId)) {
            log.info("用户 {} 从哈希环中移除", userId);
            Object node = redissonUtils.hashGet(USER_DATA + userId, USER_SERVER_NODE);
            redissonUtils.hashIncrBy(SERVER_NODE + node, NODE_USERS, -1);
            if (node == null) {
                log.warn("找不到用户所在节点的记录：userId={}", userId);
            }
            return;
        }
        log.warn("用户哈希环中找不到记录：userId={}", userId);
    }
}
