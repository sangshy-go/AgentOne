package com.agentone.skill.core;

import com.agentone.common.context.Context;

/**
 * Skill 执行器接口
 * 所有 Skill（内置/API/MCP）都实现此接口
 */
public interface SkillExecutor {

    /**
     * 执行 Skill
     *
     * @param invocation 调用参数
     * @param context    运行时上下文
     * @return 执行结果
     */
    SkillResult execute(SkillInvocation invocation, Context context);

    /**
     * 获取 Skill 描述
     */
    SkillDescriptor getDescriptor();

    /**
     * 健康检查
     *
     * @return 是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}
