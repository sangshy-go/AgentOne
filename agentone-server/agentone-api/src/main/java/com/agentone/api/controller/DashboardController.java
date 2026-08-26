package com.agentone.api.controller;

import com.agentone.api.service.DashboardService;
import com.agentone.api.vo.DashboardVO;
import com.agentone.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控仪表盘
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardVO> stats() {
        return Result.ok(dashboardService.getStats());
    }
}
