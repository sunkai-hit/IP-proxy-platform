package com.ipproxy.platform.system.auth.controller;

import com.ipproxy.platform.common.api.ApiResponse; import com.ipproxy.platform.security.UserPrincipal; import com.ipproxy.platform.system.auth.dto.*; import com.ipproxy.platform.system.auth.service.AuthService; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/v1/auth")
public class AuthController {
    private final AuthService authService; public AuthController(AuthService authService){this.authService=authService;}
    @PostMapping("/login") public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,HttpServletRequest http){return ApiResponse.success(authService.login(request,http.getRemoteAddr(),http.getHeader("User-Agent")));}
    @GetMapping("/me") public ApiResponse<LoginResponse.UserInfo> me(@AuthenticationPrincipal UserPrincipal p){return ApiResponse.success(new LoginResponse.UserInfo(p.userId(),p.username(),p.displayName(),p.roles(),p.permissions()));}
}
