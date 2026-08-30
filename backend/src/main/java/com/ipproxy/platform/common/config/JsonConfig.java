package com.ipproxy.platform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonConfig {
    @Bean
    ObjectMapper legacyObjectMapper() {
        // M3 起显式提供 ObjectMapper；M6 的订单/服务快照开始包含 OffsetDateTime，
        // 因此必须注册 classpath 中的 Java Time 等标准 Jackson 模块。
        return new ObjectMapper().findAndRegisterModules();
    }
}
