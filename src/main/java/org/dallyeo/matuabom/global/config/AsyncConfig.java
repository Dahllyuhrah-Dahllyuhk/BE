package org.dallyeo.matuabom.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "googleSyncExecutor")
    public Executor googleSyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("google-sync-");
        ex.setRejectedExecutionHandler(loggingRejectedExecutionHandler());
        ex.initialize();
        return ex;
    }

    private RejectedExecutionHandler loggingRejectedExecutionHandler() {
        return (r, executor) ->
            log.warn("Google sync task rejected — queue full. coreSize={}, activeCount={}, queueSize={}",
                    executor.getCorePoolSize(),
                    executor.getActiveCount(),
                    executor.getQueue().size());
    }
}