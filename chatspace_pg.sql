-- =============================================================================
-- ChatSpace · PostgreSQL Schema
-- -----------------------------------------------------------------------------
-- 由 chatspace.sql（MySQL 8）转换而来，并对照 Java 实体（infrastructure/po）修正
-- 目标版本：PostgreSQL 12+（openGauss 3.x 见文末）
--
-- 用法：
--   createdb chat_space_v2
--   psql -v ON_ERROR_STOP=1 -d chat_space_v2 -f chatspace_pg.sql
--
-- 与 MySQL 版的关键差异：
--   1. t_session_message -> t_message（代码 @TableName("t_message")，原 SQL 已漂移）
--   2. int auto_increment  -> bigserial
--   3. timestamp           -> timestamptz（MySQL TIMESTAMP 本身就是 UTC + 会话时区）
--   4. 列内联 comment      -> COMMENT ON COLUMN
--   5. on update CURRENT_TIMESTAMP -> BEFORE UPDATE 触发器
--   6. json                -> jsonb
--   7. 索引 user_id_index 重名（MySQL 表级唯一）-> PG 模式级唯一，已重命名
--   8. 补 t_user.email（PO 有该字段，MySQL 脚本缺失）
--   9. 补 t_zone.access_key（MySQL 有，openGauss 版缺失）
--  10. 热点表（t_message / t_site）按应用实际写入收紧为 NOT NULL
--  11. 不建外键（MySQL 版也没有；压测时少一层约束开销）
-- =============================================================================

-- 需要清库重来时：DROP SCHEMA public CASCADE; CREATE SCHEMA public;

BEGIN;

-- -----------------------------------------------------------------------------
-- 通用：update_time 自动维护（替代 MySQL 的 ON UPDATE CURRENT_TIMESTAMP）
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_update_time() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.update_time := now();
    RETURN NEW;
END $$;

-- -----------------------------------------------------------------------------
-- 1. 用户
-- -----------------------------------------------------------------------------
CREATE TABLE t_user (
    id          bigserial    PRIMARY KEY,
    user_name   varchar(20),
    user_pass   varchar(255),
    sex         varchar(10),
    nick_name   varchar(20),
    avatar      varchar(255),
    email       varchar(50),
    box_id      bigint,
    freeze_time timestamptz,
    delete_time timestamptz,
    create_time timestamptz  DEFAULT now(),
    update_time timestamptz  DEFAULT now()
);
COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.id IS '用户 ID';
COMMENT ON COLUMN t_user.user_name IS '用户名（区分大小写，PG 默认行为即区分）';
COMMENT ON COLUMN t_user.user_pass IS '用户密码';
COMMENT ON COLUMN t_user.sex IS '用户性别';
COMMENT ON COLUMN t_user.nick_name IS '用户昵称';
COMMENT ON COLUMN t_user.avatar IS '用户头像';
COMMENT ON COLUMN t_user.email IS '邮箱';
COMMENT ON COLUMN t_user.box_id IS '用户的随身空间箱 ID';
COMMENT ON COLUMN t_user.freeze_time IS '用户冻结的时间';
COMMENT ON COLUMN t_user.delete_time IS '用户注销的时间';
COMMENT ON COLUMN t_user.create_time IS '账号创建时间';
COMMENT ON COLUMN t_user.update_time IS '修改时间';

