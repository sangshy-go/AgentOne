package com.agentone.skill.mapper;

import com.agentone.skill.entity.SkillDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Skill Mapper
 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillDO> {

    /**
     * 启动时加载全部工作空间的 active API Skill（此时无请求上下文，无法走租户过滤）。
     * 仅供 ApiSkillBootstrap 调用；请求链路中的查询一律走 BaseMapper（租户拦截生效）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM skill WHERE type = 'api' AND status = 'active'")
    List<SkillDO> selectAllActiveApiSkills();
}
