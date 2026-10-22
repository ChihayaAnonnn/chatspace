# 一致性哈希回归测试

仅 `chatspace-netty` 使用自定义一致性哈希，其他服务保持默认负载均衡。网关监听 Nacos 的健康、启用实例，并每 5 秒拉取完整快照对账。每个物理节点有 100 个虚拟节点，哈希键为 `userId-spaceId`，因此同一空间的不同用户可以落到不同节点。

Redis 的 `chatspace:user:route` 记录用户的节点、空间和连接代次。分配、释放及拓扑变更使用同一把分布式锁，避免跨网关的容量检查竞态。旧会话的关闭只能释放匹配自身代次的分配。WebSocket 会话关闭由 `HashWebSocketServiceConfig` 观察，HTTP 升级结束不会提前释放名额。

## 运行测试

使用 JDK 8 和 Maven。哈希测试会清空指定 Redis 的当前数据库，必须提供独立可丢弃实例，禁止使用业务 Redis 或归档远端环境。未提供地址时仅跳过 Redis 集成测试，不能将其当作完整通过。

```sh
docker run --rm -d --name chatspace-hash-test-redis -p 127.0.0.1:16379:6379 redis:7
mvn -pl chatspace-gateway -am clean test -Dhash.test.redis=redis://127.0.0.1:16379
docker stop chatspace-hash-test-redis
```

12 项测试覆盖重复分配、同节点旧代次关闭、两个分配器并发争抢容量、扩容通知与保留、旧节点晚到关闭、节点移除、空环及空间准入、重启后拓扑对账、空间移除、浏览器认证转发、HTTP 认证边界和 HTTP 升级生命周期。

## 真实多节点验证

2026-10-13 在独立 Docker 网络进行了两轮 3→4→3 测试，90 个 WebSocket 客户端全部经过网关。三阶段用户分布为 `40/26/24 → 33/20/15/22 → 40/26/24`。22 个用户迁移，68 个未受影响的连接没有重连；所有归属都与实时节点写入的实际归属一致。关闭客户端后，路由表为空且所有节点计数归零。

测试脚本与原始证据保存在工作区 `interview-review-2026-10-12/chatspace-retest/research/`，入口为 `scale-launch.py`、`scale-probe.py`，结果为 `scale-result.json`、`scale-summary.json`。启动网关和 1、2 号附加节点后启动探针，再启动 3 号附加节点，并用 `scale-command.txt` 切换 `baseline`、`expanded`、`shrunk` 阶段；缩容阶段实际停止 3 号节点。

该结果证明本轮拓扑迁移正确性，不是容量极限或吞吐证明。迁移依赖客户端自动重连，非无缝转移；本轮扩容重连 0.75–0.85 秒，直接停节点后的缩容重连 23.58–30.70 秒，包含注册中心故障发现及重试。`user_num` 是唯一用户路由名额，不等于同账号多开时的物理连接数量。网关进程强制退出导致的遗留名额回收、MQ 发布确认以及更大规模下分布式锁的性能，需要另行完善与测试。
