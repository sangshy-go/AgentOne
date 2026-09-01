package com.agentone.workspace.service;

import com.agentone.common.result.PageResult;
import com.agentone.workspace.dto.MemberDTO;
import com.agentone.workspace.vo.MemberVO;

/**
 * 工作空间成员管理服务（当前工作空间范围）
 */
public interface MemberService {

    PageResult<MemberVO> listMembers(Integer current, Integer size);

    MemberVO addMember(MemberDTO dto);

    void updateRole(String userId, MemberDTO dto);

    void removeMember(String userId);
}
