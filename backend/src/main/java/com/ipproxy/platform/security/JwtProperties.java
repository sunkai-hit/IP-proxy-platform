package com.ipproxy.platform.security;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="app.jwt")
public record JwtProperties(String secret, long expirationSeconds) {}
