package com.agentone.im.controller;

import com.agentone.common.result.Result;
import com.agentone.im.dto.ImBotCreateDTO;
import com.agentone.im.dto.ImBotUpdateDTO;
import com.agentone.im.dto.ImSendDTO;
import com.agentone.im.service.ImBotService;
import com.agentone.im.vo.ImBotVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IM 机器人管理（JWT 鉴权 + 工作空间隔离；observer 写操作由 WorkspaceRbacFilter 统一拦截）
 */
@RestController
@RequestMapping("/api/im/bots")
@RequiredArgsConstructor
public class ImBotController {

    private final ImBotService imBotService;

    @GetMapping
    public Result<List<ImBotVO>> list() {
        return Result.ok(imBotService.list());
    }

    @PostMapping
    public Result<ImBotVO> create(@Valid @RequestBody ImBotCreateDTO dto) {
        return Result.ok(imBotService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<ImBotVO> update(@PathVariable String id, @Valid @RequestBody ImBotUpdateDTO dto) {
        return Result.ok(imBotService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        imBotService.delete(id);
        return Result.ok();
    }

    /** 主动发送测试消息（仅钉钉 webhook 模式） */
    @PostMapping("/{id}/send")
    public Result<Void> send(@PathVariable String id, @Valid @RequestBody ImSendDTO dto) {
        imBotService.send(id, dto);
        return Result.ok();
    }
}
