
-- 用户表
create table t_user (
    id          int auto_increment comment '用户 ID' primary key,
    user_name   varchar(20)        comment '用户名',
    user_pass   varchar(255)       comment '用户密码',
    sex         varchar(10)        comment '用户性别',
    nick_name   varchar(20)        comment '用户昵称',
    avatar      varchar(255)       comment '用户头像',
    box_id      int                comment '用户的随身空间箱 ID',
    freeze_time timestamp null default null comment '用户冻结的时间',
    delete_time timestamp null default null comment '用户注销的时间',
    create_time timestamp      default CURRENT_TIMESTAMP comment '账号创建时间',
    update_time timestamp      default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '修改时间'
);

-- 用户表字段区分大小写
ALTER TABLE t_user MODIFY COLUMN user_name VARCHAR(20) BINARY CHARACTER SET utf8 COLLATE utf8_bin DEFAULT NULL;

-- 好友表
create table t_friend (
    map_id      int auto_increment comment '映射 ID' primary key,
    friend_id  int                comment '好友 ID，唯一标识',
    user_id     int                comment '用户 ID',
    to_user_id  int                comment '好友用户 ID',
    last_msg_id int default 0      comment '最后一次阅读的消息 ID',
    mark_name   varchar(20)        comment '好友备注',
    delete_time timestamp null default null comment '取消好友的时间',
    create_time timestamp      default CURRENT_TIMESTAMP comment '成为好友的时间'
);

-- 好友表索引
create index user_id_index on t_friend (user_id);

-- 群组表·改（新增 type 字段，表示用户创建的或者是系统维护的，系统维护的有传送门、信箱这些）
create table t_group (
    id          int auto_increment comment '群组 ID' primary key,
    creator_id  int                comment '创建者 ID',
    group_type  int default 0      comment '群组类型（默认为 0 表示用户创建的）',
    group_name  varchar(20)        comment '群组名称',
    avatar      varchar(255)       comment '群组标识',
    number      int                comment '关键数量（人数、传送区数等）',
    delete_time timestamp null default null comment '删除群聊时间',
    create_time timestamp      default CURRENT_TIMESTAMP comment '群聊创建时间',
    update_time timestamp      default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP not null comment '修改时间'
);

-- 用户群聊表
create table t_user_group (
    map_id      int       auto_increment comment '映射 ID' primary key,
    user_id     int                      comment '用户 ID',
    group_id    int                      comment '群聊 ID',
    last_msg_id int default 0            comment '最后一条阅读的消息 ID',
    mark_name   varchar(20)              comment '用户群聊备注',
    delete_time timestamp null default null         comment '用户退群时间',
    create_time timestamp default CURRENT_TIMESTAMP comment '用户进群时间'
);

-- 用户群聊表索引
create index user_id_index on t_user_group (user_id);
create index group_id_index on t_user_group (group_id);

-- 会话表
create table t_session (
    id          int       auto_increment comment '会话 ID' primary key,
    type        int                      comment '会话类型（0-私聊，1-群组，2-区域，3-空间）',
    target_id   int                      comment '会话的目标 ID（可指向好友、群组、区域、空间）',
    delete_time timestamp null default null         comment '会话删除时间（群主解散群或私聊一方删除好友）',
    create_time timestamp default CURRENT_TIMESTAMP comment '会话创建时间',
    update_time timestamp default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '会话更新时间'
);

-- 会话聊天记录表
create table t_session_message (
    id          int       auto_increment comment '会话聊天记录 ID' primary key,
    session_id  int                      comment '所属会话 ID',
    msg_id      int                      comment '会话内消息 ID',
    sender_id   int                      comment '消息发送者 ID',
    type        int                      comment '消息类型（0-文本，1-图片，2-视频）',
    content     varchar(255)             comment '消息内容',
    create_time timestamp default CURRENT_TIMESTAMP comment '消息发送时间'
);

-- 会话聊天记录表索引
create index session_id_index on t_session_message (session_id);

-- 会话申请表
create table t_session_request (
    id          int       auto_increment comment '请求 ID' primary key,
    type        int                      comment '会话类型',
    sender_id   int                      comment '发送者用户 ID',
    target_id   int                      comment '目标 ID（用户/群聊主键）',
    req_src     varchar(255)             comment '请求来源',
    req_remark  varchar(255)             comment '请求备注信息',
    req_status  int default 0            comment '请求状态（0 未读，1 已读，2 接受，3 拒绝）',
    create_time timestamp default CURRENT_TIMESTAMP comment '请求发送时间'
);

-- 空间元数据表
CREATE TABLE t_space (
    id          int auto_increment comment '空间 ID' primary key,
    space_name  varchar(40) comment '空间名称',
    size_x      int comment '空间横向大小',
    size_y      int comment '空间纵向大小',
    create_time timestamp default CURRENT_TIMESTAMP comment '空间创建时间' NOT NULL
);

-- 用户空间元数据表
CREATE TABLE t_site (
    id   int auto_increment comment '位置 ID，同时也是用户 ID' primary key,
    space_id  int comment '空间 ID',
    x         int comment '用户所在 x 坐标',
    y         int comment '用户所在 y 坐标',
    vision    int comment '用户视野大小',
    exist     int comment '用户存在感大小',
    last_time timestamp default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '最后一次下线时间'
);

