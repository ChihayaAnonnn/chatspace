# ChatSpace 测试环境 Handoff

> 交接时间：2026-10-22
> 目的：把 chatspace 的数据层从 MySQL 切到 PostgreSQL，让 `chatspace-realtime` 与 `chatspace-message` 能在另一台机器上跑高并发测试。
> 阅读顺序：第 1 节（改了什么）→ 第 3 节（怎么起）→ 第 5 节（已验证/未验证）→ 第 6 节（压测前必须做的）。

---

## 1. 本次改动

### 1.1 PostgreSQL 迁移（本次新增）

| 文件 | 改动 | 原因 |
|---|---|---|
| `chatspace_pg.sql` | **新增**，完整 PG DDL + 种子数据 | MySQL 脚本无法直接在 PG 执行，详见第 2 节 |
| `chatspace-infrastructure/src/main/resources/application-shared.yml` | 数据源切 PG；`filters: [stat,wall]` → `[stat]` | `wall` 对 PG 的 `->>` 等语法支持不完整，压测期间先关 |
| `chatspace-infrastructure/pom.xml` | 新增 `org.postgresql:postgresql`（runtime） | 原来只有 MySQL 驱动 |
| `.../infrastructure/config/MybatisPlusConfig.java` | `DbType.MYSQL` → `DbType.POSTGRE_SQL` | 否则分页生成 `LIMIT ?,?`，PG 语法错误 |
| `.../infrastructure/po/Site.java` | `IdType.AUTO` → `IdType.INPUT` | `t_site.id` 由应用写入（等于 `userId`），交给自增会生成错误 ID |
| `.../group/mapper/box/BoxItemMapper.java` | `item_extend->>'$.name'` → `->>'name'`；3 处 `box_item.id` → `box_item.box_item_id` | MySQL JSON Path / 不存在的列名，PG 下必然失败 |

### 1.2 网关一致性哈希（仓库原有未提交工作，一并提交）

`chatspace-gateway` 下 7 个改动文件 + 3 个新增文件（`HashReservationFilter`、`HashWebSocketServiceConfig`、`README-hash-tests.md`）与 `src/test/`。
这部分不是本次 PG 迁移的内容，但属于同一工作区状态，为了让另一台机器拿到一致代码，单独作为一个 commit 提交。

### 1.3 未提交的内容

`*/target/` 下的 321 个编译产物没有提交（`.gitignore` 已忽略，但历史上被 track 过，所以 `git status` 里仍会显示脏）。
到新机器后直接 `mvn package` 重新生成即可，不要依赖仓库里的旧 class。

---

## 2. 数据库

### 2.1 版本要求

**PostgreSQL 11+**。用到了 `bigserial` 和 `EXECUTE FUNCTION` 触发器（PG 11 起支持）。

### 2.2 初始化

```bash
createdb chat_space_v2
psql -v ON_ERROR_STOP=1 -d chat_space_v2 -f chatspace_pg.sql
```

脚本自带事务，中途失败会整体回滚，不会留半套表。

### 2.3 与 MySQL 版的关键差异

| 项 | MySQL 版 | PG 版 |
|---|---|---|
| 消息表 | `t_session_message` | **`t_message`**（代码 `@TableName("t_message")`） |
| 主键 | `int auto_increment` | `bigserial` |
| 时间 | `timestamp` | `timestamptz` |
| 列注释 | 内联 `comment` | `COMMENT ON COLUMN` |
| 自动更新时间 | `ON UPDATE CURRENT_TIMESTAMP` | `BEFORE UPDATE` 触发器 |
| JSON | `json` | `jsonb` |
| 索引名 | 表级唯一（两处都叫 `user_id_index`） | 模式级唯一，已重命名为 `idx_*` |
| 外键 | 无 | 无（保持一致，压测少一层约束） |

### 2.4 种子数据（必须保留）

- 7 条 `t_meta_item` + 7 条 `t_item`
- **0 号空间**（`t_space.id = 0`，20×20）：realtime 初始化位置时会调 `groupClient.getSpaceData(0)`，缺这行会 NPE
- 测试宝箱与 10 条箱内物品

### 2.5 脚本末尾的可选段

- **A. 压测数据**：`generate_series` 批量造 1 万用户、公共空间会话
- **B. 调优建议**：`synchronous_commit=off`、`t_site` 的 `fillfactor=70`、`t_message` 的 autovacuum 阈值
- **C. openGauss 兼容说明**

默认全部注释，按需放开。

### 2.6 关于索引的一个选择

`(session_id, msg_id)` 默认建的是**普通索引**，不是唯一索引。

