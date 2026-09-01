package com.agentone.workspace.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间成员 VO
 */
@Data
public class MemberVO {

    private String userId;
    private String email;
    private String nickname;
    private String role;
    private LocalDateTime joinedAt;
}
