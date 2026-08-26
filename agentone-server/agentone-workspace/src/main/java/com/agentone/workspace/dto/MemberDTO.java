package com.agentone.workspace.dto;

import lombok.Data;

/**
 * 成员管理 DTO：添加成员用 email+role，变更角色用 role
 */
@Data
public class MemberDTO {

    private String email;
    private String role;
}