原因：当前 `MessageServiceImpl.saveMessage` 用 `max(msg_id)+1` 生成序号，并发下会产生重复。如果加唯一约束，消费端抛 23505 后 Spring AMQP 会无限重投，压测可能被消息风暴拖垮。
**要验证这个并发缺陷时**，先给消费端加异常捕获，再启用脚本里注释掉的那行唯一索引。

---

## 3. 配置与环境

### 3.1 环境变量

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `NACOS_IP` | ✅ | 无 | `bootstrap.yml` 里没有默认值，缺了启动直接失败 |
| `HOST_IP` | ✅ | 无 | 注册到 Nacos 的 IP；同时决定 `node-consumer-<ip>:19019` 队列名 |
| `PG_HOST` | | `127.0.0.1` | |
| `PG_PORT` | | `5432` | |
| `PG_DB` | | `chat_space_v2` | |
| `PG_USER` | | `postgres` | |
| `PG_PASSWORD` | | `postgres` | |

### 3.2 必须修改的配置项

| 配置 | 默认值 | 为什么必须改 |
|---|---|---|
| `chatspace.websocket.register.max-user-num` | **300** | 单节点只收 300 人，1 万连接需要 34 个节点 |
| `chatspace.websocket.register.access-space` | **[1, 2, 5]** | 只受理 1/2/5 号空间；而种子数据只有 0 号。走网关会判“无可用节点”，直连 Netty 不受限 |

### 3.3 JDK

必须 **JDK 8**。`chatspace-realtime` 的 `MessageReceiver.java` 有一个未使用的 `import javax.xml.soap.Node`，JDK 11+ 已移除该包，编译直接失败。用高版本 JDK 的话删掉这行即可。

---

## 4. 依赖组件与启动顺序

### 4.1 依赖

| 组件 | realtime | message | 说明 |
|---|---|---|---|
| PostgreSQL 11+ | ✅ | ✅ | |
| Redis 5+ | ✅ | ✅ | message 通过 `UserCache` 依赖 Redis，不是可选项 |
| RabbitMQ 3.x | ✅ | ✅ | 延时插件只有 group 需要 |
| Nacos | ✅ | ✅ | 配置 + 发现；realtime 还会手动注册 `chatspace-netty` |
| group 服务 | ✅ 启动强依赖 | ❌ | realtime `main` 里先跑 `zoneInit()`，group 不在就起不来 |
| user 服务 | ✅ 握手强依赖 | ❌ | 握手时 `changeOnline` 取用户 |
| friend / session | ✅ 按需 | ✅ 按需 | message 需要 session 解析 `sessionId` |

### 4.2 端口

| 服务 | 端口 |
|---|---|
| realtime | 19018（HTTP）、**19019**（Netty WS）、10018（actuator） |
| message | 19014、10014 |
| user | 19011 |
| group | 19013 |

### 4.3 启动顺序

```
PG / Redis / RabbitMQ / Nacos
        ↓
user (19011) + group (19013)     ← realtime 启动前必须就绪
        ↓
message (19014)
        ↓
realtime (19018/19019)
```

### 4.4 RabbitMQ 队列

| 队列 | 声明方 | 说明 |
|---|---|---|
| `new-message` | message 自建 | 落库入口 |
| `node-consumer-<ip>:19019` | realtime 自建 | 跨节点投递 |
| `session-request` / `zone_created` | realtime 自建 | |
| `box-lock` | **group**（延时交换机绑定） | realtime 用 `queues=` 消费，队列不存在会持续报错重试。只跑 realtime+message 时，手工建一个空的 `box-lock` 队列，或忽略日志 |

---

## 5. 已验证 / 未验证

### 5.1 已验证（本机真实环境）

环境：Docker + PostgreSQL 16 + Redis 7 + RabbitMQ 3.13（Nacos 未起，用 simple discovery 替代）。

| 项 | 结果 |
|---|---|
| 编译 | JDK 8 下 11/11 模块 `BUILD SUCCESS`，realtime 全量重编译 37 个源文件 |
| group 启动 | `Started ... in 6.128s`；`GET /space/space/list` 返回 PG 里的 0 号空间 |
| JSON 查询 | `GET /space/zone/box/item/detail/1` 返回 `aliasName=限量皮肤`，证明 `->>'name'` 生效 |
| realtime 启动 | `Started ... in 6.062s`，Netty 监听 19019 |
| WS 握手 | `101 Switching Protocols`，收到 `SPACE_INIT`（含 0 号空间与用户位置） |
| `t_site` 主键 | 写入行 `id = 9001`（等于 userId），证明 `IdType.INPUT` 生效 |

### 5.2 未验证 / 已知问题

