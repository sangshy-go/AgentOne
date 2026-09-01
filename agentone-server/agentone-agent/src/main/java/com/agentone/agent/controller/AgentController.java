package com.agentone.agent.controller;

import com.agentone.agent.dto.AgentDTO;
import com.agentone.agent.dto.AgentUpdateDTO;
import com.agentone.agent.service.AgentService;
import com.agentone.agent.vo.AgentVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * Agent 管理 Controller
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public Result<PageResult<AgentVO>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(agentService.list(page, size));
    }

    @GetMapping("/{id}")
    public Result<AgentVO> getById(@PathVariable String id) {
        return Result.ok(agentService.getById(id));
    }

    @PostMapping
    public Result<AgentVO> create(@Valid @RequestBody AgentDTO dto) {
        return Result.ok(agentService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<AgentVO> update(@PathVariable String id, @Valid @RequestBody AgentUpdateDTO dto) {
        return Result.ok(agentService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        agentService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/stop")
    public Result<AgentVO> stop(@PathVariable String id) {
        return Result.ok(agentService.stop(id));
    }

    @PostMapping("/{id}/revert")
    public Result<AgentVO> revertToDraft(@PathVariable String id) {
        return Result.ok(agentService.revertToDraft(id));
    }
}
