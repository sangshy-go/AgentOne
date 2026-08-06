package com.agentone.agent.controller;

import com.agentone.agent.dto.ModelCheckDTO;
import com.agentone.agent.service.ModelCheckService;
import com.agentone.agent.vo.ModelCheckVO;
import com.agentone.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 模型检测 Controller
 */
@RestController
@RequestMapping("/api/model-check")
@RequiredArgsConstructor
public class ModelCheckController {

    private final ModelCheckService modelCheckService;

    /**
     * 检测模型可用性
     */
    @PostMapping("/check")
    public Result<ModelCheckVO> checkModel(@Valid @RequestBody ModelCheckDTO dto) {
        return Result.ok(modelCheckService.checkModel(dto));
    }
}
