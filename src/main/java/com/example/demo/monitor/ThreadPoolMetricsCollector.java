package com.example.demo.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池指标收集器
 */
@Slf4j
public class ThreadPoolMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final String poolName;
    private final ThreadPoolExecutor executor;

    public ThreadPoolMetricsCollector(MeterRegistry meterRegistry, String poolName, ThreadPoolExecutor executor) {
        this.meterRegistry = meterRegistry;
        this.poolName = poolName;
        this.executor = executor;
        registerMetrics();
    }

    private void registerMetrics() {
        // executor_active_threads: 执行器活跃的线程数
        Gauge.builder("executor_active_threads", executor, ThreadPoolExecutor::getActiveCount)
                .tag("pool_name", poolName)
                .description("Number of active threads in the executor")
                .register(meterRegistry);

        // executor_queued_threads: 排队的任务
        Gauge.builder("executor_queued_threads", executor, e -> e.getQueue().size())
                .tag("pool_name", poolName)
                .description("Number of queued tasks in the executor")
                .register(meterRegistry);

        // executor_pool_size_threads: 执行器的线程池大小
        Gauge.builder("executor_pool_size_threads", executor, ThreadPoolExecutor::getPoolSize)
                .tag("pool_name", poolName)
                .description("Current pool size of the executor")
                .register(meterRegistry);

        // executor_completed_tasks_total: 完成的任务
        Gauge.builder("executor_completed_tasks_total", executor, ThreadPoolExecutor::getCompletedTaskCount)
                .tag("pool_name", poolName)
                .description("Total number of completed tasks")
                .register(meterRegistry);

        // executor_core_pool_size: 核心线程数
        Gauge.builder("executor_core_pool_size", executor, ThreadPoolExecutor::getCorePoolSize)
                .tag("pool_name", poolName)
                .description("Core pool size of the executor")
                .register(meterRegistry);

        // executor_max_pool_size: 最大线程数
        Gauge.builder("executor_max_pool_size", executor, ThreadPoolExecutor::getMaximumPoolSize)
                .tag("pool_name", poolName)
                .description("Maximum pool size of the executor")
                .register(meterRegistry);

        // executor_largest_pool_size: 历史峰值线程数
        Gauge.builder("executor_largest_pool_size", executor, ThreadPoolExecutor::getLargestPoolSize)
                .tag("pool_name", poolName)
                .description("Largest pool size in the executor history")
                .register(meterRegistry);

        // executor_task_count_total: 总任务数
        Gauge.builder("executor_task_count_total", executor, ThreadPoolExecutor::getTaskCount)
                .tag("pool_name", poolName)
                .description("Total number of tasks (completed + queued + active)")
                .register(meterRegistry);

        // executor_queue_capacity: 队列容量
        Gauge.builder("executor_queue_capacity", executor, e -> e.getQueue().size() + e.getQueue().remainingCapacity())
                .tag("pool_name", poolName)
                .description("Total capacity of the task queue")
                .register(meterRegistry);

        // executor_queue_remaining_capacity: 队列剩余容量
        Gauge.builder("executor_queue_remaining_capacity", executor, e -> e.getQueue().remainingCapacity())
                .tag("pool_name", poolName)
                .description("Remaining capacity of the task queue")
                .register(meterRegistry);

        // executor_seconds_count, executor_seconds_sum, executor_seconds_max
        // 通过 Timer 自动生成这三个指标
        Timer.builder("executor_seconds")
                .tag("pool_name", poolName)
                .description("Executor execution time")
                .register(meterRegistry);

        log.info("Registered metrics for thread pool: {}", poolName);
    }
}
