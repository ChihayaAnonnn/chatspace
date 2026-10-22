# 测试依赖栈（Nacos + PostgreSQL + Redis + RabbitMQ）

给 `chatspace-realtime` / `chatspace-message` 压测用的最小依赖栈，编排文件在仓库根目录：
[`docker-compose.test.yml`](../../docker-compose.test.yml)。

## 启动

```bash
# 可选：改密码
cp deploy/test-stack/.env.example .env

docker compose -f docker-compose.test.yml up -d
docker compose -f docker-compose.test.yml ps
```

首次启动时 PostgreSQL 会自动执行 [`chatspace_pg.sql`](../../chatspace_pg.sql) 建表并写入种子数据。
数据卷非空时不会重复执行，改 SQL 后要重建：

```bash
docker compose -f docker-compose.test.yml down -v
docker compose -f docker-compose.test.yml up -d
```

## 连接信息

服务跑在宿主机上时：

| 组件 | 地址 | 账号 |
|---|---|---|
| PostgreSQL | `127.0.0.1:5432/chat_space_v2` | `postgres` / `postgres` |
| Redis | `127.0.0.1:6379` | 密码 `redispass` |
| RabbitMQ | `127.0.0.1:5672` | `guest` / `guest`，管理台 <http://localhost:15672> |
| Nacos | `127.0.0.1:8848` | 鉴权关闭 |

服务也放进 Compose 时，用容器名访问：`postgres` / `redis` / `rabbitmq` / `nacos`。

对应环境变量：

```bash
export NACOS_IP=127.0.0.1
export HOST_IP=$(你的对外 IP)
export PG_HOST=127.0.0.1 PG_PORT=5432 PG_DB=chat_space_v2 PG_USER=postgres PG_PASSWORD=postgres
```

## 资源配额

| 容器 | 上限 | 说明 |
|---|---|---|
| postgres | 2 CPU / 3G | `shared_buffers=768MB`、`synchronous_commit=off`（压测用） |
| rabbitmq | 1 CPU / 1G | 内存水位 60%，磁盘低于 2GB 时阻塞生产者 |
| redis | 1 CPU / 512M | 关闭持久化，`noeviction`（GEO 数据不能被淘汰） |
| nacos | 1 CPU / 1G | JVM 固定 512m 堆 |

合计上限约 5C/5.5G，空载实际占用约 0.9G。**2C4G 只能做功能验证**，万人压测建议整机 8C16G。

## 两个容易踩的点

1. **Nacos 官方镜像只有 amd64**
   在 Apple Silicon 上靠模拟运行，首次启动要 1~3 分钟；x86 服务器不受影响。文件里已经写了 `platform: linux/amd64`。

2. **RabbitMQ 延时插件**
   `group` 服务的延时队列依赖 `rabbitmq_delayed_message_exchange`，官方镜像不自带，这里通过挂载插件并指定 `enabled_plugins` 解决，无需联网构建。
   插件来源：<https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/tag/v3.13.0>（MPL 2.0）。
   只跑 realtime + message 时可以不要，但 `group` 不在时 realtime 消费的 `box-lock` 队列需要手工创建。

## 清理

```bash
docker compose -f docker-compose.test.yml down       # 保留数据
docker compose -f docker-compose.test.yml down -v    # 连数据一起删
```
