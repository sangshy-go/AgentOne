package com.agentone.knowledge.controller;

import com.agentone.common.result.Result;
import com.agentone.knowledge.dto.ModelDTO;
import com.agentone.knowledge.service.ModelService;
import com.agentone.knowledge.vo.ModelVO;
import com.agentone.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型 Controller
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    /**
     * 创建模型
     */
    @PostMapping("/providers/{providerId}/models")
    public Result<ModelVO> createModel(
            @PathVariable String providerId,
            @Valid @RequestBody ModelDTO dto) {
        return Result.ok(modelService.createModel(providerId, dto));
    }

    /**
     * 获取 provider 下的所有模型
     */
    @GetMapping("/providers/{providerId}/models")
    public Result<PageResult<ModelVO>> listModels(
            @PathVariable String providerId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(modelService.listModels(providerId, page, size));
    }

    /**
     * 获取模型详情
     */
    @GetMapping("/{modelId}")
    public Result<ModelVO> getModel(@PathVariable String modelId) {
        return Result.ok(modelService.getModel(modelId));
    }

    /**
     * 更新模型
     */
    @PutMapping("/{modelId}")
    public Result<ModelVO> updateModel(
            @PathVariable String modelId,
            @Valid @RequestBody ModelDTO dto) {
        return Result.ok(modelService.updateModel(modelId, dto));
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{modelId}")
    public Result<Void> deleteModel(@PathVariable String modelId) {
        modelService.deleteModel(modelId);
        return Result.ok();
    }

    /**
     * 按类型获取模型列表（用于知识库选择 embedding 模型）
     */
    @GetMapping("/by-type/{modelType}")
    public Result<List<ModelVO>> listModelsByType(@PathVariable String modelType) {
        return Result.ok(modelService.listModelsByType(modelType));
    }
}
