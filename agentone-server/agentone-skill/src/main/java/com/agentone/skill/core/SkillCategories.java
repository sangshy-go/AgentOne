package com.agentone.skill.core;

/**
 * Skill 工种分类（Skill 中心 v2）。
 *
 * v2 起按「工种」而非业务场景组织技能：市场/销售/客服/人事/财务/法务合规/
 * 行政/数据分析/IT集成/其他。完整受控词表由前端选择器维护，后端不做强枚举校验
 * （词表可配置化在 v1.1）；本类只定义系统侧需要的固定值：
 * 用户 Skill 缺省分类与虚拟挂载技能（builtin/mcp）的默认归类。
 */
public final class SkillCategories {

    /** 用户 Skill 未指定分类时的缺省值 */
    public static final String DEFAULT = "其他";

    /** IT/集成类工具（HTTP 请求、代码执行、知识库检索、MCP 工具、API 封装的典型归属） */
    public static final String IT = "IT集成";

    private SkillCategories() {
    }
}
