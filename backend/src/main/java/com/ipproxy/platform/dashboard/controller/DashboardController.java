package com.ipproxy.platform.dashboard.controller;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.runtime.StatisticsService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/dashboard")
public class DashboardController {
    private final StatisticsService statistics;
    public DashboardController(StatisticsService statistics){this.statistics=statistics;}
    @GetMapping("/overview") public ApiResponse<Map<String,Object>> overview(){return ApiResponse.success(statistics.dashboard());}
}
