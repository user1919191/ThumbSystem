# 项目架构图

<img width="3508" height="2640" alt="稳赞系统架构图" src="https://github.com/user-attachments/assets/ea086ee2-eb87-4320-8a08-62b496e0e295" />

## 点赞数据时序图

<img width="1294" height="675" alt="PixPin_2025-09-18_17-49-31" src="https://github.com/user-attachments/assets/edd73504-4870-4a12-866f-6ce08fcecb59" />

# 技术选型

## 技术栈选型

* 后端框架：Spring Boot 3 + Java 21
* 数据访问：MyBatis-Plus
* 分布式数据库：TiDB
* 内存数据库: Caffeine
* 缓存系统：Redis Cluster
* 消息队列：Pulsar
* 容器化：Docker
* 负载均衡：Nginx
* 监控系统：Prometheus + Grafana
* 压测工具: JMeter
* 性能分析工具: JProfiler
* 限流组件: Sentinel

## 为什么选用RedisCluster+Pulsar+TiDB架构

| 功能项 | Redis → Pulsar → TiDB | 直接Pulsar → TiDB | Redis + TiDB 同步 | 技术选型分析 |
|--------|----------------------|-------------------|------------------|-------------|
| **架构复杂度** | 较高 | 简单 | 简单 | Redis→Pulsar→TiDB架构相对复杂但职责清晰 |
| **组件耦合度** | 低耦合 | 中等耦合 | 高耦合 | Redis→Pulsar→TiDB各组件职责分离，耦合度最低 |
| **数据流向** | 三级流转 | 二级流转 | 二级流转 | 三级流转提供更好的缓冲和削峰能力 |
| **写入延迟** | 极低(Redis层) | 中等(消息队列) | 高(同步DB) | Redis内存操作提供最低写入延迟 |
| **吞吐能力** | 极高 | 高 | 中等 | Redis层承接瞬时高并发，整体吞吐最优 |
| **资源消耗** | 中等 | 低 | 高 | 三级架构资源消耗适中，性能均衡 |
| **实时一致性** | 最终一致性 | 最终一致性 | 强一致性 | 前两者为最终一致性，Redis+TiDB同步为强一致性 |
| **数据可靠性** | 高 | 高 | 中等 | 前两者通过消息队列保证可靠性，同步方式存在单点风险 |
| **故障恢复** | 自动恢复 | 自动恢复 | 手动干预 | 异步架构具备更好的故障自动恢复能力 |
| **系统容错** | 高 | 高 | 低 | 多级缓冲架构容错能力最强 |
| **组件依赖** | 弱依赖 | 强依赖 | 强依赖 | Redis→Pulsar→TiDB各组件故障影响范围最小 |
| **服务降级** | 支持降级 | 有限降级 | 难降级 | Redis层可独立服务，降级策略丰富 |
| **水平扩展** | 易扩展 | 易扩展 | 难扩展 | 前两者各组件均可独立水平扩展 |
| **扩容影响** | 无影响 | 无影响 | 需要停机 | 异步架构扩容对业务无感知 |
| **高并发场景** | 极佳 | 良好 | 较差 | Redis层完美承接突发流量 |
| **数据追溯** | 支持 | 支持 | 不支持 | 消息队列提供数据重放和能力 |

**选型** 
* 在本项目中，因为T0级别为**高并发高可用**，Redis作为第一级存储，提供了**微秒级的内存操作能力**，能够完美承接点赞系统特有的瞬时高并发请求。Pulsar作为和kafka相当的**高并发消息队列**,性能非常优秀。TiDB作为新一代**NewSQL**的代表,功能完备也代表未来SQL发展方向。
* Redis+Pulsar+TiDB对于数据处理的不同**职责充分解耦**,Redis承接高流量读写请求,Pulsar做削峰填谷和异步,TiDB做好数据分片和持久化存储。这三层架构可以**提供极高的容错机制和水平扩展能力**首先从容错机制来说,Redis层故障时可降级到Pulsar直写,Pulsar提供了数据查询的功能,在极限情况下可以承担起数据读取的功能;TiDB层故障时消息自动重试,确保了即使在部分组件异常的情况下，**系统整体仍能保持可用**。然后,就是水平扩展,Redis Cluster通过增加节点自动分片数据；Pulsar基于计算存储分离架构实现无缝扩容；TiDB支持在线弹性扩展。这种**底成本的弹性伸缩方案**可以随着业务流量调整架构复杂度,承接小流量->大流量的各个业务场景的需求。