-- -----------------------------------------------------------------------------
-- 2. 好友
-- -----------------------------------------------------------------------------
CREATE TABLE t_friend (
    map_id      bigserial   PRIMARY KEY,
    friend_id   bigint,
    user_id     bigint,
    to_user_id  bigint,
    last_msg_id bigint      DEFAULT 0,
    mark_name   varchar(20),
    delete_time timestamptz,
    create_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_friend IS '好友表';

-- -----------------------------------------------------------------------------
-- 3. 群组
-- -----------------------------------------------------------------------------
CREATE TABLE t_group (
    id          bigserial   PRIMARY KEY,
    creator_id  bigint,
    group_type  integer     DEFAULT 0,
    group_name  varchar(20),
    avatar      varchar(255),
    number      integer,
    delete_time timestamptz,
    create_time timestamptz DEFAULT now(),
    update_time timestamptz DEFAULT now() NOT NULL
);
COMMENT ON TABLE t_group IS '群组表（group_type=0 用户创建，系统维护的有传送门、信箱等）';

-- -----------------------------------------------------------------------------
-- 4. 用户-群组
-- -----------------------------------------------------------------------------
CREATE TABLE t_user_group (
    map_id      bigserial   PRIMARY KEY,
    user_id     bigint,
    group_id    bigint,
    last_msg_id bigint      DEFAULT 0,
    mark_name   varchar(20),
    delete_time timestamptz,
    create_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_user_group IS '用户群聊表';

-- -----------------------------------------------------------------------------
-- 5. 会话
-- -----------------------------------------------------------------------------
CREATE TABLE t_session (
    id          bigserial   PRIMARY KEY,
    type        integer     NOT NULL,
    target_id   bigint      NOT NULL,
    delete_time timestamptz,
    create_time timestamptz DEFAULT now(),
    update_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_session IS '会话表';
COMMENT ON COLUMN t_session.type IS '会话类型（0-私聊，1-群组，2-区域，3-空间）';
COMMENT ON COLUMN t_session.target_id IS '目标 ID（好友映射 ID / 群组 ID / 区域 ID / 空间 ID）';

-- -----------------------------------------------------------------------------
-- 6. 消息（原 chatspace.sql 中名为 t_session_message，与代码不一致）
-- -----------------------------------------------------------------------------
CREATE TABLE t_message (
    id          bigserial    PRIMARY KEY,
    session_id  bigint       NOT NULL,
    msg_id      bigint       NOT NULL,
    sender_id   bigint       NOT NULL,
    type        integer      NOT NULL DEFAULT 0,
    content     varchar(255) NOT NULL DEFAULT '',
    create_time timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE t_message IS '会话聊天记录表（msg_id 为会话内序号，由 message 服务计算）';
COMMENT ON COLUMN t_message.session_id IS '所属会话 ID';
COMMENT ON COLUMN t_message.msg_id IS '会话内消息序号';
COMMENT ON COLUMN t_message.sender_id IS '消息发送者 ID';
COMMENT ON COLUMN t_message.type IS '消息类型（0-文本，1-图片，2-视频）';
COMMENT ON COLUMN t_message.content IS '消息内容';

-- -----------------------------------------------------------------------------
-- 7. 会话申请
-- -----------------------------------------------------------------------------
CREATE TABLE t_session_request (
    id          bigserial   PRIMARY KEY,
    type        integer,
    sender_id   bigint,
    target_id   bigint,
    req_src     varchar(255),
    req_remark  varchar(255),
    req_status  integer     DEFAULT 0,
    create_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_session_request IS '会话申请表';
COMMENT ON COLUMN t_session_request.req_status IS '请求状态（0 未读，1 已读，2 接受，3 拒绝）';

-- -----------------------------------------------------------------------------
-- 8. 空间
-- -----------------------------------------------------------------------------
CREATE TABLE t_space (
    id          bigserial    PRIMARY KEY,
    space_name  varchar(40),
    size_x      integer,
    size_y      integer,
    create_time timestamptz  DEFAULT now()
);
COMMENT ON TABLE t_space IS '空间元数据表';

-- -----------------------------------------------------------------------------
-- 9. 用户位置（实时服务高频读写）
--    id 由应用显式写入（等于 userId），因此不设默认值，缺失时立即报错而非静默自增
-- -----------------------------------------------------------------------------
CREATE TABLE t_site (
    id          bigint      PRIMARY KEY,
    space_id    integer     NOT NULL DEFAULT 0,
    x           integer     NOT NULL DEFAULT 0,
    y           integer     NOT NULL DEFAULT 0,
    vision      integer     NOT NULL DEFAULT 4,
    exist       integer     NOT NULL DEFAULT 2,
    last_time   timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE t_site IS '用户位置表（高频更新，见文末 fillfactor 建议）';
COMMENT ON COLUMN t_site.id IS '位置 ID，同时也是用户 ID';
COMMENT ON COLUMN t_site.space_id IS '所在空间 ID';
COMMENT ON COLUMN t_site.x IS 'x 坐标';
COMMENT ON COLUMN t_site.y IS 'y 坐标';
COMMENT ON COLUMN t_site.vision IS '视野大小';
COMMENT ON COLUMN t_site.exist IS '存在感大小';
COMMENT ON COLUMN t_site.last_time IS '最后一次位置更新时间';

-- -----------------------------------------------------------------------------
-- 10. 区域
-- -----------------------------------------------------------------------------
CREATE TABLE t_zone (
    id          bigserial   PRIMARY KEY,
    group_id    bigint,
    space_id    bigint,
    zone_pid    bigint      DEFAULT 0,
    zone_rid    bigint      DEFAULT 0,
    zone_name   varchar(40),
    zone_type   integer,
    start_x     integer,
    start_y     integer,
    end_x       integer,
    end_y       integer,
    create_time timestamptz DEFAULT now(),
    delete_time timestamptz,
    access_key  varchar(40)
);
COMMENT ON TABLE t_zone IS '区域元数据表';
COMMENT ON COLUMN t_zone.zone_pid IS '父级区域 ID（无则为 0）';
COMMENT ON COLUMN t_zone.zone_rid IS '根级区域 ID（无则为 0）';
COMMENT ON COLUMN t_zone.access_key IS '进入区域需持有的 key';

-- -----------------------------------------------------------------------------
-- 11. 元物品
-- -----------------------------------------------------------------------------
CREATE TABLE t_meta_item (
    id           bigserial    PRIMARY KEY,
    item_name    varchar(50)  NOT NULL UNIQUE,
    item_name_en varchar(50)  NOT NULL UNIQUE,
    item_cate    varchar(10),
    item_intro   varchar(255),
    create_time  timestamptz  DEFAULT now(),
    update_time  timestamptz  DEFAULT now()
);
COMMENT ON TABLE t_meta_item IS '元物品表';

-- -----------------------------------------------------------------------------
-- 12. 物品
-- -----------------------------------------------------------------------------
CREATE TABLE t_item (
    id           bigserial   PRIMARY KEY,
    meta_item_id bigint,
    item_extend  jsonb,
    create_time  timestamptz DEFAULT now(),
    update_time  timestamptz DEFAULT now()
);
COMMENT ON TABLE t_item IS '物品表（含所有元物品实例，扩展信息用 jsonb）';

-- -----------------------------------------------------------------------------
-- 13. 箱子
-- -----------------------------------------------------------------------------
CREATE TABLE t_box (
    box_id      bigserial   PRIMARY KEY,
    zone_id     bigint,
    box_type    integer     NOT NULL DEFAULT 0,
    length      integer,
    width       integer,
    box_cap     integer,
    box_load    integer,
    lock_stat   integer     DEFAULT 0,
    hunt_seq    bigint      DEFAULT 0,
    unlock_time timestamptz,
    lock_time   timestamptz
);
COMMENT ON TABLE t_box IS '箱子表（0 箱子、1 宝箱、2 空间箱）';

-- -----------------------------------------------------------------------------
-- 14. 箱子物品
-- -----------------------------------------------------------------------------
CREATE TABLE t_box_item (
    box_item_id bigserial PRIMARY KEY,
    box_id      bigint,
    box_idx     integer,
    item_id     bigint,
    amount      integer,
    lock_stat   integer   DEFAULT 0
);
COMMENT ON TABLE t_box_item IS '箱子物品表';

-- -----------------------------------------------------------------------------
-- 15. 夺宝记录
-- -----------------------------------------------------------------------------
CREATE TABLE t_box_hunt (
    hunt_id     bigint      PRIMARY KEY,
    hunt_seq    bigint,
    user_id     bigint,
    item_id     bigint,
    amount      integer,
    create_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_box_hunt IS '宝箱夺宝记录（hunt_id 为雪花 ID，非自增）';

-- -----------------------------------------------------------------------------
-- 16. 日志
-- -----------------------------------------------------------------------------
CREATE TABLE t_log_head (
    head_id     bigserial   PRIMARY KEY,
    log_level   varchar(10),
    log_thread  varchar(255),
    log_site    varchar(255),
    log_mode    varchar(20),
    log_type    varchar(20),
    log_url     varchar(255),
    log_parent  varchar(255),
    log_method  varchar(255),
    log_params  varchar(255),
    log_return  text,
    log_stack   text,
    log_read    integer     DEFAULT 0,
    create_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_log_head IS '日志头表';

CREATE TABLE t_log_message (
    message_id  bigserial   PRIMARY KEY,
    head_id     bigint,
    log_level   varchar(10),
    log_site    varchar(255),
    log_message text,
    create_time timestamptz DEFAULT now()
);
COMMENT ON TABLE t_log_message IS '日志信息表';

-- -----------------------------------------------------------------------------
-- 索引
-- -----------------------------------------------------------------------------
CREATE INDEX idx_user_name        ON t_user (user_name);
CREATE INDEX idx_friend_user      ON t_friend (user_id);
CREATE INDEX idx_friend_to_user   ON t_friend (to_user_id);
CREATE INDEX idx_ug_user          ON t_user_group (user_id);
CREATE INDEX idx_ug_group         ON t_user_group (group_id);
CREATE INDEX idx_zone_space       ON t_zone (space_id);
CREATE INDEX idx_zone_group       ON t_zone (group_id);
CREATE INDEX idx_req_target       ON t_session_request (target_id, req_status);
CREATE INDEX idx_box_item_box     ON t_box_item (box_id);

-- 会话唯一性：findSessionId(type, targetId) 语义要求唯一，重复会让 selectOne 抛异常
-- 若历史数据可能存在重复，先注释这一行
CREATE UNIQUE INDEX uk_session_type_target ON t_session (type, target_id);

-- 消息查询：同时服务 getLastMsgId(取 max) 和历史分页
CREATE INDEX idx_message_session_msg  ON t_message (session_id, msg_id);
CREATE INDEX idx_message_session_time ON t_message (session_id, create_time DESC);

-- 如需暴露 msg_id 并发冲突，把上面换成唯一索引：
-- 注意：当前 saveMessage 未捕获 23505，会触发 RabbitMQ 无限重投，压测前先确认消费端行为
-- CREATE UNIQUE INDEX uk_message_session_msg ON t_message (session_id, msg_id);

-- -----------------------------------------------------------------------------
-- 触发器：update_time / last_time
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_user_update_time      BEFORE UPDATE ON t_user
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
CREATE TRIGGER trg_group_update_time     BEFORE UPDATE ON t_group
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
CREATE TRIGGER trg_item_update_time      BEFORE UPDATE ON t_item
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
CREATE TRIGGER trg_meta_item_update_time BEFORE UPDATE ON t_meta_item
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
CREATE TRIGGER trg_session_update_time   BEFORE UPDATE ON t_session
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

CREATE OR REPLACE FUNCTION trg_set_site_last_time() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.last_time := now();
    RETURN NEW;
END $$;

CREATE TRIGGER trg_site_last_time BEFORE UPDATE ON t_site
    FOR EACH ROW EXECUTE FUNCTION trg_set_site_last_time();

-- -----------------------------------------------------------------------------
-- 基础种子数据（源自 chatspace.sql）
-- -----------------------------------------------------------------------------
INSERT INTO t_meta_item (item_name, item_name_en, item_cate, item_intro) VALUES
('空间币',       'SpaceCoin',          0, '系统中的虚拟货币，可用于交易'),
('区域资源',     'Zone Source',        1, '可占有空间中的区域'),
('箱子资源',     'Box Source',         1, '可划分出一片箱子区域，该区域内可存放其他物品'),
('传送门资源',   'Portal Source',      1, '可划分出一片传送门区域，处于同一个组的区域可互相传送'),
('空间发送器',   'Space Transmitter',  2, '可向当前空间发送一条消息，空间内所有用户都将收到'),
('世界发送器',   'Universe Transmitter', 2, '可向世界发送一条消息，世界内所有用户都将收到'),
('纸张',         'Paper',              2, '可供写字的纸张，写完的字无法擦除，会占用空间');

-- 为每个元物品生成一个实例
INSERT INTO t_item (meta_item_id) SELECT id FROM t_meta_item ORDER BY id;

-- 0 号空间：实时服务初始化位置时会调用 groupClient.getSpaceData(0)，缺这行会 NPE
INSERT INTO t_space (id, space_name, size_x, size_y) VALUES (0, '测试空间', 20, 20);
SELECT setval(pg_get_serial_sequence('t_space', 'id'), 1, false);

-- 测试宝箱与箱内物品
INSERT INTO t_box (zone_id, box_type) VALUES (9, 1);
INSERT INTO t_box_item (box_id, box_idx, item_id, amount) VALUES
(1,  1, 1, 10), (1,  2, 6,  1), (1,  3, 1,  7), (1,  4, 1, 15), (1,  5, 1,  5),
(1,  6, 1, 13), (1,  7, 5,  1), (1,  8, 1,  9), (1,  9, 1, 11), (1, 10, 2,  1);

COMMIT;

-- =============================================================================
-- 以下为可选内容，按需执行
-- =============================================================================

-- -----------------------------------------------------------------------------
-- A. 压测数据（1 万用户）
-- -----------------------------------------------------------------------------
-- INSERT INTO t_user (user_name, user_pass, nick_name)
-- SELECT 'u' || g, 'x', 'u' || g FROM generate_series(1, 10000) g;

-- 会话压测用的公共空间会话（type=3 空间，target=0 即 0 号空间）
-- INSERT INTO t_session (type, target_id) VALUES (3, 0) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- B. 高并发参数建议（按机器规格调整，勿直接照抄）
-- -----------------------------------------------------------------------------
-- 消息落库是高频小事务，测试环境可以牺牲一点持久性换吞吐：
--   ALTER SYSTEM SET synchronous_commit = off;
--   SELECT pg_reload_conf();
--
-- t_site 是"一行被反复 UPDATE"的典型，降低 fillfactor 让 HOT update 更容易命中：
--   ALTER TABLE t_site SET (fillfactor = 70);
--
-- t_message 是纯追加，autovacuum 需要更积极，避免膨胀拖慢索引扫描：
--   ALTER TABLE t_message SET (
--       autovacuum_vacuum_scale_factor  = 0.02,
--       autovacuum_analyze_scale_factor = 0.01
--   );
--
-- 服务侧连接池（Druid）不是越大越好，10 个实时节点 × 20 连接就 200 了；
-- 先确认 max_connections 与实例数的关系，再调 initialSize/maxActive。

-- -----------------------------------------------------------------------------
-- C. openGauss 兼容说明
-- -----------------------------------------------------------------------------
-- 本文件以 PostgreSQL 为准。若实际跑在 openGauss 3.x 上，需要替换：
--   bigserial                      -> 可用，但建议换成显式序列
--   EXECUTE FUNCTION               -> 老版本用 EXECUTE PROCEDURE
--   now()                          -> openGauss 也支持，无需改 pg_systimestamp()
--   jsonb                          -> 3.0 之前不支持，需改回 json 或 text
-- 另外 openGauss 不支持 CREATE INDEX ... INCLUDE，本文件未使用。