-- 区域元数据表
CREATE TABLE t_zone (
    id          int auto_increment comment '区域 ID' primary key,
    group_id    int comment '所属群组',
    space_id    int comment '所在空间',
    zone_pid    int default 0 comment '父级区域 ID（上一级嵌套区域的 ID，否则为 0）',
    zone_rid    int default 0 comment '根级区域 ID（最初一级的嵌套区域 ID，否则为 0）',
    zone_name   varchar(40) comment '区域名称',
    zone_type   int comment '区域类型',
    start_x     int comment '起始 x 坐标',
    start_y     int comment '起始 y 坐标',
    end_x       int comment '终止 x 坐标',
    end_y       int comment '终止 y 坐标',
    create_time timestamp      default CURRENT_TIMESTAMP comment '区域创建时间' NOT NULL,
    delete_time timestamp null default null comment '区域删除时间',
    access_key  varchar(40) comment '进入区域需持有的 key'
);

-- 元物品表
CREATE TABLE t_meta_item (
    id        int auto_increment comment '物品 ID' primary key,
    item_name varchar(50) not null unique comment '物品名称',
    item_name_en varchar(50) not null unique comment '物品的英文名称',
    item_cate char(10) comment '物品类别',
    item_intro varchar(255) comment '物品简介',
    create_time timestamp default CURRENT_TIMESTAMP comment '物品创建时间',
    update_time timestamp default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '物品更新时间'
);

INSERT INTO t_meta_item (item_name, item_name_en, item_cate, item_intro) values
('空间币', 'SpaceCoin', 0, '系统中的虚拟货币，可用于交易'),
('区域资源', 'Zone Source', 1, '可占有空间中的区域'),
('箱子资源', 'Box Source', 1, '可划分出一片箱子区域，该区域内可存放其他物品'),
('传送门资源', 'Portal Source', 1, '可划分出一片传送门区域，处于同一个组的区域可互相传送'),
('空间发送器', 'Space Transmitter', 2, '可向当前空间发送一条消息，空间内所有用户都将收到'),
('世界发送器', 'Universe Transmitter', 2, '可向世界发送一条消息，世界内所有用户都将收到'),
('纸张', 'Paper', 2, '可供写字的纸张，写完的字无法擦除，会占用空间');

-- 物品表（动态）会包含所有元物品
CREATE TABLE t_item (
    id int auto_increment comment '物品 ID' primary key,
    meta_item_id int comment '对应的元物品 ID',
    item_extend json comment '物品的扩展信息',
    create_time timestamp default CURRENT_TIMESTAMP comment '物品创建时间',
    update_time timestamp default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '物品更新时间'
);

-- 添加所有元物品，无扩展信息
INSERT INTO t_item(meta_item_id) VALUES (1), (2), (3), (4), (5), (6), (7);

-- 箱子表
CREATE TABLE t_box (
    box_id      int auto_increment comment '箱子 ID' primary key,
    zone_id     int comment '箱子所占有的区域 ID',
    box_type    int       not null default 0 comment '箱子类型（箱子、宝箱、空间箱）',
    length      int comment '箱子的长度',
    width       int comment '箱子的宽度',
    box_cap     int comment '箱子的容量',
    box_load    int comment '箱子的承重量',
    lock_stat   int                default 0 comment '箱子的锁标识（0 锁住、1 解锁）',
    hunt_seq    bigint             default 0 comment '宝箱的夺宝序列号',
    unlock_time timestamp null     default null comment '箱子自动解锁定时任务',
    lock_time   timestamp null     default null comment '箱子自动上锁定时任务'
);

-- 测试宝箱（宝箱类型）
INSERT INTO t_box (zone_id, box_type) values (9, 1);

-- 箱子物品表
CREATE TABLE t_box_item (
    box_item_id int auto_increment comment '物品项 ID' primary key,
    box_id int comment '物品所属的箱子 ID',
    box_idx int comment '箱子内空间的索引',
    item_id int comment '该索引上存放的物品 ID',
    amount int comment '物品的数量',
    lock_stat int default 0 comment '物品项的锁标识'
);

-- 测试宝箱的物品
INSERT INTO t_box_item (box_id, box_idx, item_id, amount) values
(1, 1, 1, 10),
(1, 2, 6, 1),
(1, 3, 1, 7),
(1, 4, 1, 15),
(1, 5, 1, 5),
(1, 6, 1, 13),
(1, 7, 5, 1),
(1, 8, 1, 9),
(1, 9, 1, 11),
(1, 10, 2, 1);

-- 宝箱的夺宝记录
CREATE TABLE t_box_hunt (
    hunt_id     bigint comment '夺宝 ID' primary key,
    hunt_seq    bigint comment '所属的夺宝序列号',
    user_id     int comment '所属的用户 ID',
    item_id     int comment '夺宝获得的物品 ID',
    amount      int comment '获得的物品数量',
    create_time timestamp default CURRENT_TIMESTAMP comment '夺宝成功的时间'
);

-- 日志头表
create table t_log_head(
    head_id     int       auto_increment comment '日志头 ID' primary key,
    log_level   char(10)                 comment '日志级别',
    log_thread  varchar(255)             comment '日志线程',
    log_site    varchar(255)             comment '日志产生位置',
    log_mode    varchar(20)              comment '日志模式',
    log_type    varchar(20)              comment '日志类型',
    log_url     varchar(255)             comment '请求路径',
    log_parent  varchar(255)             comment '所属父类',
    log_method  varchar(255)             comment '所属方法',
    log_params  varchar(255)             comment '传入参数',
    log_return  text                     comment '请求返回',
    log_stack   text                     comment '日志的堆栈调用信息',
    log_read    int       default 0      comment '是否已读',
    create_time timestamp default CURRENT_TIMESTAMP not null comment '创建时间'
);

-- 日志信息表
create table t_log_message(
    message_id  int       auto_increment comment '日志信息 ID' primary key,
    head_id     int                      comment '日志头 ID',
    log_level   char(10)                 comment '日志级别',
    log_site    varchar(255)             comment '日志产生位置',
    log_message text                     comment '日志信息',
    create_time timestamp default CURRENT_TIMESTAMP not null comment '创建时间'
)