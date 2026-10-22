package com.masters.socratesai.admin.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapConfiguration {
    @Bean ApplicationRunner adminBootstrap(AdminBootstrapService service) {
        return args -> service.initialize();
    }
}
