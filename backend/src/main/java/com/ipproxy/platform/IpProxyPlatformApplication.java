package com.ipproxy.platform;

import com.ipproxy.platform.security.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan({"com.ipproxy.platform.system.user.mapper","com.ipproxy.platform.customer.mapper"})
@EnableConfigurationProperties(JwtProperties.class)
public class IpProxyPlatformApplication {
    public static void main(String[] args) { SpringApplication.run(IpProxyPlatformApplication.class, args); }
}