1. **位置更新缺字段会 NPE**
   发 `{"x":7,"y":8}` 时 `GeoCache.batchGetCover` 在 `GeoCache.java:119` 抛空指针（`vision` 拆箱）。
   压测脚本的 `SITE_UPDATE` 必须带 `vision` 和 `exist`，例如：
   ```json
   {"wsType":"SITE_UPDATE","wsContent":{"x":7,"y":8,"vision":4,"exist":2}}
   ```

2. **断开时未观察到位置落库**
   会话内存里的坐标已更新为 `x=7,y=8`，但 `t_site` 仍是旧值，日志里也没有进入下线处理分支。
   可能是测试环境导致（缺 friend/message/session 服务、同账号反复连接触发强制下线），也可能是 `channelInactive` 分支问题。
   **正式压测前请手工确认一次：连上 → 移动 → 断开 → 查 `t_site`。**

3. **MyBatis-Plus 分页方言未运行时验证**
   group 没有分页接口。第一个真正用到 `DbType.POSTGRE_SQL` 的是 message 的历史消息分页，起 message 后留意 SQL 是否为 `LIMIT ? OFFSET ?`。

4. **Nacos 未参与验证**
   本次用 `spring.cloud.discovery.client.simple.instances.*` 直接指定 group/user 地址，并关闭了自动注册。
   realtime 在 Nacos 不可用时**不会启动失败**：注册异常被捕获，Netty 仍会绑定端口（日志有 `Netty 服务注册失败`）。

5. **`t_message` 的 `msg_id` 并发竞态仍然存在**
   `max(msg_id)+1` 是“先查后插”，多实例消费必然重复。默认索引不是唯一索引，压测时不会报错，但数据会重复。
   这是代码缺陷，不是本次迁移引入的。

6. **`target/` 未提交**，到新机器必须重新构建。

### 5.3 压测脚本需要注意的鉴权细节

WS 握手用子协议传 token 和空间：

```
Sec-WebSocket-Protocol: <jwt>,<spaceId>
```

但**光有合法 JWT 不够**：`ChatHandler.checkToken` 会拿 Redis 里的 token 比对，不一致就当“异地登录”强制下线。Redis 键是：

```
HSET chatspace:user:login:<userId> token <jwt>
```

正常客户端由 user 服务登录时写入；压测脚本如果绕过登录，必须自己把 token 写进这个 hash，否则发出的消息会被静默丢弃。

JWT 签名密钥在 `JwtUtils.generalKey()`，HS256，key 为固定字节数组。

---

## 6. 压测前 Checklist

- [ ] 调大 `max-user-num`（例如 20000），或明确只在单节点内测试
- [ ] 确认 `access-space` 与测试空间一致（当前只放行 1/2/5，种子空间是 0）
- [ ] 用户坐标**分散**，不要都落在 `(10,10)`：视野 4 / 存在感 2 会让同空间用户互相可见，1 万人同点会产生 O(n²) 广播
- [ ] `ulimit -n` ≥ 65535（1 万 WebSocket 连接 = 1 万 fd）
- [ ] PG：`shared_buffers` 调到内存的 25%，测试环境可 `synchronous_commit=off`
- [ ] `t_site` 设置 `fillfactor=70`（一行被反复 UPDATE，提高 HOT 更新命中率）
- [ ] 确认 `box-lock` 队列存在，或接受 realtime 的报错日志
- [ ] 压测脚本的 `SITE_UPDATE` 带上 `vision` / `exist`
- [ ] 决定 `msg_id` 唯一约束策略（默认不加，加了要先补消费端异常捕获）

---

## 7. 回滚到 MySQL

1. `application-shared.yml` 里四个数据源字段换回 MySQL（`chatspace.sql` 仍在仓库）
2. `MybatisPlusConfig` 改回 `DbType.MYSQL`
3. `Site.java` 改回 `IdType.AUTO`
4. `BoxItemMapper` 的 JSON 语法与列名改回（不推荐，`box_item.id` 本来就是错的）

---

## 8. 参考启动命令

```bash
# group
java -jar chatspace-group/target/chatspace-group-1.0.jar \
  --NACOS_IP=<nacos-ip> \
  --PG_HOST=<pg-ip> --PG_PORT=5432 --PG_USER=postgres --PG_PASSWORD=<pwd> \
  --spring.redis.host=<redis-ip> --spring.redis.password=<pwd> \
  --spring.rabbitmq.host=<mq-ip> --spring.rabbitmq.username=guest --spring.rabbitmq.password=guest

# realtime
java -jar chatspace-realtime/target/chatspace-realtime-1.0.jar \
  --NACOS_IP=<nacos-ip> --HOST_IP=<本机对外 IP> \
  --PG_HOST=<pg-ip> ...（同上）
```

容器化时注意：`network_mode: host` 或保证 `REGISTER_PORT=19019` 可达，否则 `node-consumer-<ip>:19019` 队列会在跨节点投递时失效。
