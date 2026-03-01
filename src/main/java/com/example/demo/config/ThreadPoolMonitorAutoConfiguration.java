package com.example.demo.config;

import com.example.demo.monitor.ThreadPoolMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池监控自动配置
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ThreadPoolExecutor.class, MeterRegistry.class})
public class ThreadPoolMonitorAutoConfiguration {

    @Bean
    public ThreadPoolMonitorBeanPostProcessor threadPoolMonitorBeanPostProcessor(MeterRegistry meterRegistry) {
        return new ThreadPoolMonitorBeanPostProcessor(meterRegistry);
    }

    /**
     * BeanPostProcessor 用于自动扫描并监控所有 ThreadPoolExecutor Bean 和成员字段
     */
    static class ThreadPoolMonitorBeanPostProcessor implements BeanPostProcessor {

        private final MeterRegistry meterRegistry;
        private final Set<ThreadPoolExecutor> registeredExecutors = new HashSet<>();

        public ThreadPoolMonitorBeanPostProcessor(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            // 1. 如果 Bean 本身就是 ThreadPoolExecutor
            if (bean instanceof ThreadPoolExecutor executor) {
                registerExecutor(executor, beanName);
            }

            // 2. 扫描 Bean 的所有字段，查找 ThreadPoolExecutor 类型的成员变量
            scanFieldsForThreadPools(bean, beanName);

            return bean;
        }

        private void scanFieldsForThreadPools(Object bean, String beanName) {
            Class<?> clazz = bean.getClass();

            // 遍历当前类及其父类的所有字段
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (ThreadPoolExecutor.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            Object fieldValue = field.get(bean);

                            if (fieldValue instanceof ThreadPoolExecutor executor) {
                                String poolName = beanName + "." + field.getName();
                                registerExecutor(executor, poolName);
                            }
                        } catch (IllegalAccessException e) {
                            log.warn("Failed to access field {} in bean {}: {}", field.getName(), beanName, e.getMessage());
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }

        private void registerExecutor(ThreadPoolExecutor executor, String poolName) {
            // 避免重复注册同一个线程池实例
            if (registeredExecutors.contains(executor)) {
                log.debug("ThreadPoolExecutor {} already registered, skipping...", poolName);
                return;
            }

            log.info("Detected ThreadPoolExecutor: {}, registering metrics...", poolName);
            new ThreadPoolMetricsCollector(meterRegistry, poolName, executor);
            registeredExecutors.add(executor);
        }
    }
}
