package top.iceclean.chatspace.gateway.loadbalance.entry;

import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.Getter;
import top.iceclean.chatspace.gateway.constant.NettyServerConst;

import java.util.*;

/**
 * 服务节点信息
 * @author : Ice'Clean
 * @date : 2023-01-24
 */
@Getter
public class ServerEntry {
    /** 节点 IP 地址 */
    private final String ip;
    /** 节点端口 */
    private final Integer port;
    /** 节点最大负载 */
    private Integer maxUserNum;
    /** 节点受理的空间 ID 集合 */
    private Set<Integer> accessSpaces;

    /** 本次更新元数据，变化的受理空间集 */
    private Set<Integer> removeSpaces;
    private Set<Integer> newSpaces;

    /** 当前节点总用户连接数 */
    private Integer userNum;
    /** 拥有的所有虚拟节点的哈希值列表 */
    private List<Integer> nodeHashList;

    public ServerEntry(Instance instance) {
        this.ip = instance.getIp();
        this.port = instance.getPort();
        this.userNum = 0;
        this.accessSpaces = new HashSet<>();
        updateMetadata(instance.getMetadata());
    }

    /** 空间能够受理且没有达到最大负载 */
    public boolean isAccess(int spaceId) {
        return accessSpaces.contains(spaceId) && userNum < maxUserNum;
    }
    /** 空间能够受理且没有达到最大负载，并在可受理执行受理（增加用户数） */
    public boolean access(int spaceId) {
        if (accessSpaces.contains(spaceId) && userNum < maxUserNum) {
            userNum++;
            return true;
        }
        return false;
    }

    /** 有用户离开 */
    public void userRemove() {
        userNum--;
    }

    /**
     * 更新元数据配置，并在受理空间发生改变时返回 true
     * @param metadata 新的元数据配置
     * @return 受理空间集合发生改变时，返回 true
     */
    public boolean updateMetadata(Map<String, String> metadata) {
        // 更新最大用户负载，默认配置为 200
        String newMaxUserNum = metadata.get(NettyServerConst.MAX_USER_NUM);
        this.maxUserNum = newMaxUserNum == null ? 200 : Integer.parseInt(newMaxUserNum);
        // 更新受理空间集
        String newAccessSpace = metadata.get(NettyServerConst.ACCESS_SPACE);
        // 先将本次的变化置空
        this.removeSpaces = Collections.emptySet();
        this.newSpaces = Collections.emptySet();
        if (newAccessSpace == null && !this.accessSpaces.isEmpty()) {
            // 原有的受理空间被移除
            this.removeSpaces = this.accessSpaces;
            this.accessSpaces = new HashSet<>();
            return true;
        }
        Set<Integer> newAccessSpaces = new HashSet<>(JSON.parseArray(newAccessSpace, Integer.class));
        if (!this.accessSpaces.equals(newAccessSpaces)) {
            // 被移除的
            this.removeSpaces = new HashSet<>(accessSpaces);
            this.removeSpaces.removeAll(newAccessSpaces);
            // 新增的
            this.newSpaces = new HashSet<>(newAccessSpaces);
            this.newSpaces.removeAll(accessSpaces);
            // 当前的
            this.accessSpaces.removeAll(removeSpaces);
            this.accessSpaces.addAll(newSpaces);
            return true;
        }
        return false;
    }

    /** 设置节点的虚拟节点哈希列表 */
    public void setNodeHashList(List<Integer> nodeHashList) {
        this.nodeHashList = nodeHashList;
    }

    /** 获取节点 ID */
    public String getServerId() {
        return ip + ":" + port;
    }

    @Override
    public String toString() {
        return "ServerEntry<" + ip +":" + port + "> (" +
                "maxUserNum=" + maxUserNum +
                ", accessSpaces=" + accessSpaces +
                ", userNum=" + userNum + ')';
    }
}
