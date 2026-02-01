package top.iceclean.chatspace.gateway.loadbalance.hash;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;

/**
 * 一致性哈希接口
 * @author : Ice'Clean
 * @date : 2023-02-26
 */
public interface ConsistentHashing {
    /**
     * 使用一致性哈希算法，选择一个节点
     * @param instances 现有所有实例列表
     * @param userId 请求的用户 ID
     * @param spaceId 请求的空间 ID
     * @return 要提供服务的的节点实例
     */
    ServiceInstance getInstance(List<ServiceInstance> instances, String userId, String spaceId);

    /**
     * 服务节点增加
     * @param newInstances 新增的节点实例列表
     */
    void serverAdd(List<Instance> newInstances);

    /**
     * 服务节点移除
     * @param removeInstances 移除的节点实例列表
     */
    void serverRemove(List<Instance> removeInstances);

    /**
     * 服务节点元数据发生改变
     * @param metadataUpdates 元数据发生改变的节点实例列表
     */
    void serverMetadataUpdate(List<Instance> metadataUpdates);

    /**
     * 移除用户
     * @param serverId 服务节点 ID
     * @param userId 用户 ID
     */
    void userRemove(String serverId, Object userId);

    /**
     * 使用的哈希算法，默认为 FNV1_32_HASH
     * @param str 目标字符串
     * @return 计算得到的哈希值
     */
    default int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++) {
            hash = (hash ^ str.charAt(i)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;

        // 如果算出来的值为负数则取其绝对值
        if (hash < 0) {
            hash = Math.abs(hash);
        }
        return hash;
    }
}
