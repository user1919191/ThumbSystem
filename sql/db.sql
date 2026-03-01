-- 点赞系统的建库建表
create database thumbSystem;
use thumbSystem;

show tables ;

-- 点赞表
create table thumb(
                      id int primary key not null,
                      postId int not null ,
                      userId int not null ,
                      create_time datetime not null ,
                      thumb_count int not null default 0
)comment '点赞表';

-- 点赞事件表
create table thumbEvent(
                           id int primary key not null ,
                           postId int not null ,
                           userId int not null ,
                           create_time datetime not null ,
                           thumbTrue int not null
)comment '点赞消息表';

-- 用户表
create table if not exists user
(
    id bigint auto_increment primary key comment '用户ID',
    username varchar(128) not null comment '姓名',
    age int comment '年龄',
    profession varchar(128) comment '职业',
    account varchar(128) not null unique comment '账号',
    password varchar(256) not null comment '密码',
    gender tinyint comment '性别 0-女 1-男',
    avatar varchar(512) comment '头像地址',
    email varchar(128) comment '邮箱',
    expertise varchar(512) comment '擅长领域',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete tinyint default 0 not null comment '是否删除 0-未删除 1-已删除'
) comment '用户表';

-- 博客表
create table if not exists blog
(
    id   bigint auto_increment primary key,
    userId   bigint                             not null,
    title varchar(512)                       null comment '标题',
    coverImg varchar(1024)                      null comment '封面',
    content text                               not null comment '内容',
    thumbCount int      default 0                 not null comment '点赞数',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
    );
create index idx_userId on blog (userId);



INSERT INTO thumb (id, postId, userId, create_time, thumb_count)
VALUES
    (1, 1, 1001, '2025-06-22 09:00:00', 0),
    (2, 2, 1002, '2025-06-22 09:30:00', 0),
    (3, 3, 1003, '2025-06-22 10:00:00', 0);


INSERT INTO thumbEvent (id, postId, userId, create_time, thumbTrue)
VALUES
    (101, 1, 101, '2025-06-22 10:05:00', 1);