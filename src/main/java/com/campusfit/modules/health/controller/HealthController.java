package com.campusfit.modules.health.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.health.vo.HealthStatusVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping
    public ApiResponse<HealthStatusVO> health() {
        HealthStatusVO data = new HealthStatusVO(applicationName, "UP", Runtime.version().toString());
        return ApiResponse.success(data);
    }
}
