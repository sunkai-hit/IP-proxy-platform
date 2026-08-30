package com.ipproxy.platform.security;

import java.util.List;

public record UserPrincipal(Long userId,String username,String displayName,List<String> roles,List<String> permissions) {}