## RedisCluster和Redis读写集群技术选型

| 功能项 | Redis Cluster | Redis 主从复制+读写分离 | 技术选型分析 |
|--------|---------------|------------------------|-------------|
| **架构模式** | 原生分布式集群 | 主从复制 + 哨兵模式 | Redis Cluster为官方分布式方案，主从复制为传统高可用方案 |
| **数据分片** | 自动分片(16384 slots) | 无分片 | Cluster自动数据分片，主从复制全量数据复制 |
| **写扩展性** | 多主节点写入 | 单主节点写入 | Cluster支持多主写入，大幅提升写性能 |
| **读扩展性** | 多节点读取 | 多从节点读取 | 两者都支持读扩展，Cluster扩展性更强 |
| **数据同步** | 异步复制 | 异步/半同步复制 | 两者都是最终一致性，主从可配置半同步 |
| **数据分区** | 哈希槽分区 | 无分区 | Cluster通过哈希槽实现数据分布 |
| **跨节点事务** | 不支持 | 不支持 | 两者都不支持跨节点事务 |
| **故障转移** | 自动故障转移 | 哨兵自动切换 | 两者都支持自动故障转移 |
| **故障恢复时间** | 秒级 | 秒级 | 故障检测和切换时间相近 |
| **数据可靠性** | 多副本保障 | 多副本保障 | 两者都通过副本保证数据安全 |
| **写入吞吐量** | 线性扩展 | 单主瓶颈 | Cluster写性能随节点数线性增长 |
| **读取吞吐量** | 线性扩展 | 线性扩展 | 两者读性能都可线性扩展 |
| **跨节点操作** | 需要重定向 | 无跨节点操作 | Cluster跨节点操作有额外开销 |
| **部署复杂度** | 中等 | 简单 | Cluster部署和配置相对复杂 |
| **监控管理** | 需要集群管理工具 | 相对简单 | Cluster监控需要关注更多指标 |
| **数据迁移** | 自动数据平衡 | 手动数据同步 | Cluster支持自动数据迁移 |
| **客户端支持** | 需要集群感知客户端 | 普通客户端即可 | Cluster需要特殊客户端支持 |

**选型：** 在本项目中，因为首要设计为承接高并发高可用，Redis Cluster是支持分片的，在多个分片上可以并发的进行读写操作，可以极高的提升性能。而且Redis Cluster是去中心化的，相比于读写集群降低了单点故障带来的影响，所以选用Redis Cluster。

## Pulsar和Kafka技术选型

