package com.netflix.gateway.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;

@Configuration
public class StructuredConcurrencyConfig {

    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    @Primary
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public StructuredTaskScope.Builder structuredTaskScopeBuilder() {
        return new StructuredTaskScope.Builder();
    }

    @Bean
    public StructuredTaskScope.ShutdownOnFailure shutdownOnFailureScope() {
        return new StructuredTaskScope.ShutdownOnFailure();
    }

    @Bean
    public StructuredTaskScope.ShutdownOnSuccess<?> shutdownOnSuccessScope() {
        return new StructuredTaskScope.ShutdownOnSuccess<>();
    }
}