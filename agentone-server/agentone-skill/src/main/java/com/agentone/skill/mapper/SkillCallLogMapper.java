package com.agentone.skill.mapper;

import com.agentone.skill.entity.SkillCallLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Skill 调用日志 Mapper
 */
@Mapper
public interface SkillCallLogMapper extends BaseMapper<SkillCallLogDO> {

    /**
     * 按技能聚合调用次数（Skill 中心 v2「使用次数」统计口径：真实调用计数）。
     * 租户拦截器自动追加 workspace_id 过滤，只统计当前工作空间。
     */
    @Select("SELECT skill_id AS \"skillId\", COUNT(*) AS \"callCount\" "
            + "FROM skill_call_log GROUP BY skill_id")
    List<Map<String, Object>> countCallsGroupBySkill();
}
