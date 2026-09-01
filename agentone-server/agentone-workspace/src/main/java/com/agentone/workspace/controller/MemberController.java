package com.agentone.workspace.controller;

import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import com.agentone.workspace.dto.MemberDTO;
import com.agentone.workspace.service.MemberService;
import com.agentone.workspace.vo.MemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作空间成员管理（当前工作空间范围）。
 * 管理员门禁（仅 admin/owner 可访问 /api/members/**）由 WorkspaceRbacFilter 统一拦截。
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public Result<PageResult<MemberVO>> list(@RequestParam(defaultValue = "1") Integer current,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(memberService.listMembers(current, size));
    }

    @PostMapping
    public Result<MemberVO> add(@RequestBody MemberDTO dto) {
        return Result.ok(memberService.addMember(dto));
    }

    @PutMapping("/{userId}")
    public Result<Void> updateRole(@PathVariable String userId, @RequestBody MemberDTO dto) {
        memberService.updateRole(userId, dto);
        return Result.ok();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable String userId) {
        memberService.removeMember(userId);
        return Result.ok();
    }
}
