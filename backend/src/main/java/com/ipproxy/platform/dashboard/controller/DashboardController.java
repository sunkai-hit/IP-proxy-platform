package com.ipproxy.platform.dashboard.controller;

import com.ipproxy.platform.common.api.ApiResponse; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestController @RequestMapping("/api/admin/v1/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc; public DashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/overview") public ApiResponse<Map<String,Object>> overview(){Map<String,Object> d=new LinkedHashMap<>();d.put("m1Status","READY");d.put("customerCount",jdbc.queryForObject("SELECT count(*) FROM customer WHERE deleted=FALSE",Long.class));d.put("userCount",jdbc.queryForObject("SELECT count(*) FROM sys_user WHERE deleted=FALSE",Long.class));d.put("database","PostgreSQL");d.put("cache","Redis");return ApiResponse.success(d);}
}
