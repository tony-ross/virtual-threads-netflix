package com.netflix.reviews.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class VirtualThreadConfig {

    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new VirtualThreadTaskExecutor("reviews-async-");
    }

    @Bean
    public VirtualThreadTaskExecutor graphqlExecutor() {
        return new VirtualThreadTaskExecutor("reviews-graphql-");
    }
}