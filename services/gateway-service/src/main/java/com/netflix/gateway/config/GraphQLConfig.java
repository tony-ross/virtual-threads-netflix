package com.netflix.gateway.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;


import java.util.List;

@Configuration
public class GraphQLConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.Date)
                .scalar(ExtendedScalars.DateTime);
    }

    @Bean
    public VirtualThreadTaskExecutor graphqlExecutor() {
        return new VirtualThreadTaskExecutor("gateway-graphql-");
    }
}
