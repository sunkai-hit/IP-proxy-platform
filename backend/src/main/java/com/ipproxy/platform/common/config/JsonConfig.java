package com.ipproxy.platform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonConfig {
    @Bean
    ObjectMapper legacyObjectMapper() {
        return new ObjectMapper();
    }
}
