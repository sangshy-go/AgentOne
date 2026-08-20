package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 代码执行 Skill（安全桩 / 占位实现）
 *
 * ⚠️ 安全说明（银行 / 国企级合规要求）：
 *  - 进程内执行 LLM 生成的任意代码存在远程代码执行（RCE）风险（JDK 17 已移除 Nashorn，旧实现 100% 失败且不安全）。
 *  - 本技能默认禁用（enabled=false）。即使被显式开启，也不会在进程内执行任何代码，
 *    而是返回明确说明，避免：① JDK 17 下的崩溃；② 任意代码执行的攻击面。
 *  - 真正可用的代码执行应通过独立 Docker 沙箱进程实现（已在路线图中规划），届时由沙箱服务接收
 *    代码与输入、隔离执行、回收结果，本类仅作为调用入口。
 *
 * 输入参数（仅做声明，不实际执行）:
 * - language: 编程语言（目前声明支持 javascript）
 * - code: 代码内容
 * - variables: 输入变量（可选）
 */
@Slf4j
@Component
public class CodeExecuteSkill implements SkillExecutor {

    private static final String SKILL_ID = "builtin-code-execute";

    private static final String DISABLED_MESSAGE =
            "代码执行技能当前为安全禁用状态：出于安全合规要求，平台不在进程内执行任意代码。"
            + "如需启用，请先接入隔离的 Docker 沙箱执行环境（参见部署文档与路线图），由沙箱服务完成代码执行与结果回收。";

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long startTime = System.currentTimeMillis();
        log.warn("代码执行技能被调用，但处于安全禁用状态（不执行任何代码）");
        return SkillResult.failure(DISABLED_MESSAGE, System.currentTimeMillis() - startTime);
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(SKILL_ID)
                .name("代码执行")
                .description("执行 JavaScript 代码（安全禁用：需接入 Docker 沙箱后方可启用）")
                .type("builtin")
                .version("2.0.0")
                .source("agentone")
                .category(SkillCategories.IT)
                .enabled(false)
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "language", Map.of("type", "string", "enum", new String[]{"javascript", "js"}),
                                "code", Map.of("type", "string", "description", "代码内容"),
                                "variables", Map.of("type", "object", "description", "输入变量")
                        ),
                        "required", new String[]{"code"}
                ))
                .build();
    }
}
