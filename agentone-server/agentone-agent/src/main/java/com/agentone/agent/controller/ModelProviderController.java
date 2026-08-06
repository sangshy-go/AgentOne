package com.agentone.agent.controller;

import com.agentone.agent.dto.ModelProviderDTO;
import com.agentone.agent.service.ModelProviderService;
import com.agentone.agent.vo.ModelCheckVO;
import com.agentone.agent.vo.ModelProviderVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型供应商管理接口
 */
@RestController
@RequestMapping("/api/model-providers")
@RequiredArgsConstructor
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    @PostMapping
    public Result<ModelProviderVO> create(@Valid @RequestBody ModelProviderDTO dto) {
        return Result.success(modelProviderService.create(dto));
    }

    @GetMapping
    public Result<PageResult<ModelProviderVO>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(modelProviderService.list(page, size));
    }

    @GetMapping("/{id}")
    public Result<ModelProviderVO> getById(@PathVariable String id) {
        return Result.success(modelProviderService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<ModelProviderVO> update(@PathVariable String id, @Valid @RequestBody ModelProviderDTO dto) {
        return Result.success(modelProviderService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        modelProviderService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/check")
    public Result<ModelCheckVO> check(@PathVariable String id) {
        return Result.success(modelProviderService.check(id));
    }
}
