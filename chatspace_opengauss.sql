
-- 用户表
create table t_user (
    id          bigserial not null constraint idx_16983_primary primary key,
    user_name   varchar(20),
    user_pass   varchar(255),
    sex         varchar(10),
    nick_name   varchar(20),
    avatar      varchar(255),
    email       varchar(50),
    box_id      bigint,
    create_time timestamp with time zone default pg_systimestamp() not null,
    update_time timestamp with time zone,
    freeze_time timestamp with time zone,
    delete_time timestamp with time zone
);


-- 群组表
create table t_group (
    id          bigserial not null constraint idx_16909_primary primary key,
    creator_id  bigint,
    group_type  bigint                   default 0::bigint,
    group_name  varchar(20),
    avatar      varchar(255),
    number      bigint,
    delete_time timestamp with time zone,
    create_time timestamp with time zone default pg_systimestamp() not null,
    update_time timestamp with time zone,
    foreign key (creator_id) references t_user(id)
);

-- 好友表
create table t_friend (
    map_id      bigserial not null constraint idx_16901_primary primary key,
    friend_id   bigint,
    user_id     bigint,
    to_user_id  bigint,
    last_msg_id bigint                   default 0::bigint,
    mark_name   varchar(20),
    delete_time timestamp with time zone,
    create_time timestamp with time zone default pg_systimestamp() not null,
    foreign key (user_id) references t_user(id),
    foreign key (to_user_id) references t_user(id)
);

-- 用户群组表
create table t_user_group (
    map_id      bigserial not null constraint idx_16993_primary primary key,
    user_id     bigint,
    group_id    bigint,
    last_msg_id bigint                   default 0::bigint,
    mark_name   varchar(20),
    create_time timestamp with time zone default pg_systimestamp() not null,
    delete_time timestamp with time zone,
    foreign key (user_id) references t_user(id),
    foreign key (group_id) references t_group(id)
);

-- 空间表
create table t_space (
    id          bigserial not null constraint idx_16976_primary primary key,
    space_name  varchar(40),
    size_x      bigint,
    size_y      bigint,
    create_time timestamp with time zone default pg_systimestamp() not null
);

-- 区域表
create table t_zone (
    id          bigserial not null constraint idx_17001_primary primary key,
    group_id    bigint,
    space_id    bigint,
    zone_pid    bigint                   default 0::bigint,
    zone_rid    bigint                   default 0::bigint,
    start_x     bigint,
    start_y     bigint,
    end_x       bigint,
    end_y       bigint,
    zone_type   bigint,
    zone_name   varchar(40),
    create_time timestamp with time zone default pg_systimestamp() not null,
    delete_time timestamp with time zone,
    foreign key (group_id) references t_group(id),
    foreign key (space_id) references t_space(id),
    foreign key (zone_pid) references t_zone(id),
    foreign key (zone_rid) references t_zone(id)
);

-- 用户位置表
create table t_site (
    id        bigserial not null constraint idx_16970_primary primary key,
    user_id   bigint,
    space_id  bigint,
    x         bigint,
    y         bigint,
    vision    bigint,
    exist     bigint,
    last_time timestamp with time zone,
    foreign key (user_id) references t_user(id),
    foreign key (space_id) references t_space(id)
);

-- 会话表
create table t_session (
    id          bigserial not null constraint idx_16952_primary primary key,
    type        bigint,
    target_id   bigint,
    delete_time timestamp with time zone,
    create_time timestamp with time zone default pg_systimestamp() not null,
    update_time timestamp with time zone
);

-- 消息表
create table t_message (
    id          bigserial not null constraint idx_16945_primary primary key,
    session_id  bigint,
    msg_id      bigint,
    sender_id   bigint,
    type        bigint,
    content     varchar(255),
    create_time timestamp with time zone default pg_systimestamp() not null,
    foreign key (session_id) references t_session(id),
    foreign key (sender_id) references t_user(id)
);

-- 会话申请表
create table t_session_request (
    id          bigserial not null constraint idx_16959_primary primary key,
    type        bigint,
    sender_id   bigint,
    target_id   bigint,
    req_src     varchar(255),
    req_remark  varchar(255),
    req_status  bigint                   default 0::bigint,
    create_time timestamp with time zone default pg_systimestamp() not null,
    foreign key (sender_id) references t_user(id),
    foreign key (target_id) references t_user(id)
);






-----

-- 空间元数据表
SET search_path = chat_space_v2;
CREATE TABLE t_space
(
    space_id    bigint                   DEFAULT nextval('t_friend_map_id_seq'::regclass) NOT NULL,
    space_name  character varying(20),
    size_x      int,
    size_y      int,
    create_time timestamp with time zone DEFAULT pg_systimestamp()                        NOT NULL
) WITH (orientation=row, compression=no);
COMMENT ON COLUMN t_space.space_id IS '空间 ID';
COMMENT ON COLUMN t_space.space_name IS '空间的名称';
COMMENT ON COLUMN t_space.size_x IS '空间的横向大小';
COMMENT ON COLUMN t_space.size_y IS '空间的纵向大小';
COMMENT ON COLUMN t_space.create_time IS '空间的创建时间';
ALTER TABLE t_space ADD CONSTRAINT idx_space_primary PRIMARY KEY (space_id);

-- 用户空间元数据表
SET search_path = chat_space_v2;
CREATE TABLE t_site
(
    user_id   bigint,
    space_id  bigint,
    x         int,
    y         int,
    vision    int,
    exist     int,
    last_time timestamp with time zone DEFAULT pg_systimestamp()                            NOT NULL
) WITH (orientation=row, compression=no);
COMMENT ON COLUMN t_site.user_id IS '用户 ID';
COMMENT ON COLUMN t_site.space_id IS '用户所在空间 ID';
COMMENT ON COLUMN t_site.x IS '用户所在 x 坐标';
COMMENT ON COLUMN t_site.y IS '用户所在 y 坐标';
COMMENT ON COLUMN t_site.vision IS '用户的视野大小';
COMMENT ON COLUMN t_site.exist IS '用户的存在感大小';
COMMENT ON COLUMN t_site.last_time IS '最后一次上线时间';
ALTER TABLE t_site ADD CONSTRAINT idx_site_primary PRIMARY KEY (user_id);

-- 空间状态内存表（目前为每个空间三份：位置、视野和存在感各一张表）
-- 位置信息还包括了视野和存在感的大小
create foreign table t_site0 (
    x       int,
    y       int,
    user_id int,
    exist   int,
    vision  int,
    primary key (x, y, user_id)
);
create foreign table t_vision0 (
    x       int,
    y       int,
    user_id int,
    primary key (x, y, user_id)
);
create foreign table t_exist0 (
    x       int,
    y       int,
    user_id int,
    primary key (x, y, user_id)
);