[Pulsar官方文档](https://pulsar.apache.org/docs/4.1.x/)

| 功能项 | Pulsar | Kafka | 技术选型分析 |
|--------|--------|-------|-------------|
| **消费推拉模式** | push | pull | Pulsar服务端推送提供更高实时性 |
| **延迟队列** | 支持 | 不支持 | Pulsar提供延迟消息，Kafka需自行实现 |
| **死信队列** | 支持 | 不支持 | Pulsar提供完善的重试和死信机制 |
| **优先级队列** | 不支持 | 不支持 | 两者均不支持消息优先级 |
| **消息回溯** | 支持 | 支持 | 两者都支持按偏移量重新消费 |
| **消息持久化** | 支持 | 支持 | 两者都提供持久化保证 |
| **消息确认机制** | Offset + 单条确认 | Offset 确认 | Pulsar支持更细粒度的消息确认 |
| **消息TTL** | 支持 | 不支持 | Pulsar支持消息消费超时后自动过期 |
| **多租户隔离** | 支持 | 不支持 | Pulsar原生支持多租户 |
| **消息顺序性** | 流模式有序 | 分区有序 | Pulsar流模式全局有序，Kafka仅分区有序 |
| **消息查询** | 支持 | 不支持 | Pulsar支持按属性查询消息 |
| **消息模式** | 流模式 + 队列模式 | 流模式 | Pulsar支持更丰富的消息模式 |
| **消息可靠性** | Ack Quorum Size(Qa) | request.required.acks | 两者都提供可配置的可靠性级别 |
| **单机吞吐量** | 百万级QPS | 百万级QPS | 两者在吞吐量方面性能相当 |
| **消息延迟** | ms级 | ms级 | 两者延迟表现相近 |
| **支持主题数** | 上百万个 | 几十到几百 | Pulsar支持海量主题，Kafka在海量主题时性能严重受损 |
| **高可用** | 分布式架构 | 分布式架构 | 两者都提供高可用保证 |
| **跨地域容灾** | 支持 | 不支持 | Pulsar内置多地域复制功能 |
| **集群扩容** | 增加节点，通过新增分片负载均衡 | 增加节点，通过复制数据均衡 | Pulsar扩容更平滑，无需数据重平衡 |

**选型：** 在本项目中，Pulsar对于高并发高可用需求支持能力更强，原生提供延迟队列和死信队列支撑更丰富的业务场景，计算和存储分离架构以及Broker无状态的服务机制提供更简单的水平扩容能力，支持百万级别的Topic，相比于Kafka可用性更强。并且Pulsar对于云原生提供很强的支持性，而云原生也是未来的发展趋势。因此本项目选择Pulsar作为MQ。

## TiDB和MySQL+ShardingJDBC技术选型

[TiDB官方文档](https://docs.pingcap.com/zh/tidb/stable/overview/?source=welcome)

| 功能项 | TiDB | MySQL + ShardingJDBC | 技术选型分析 |
|--------|------|---------------------|-------------|
| **架构模式** | 原生分布式NewSQL | 单机数据库 + 应用层分片 | TiDB原生分布式，ShardingJDBC为中间件方案会引入一层负载降低性能 |
| **数据分片** | 自动分片 | 手动分片配置 | TiDB自动管理分片，ShardingJDBC需预先规划 |
| **计算存储分离** | 支持 | 不支持 | TiDB可独立扩展计算和存储节点 |
| **SQL兼容性** | MySQL 5.7协议兼容 | 原生MySQL兼容 | 都高度兼容MySQL语法 |
| **水平扩展** | 在线弹性扩展 | 支持但需停机 | TiDB扩容无感知，ShardingJDBC扩容复杂 |
| **扩展粒度** | 细粒度(计算存储分离架构) | 粗粒度(整个分片) | TiDB扩展更灵活 |
| **最大数据量** | PB级别 | TB级别 | TiDB适合海量数据场景 |
| **分布式事务** | 支持(乐观锁) | 支持(最终一致性) | TiDB提供强一致性事务 |
| **跨分片查询** | 透明支持 | 支持但性能有损 | TiDB自动优化分布式查询 |
| **全局索引** | 支持 | 不支持 | TiDB支持跨节点的全局索引 |
| **读写性能** | 高并发读写 | 高并发读，写受分片限制 | TiDB写性能更优 |
| **复杂查询** | 优化器智能优化 | 需要手动优化分片查询 | TiDB对复杂查询支持更好 |
| **热点处理** | 自动热点Region调度 | 手动分片键调整 | TiDB自动负载均衡 |
| **故障恢复** | 自动故障转移 | 依赖主从复制 | TiDB Raft协议保证高可用 |
| **数据备份** | 分布式快照 | 传统备份方式 | TiDB备份更高效 |
| **多活部署** | 支持 | 有限支持 | TiDB原生支持多数据中心 |
| **部署复杂度** | 中等 | 简单到复杂 | 简单场景MySQL更易部署 |
| **日常运维** | 自动化运维 | 手动分片管理 | TiDB运维更自动化 |
| **监控告警** | 完善监控体系 | 需要自行搭建 | TiDB监控更全面 |

**选型：** 在本项目高并发场景中，分布式NewSQL代替MySQL集群，可以实时在线扩容，支撑业务从零到亿级用户的完整生命周期。原生分布式设计，性能更优。多副本自动故障转移，业务不停机，可以实现服务高可用。自动热点调度，应对突发流量。

# 具体实现

## 为什么要做点赞系统

从业务方面来看，点赞系统是社交平台和内容社区最核心的互动功能之一，具有极高的使用范围和技术代表性。虽然点赞功能表面简单，但其"读多写多"的业务特性使其成为典型的高并发场景，能够很好地模拟真实互联网业务中的技术挑战。从技术学习角度，这个项目涵盖了现代分布式系统的完整技术栈,更重要的是，点赞系统的技术方案具有很好的普适性，其架构设计和优化思路可以复用到收藏、关注、投票等众多类似业务场景中，具备很高的实用价值。

## JVM参数配置

```bash
-Xms4g
-Xmx4g
-XX:+UseG1GC
-XX:+UseFastEmptyMethods                 
-XX:+UseFastJNIAccessors              

#日志
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-XX:+PrintGCTimeStamps
-XX:+PrintGCApplicationStoppedTime
-XX:+PrintGCApplicationConcurrentTime
-XX:+PrintTenuringDistribution
-XX:+PrintAdaptiveSizePolicy
-Xloggc:/opt/logs/gc.log

#元空间
-XX:MetaspaceSize=256M
-XX:MaxMetaspaceSize=256M
-XX:+UseCompressedClassPointers
-XX:+UseCompressedOops

#G1
-XX:G1HeapRegionSize=4m
-XX:MaxGCPauseMillis=200
-XX:ParallelGCThreads=8                   
-XX:ConcGCThreads=4                    
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=60
-XX:G1HeapWastePercent=5
-XX:G1MixedGCCountTarget=8
-XX:G1MixedGCLiveThresholdPercent=85
-XX:G1RSetUpdatingPauseTimePercent=10
-XX:InitiatingHeapOccupancyPercent=35
-XX:+G1UseAdaptiveIHOP
-XX:G1ReservePercent=15

#直接内存
-XX:MaxDirectMemorySize=1g
-XX:+DisableExplicitGC

#逃逸分析
-XX:+DoEscapeAnalysis
-XX:+EliminateAllocations
-XX:+EliminateLocks
-XX:+UseBiasedLocking
-XX:BiasedLockingStartupDelay=0

#JIT
-XX:+TieredCompilation
-XX:CompileThreshold=10000
-XX:ReservedCodeCacheSize=256m
-XX:InitialCodeCacheSize=64m
-XX:+PrintCompilation

#方法内联
-XX:MaxInlineSize=35
-XX:FreqInlineSize=325
-XX:InlineSmallCode=1000
-XX:+InlineSynchronizedMethods
```

### 部分核心JVM参数解析

**-XX:MaxGCPauseMillis=200**
* 控制最大停顿时间

**-XX:InitiatingHeapOccupancyPercent=35**
* 控制IHOP触发阈值,尽量避免Eden区填满导致的Full GC

**-XX:+TieredCompilation**
*  结合C1和C2编译器优势，平衡启动速度和运行性能

**-XX:+UseCompressedOops**
* 启用普通对象指针压缩,对于大数据量小对象的点赞系统来说,可以明显优化性能

**-XX:+UseCompressedClassPointers**
* 启用压缩类指针

## RedisCluster配置

```yaml
spring:
  redis:
    cluster:
      nodes:
        - redis-node1:6379
        - redis-node2:6379
        - redis-node3:6379
      max-redirects: 3
    lettuce:
      pool:
        max-active: 8
        max-wait: -1ms
        max-idle: 8
        min-idle: 0
      cluster:
        refresh:
          adaptive: true
          period: 2000
```

