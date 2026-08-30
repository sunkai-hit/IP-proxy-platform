package com.ipproxy.platform.system.auth.dto;
import java.util.List;
public record LoginResponse(String accessToken,String tokenType,long expiresIn,UserInfo user){public record UserInfo(Long id,String username,String displayName,List<String> roles){}}
