package com.agentone.agent.enums;

/**
 * Agent 状态转换动作
 */
public enum AgentAction {

    START_TEST("开始测试"),
    PUBLISH("发布"),
    STOP("停用"),
    REVERT_TO_DRAFT("退回草稿"),
    ARCHIVE("归档");

    private final String description;

    AgentAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
