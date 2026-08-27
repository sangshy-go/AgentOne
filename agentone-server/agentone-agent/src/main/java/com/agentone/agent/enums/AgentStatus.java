package com.agentone.agent.enums;

import com.agentone.common.exception.BusinessException;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Agent 状态枚举
 * 封装状态值和状态转换规则
 *
 * 发布审批（课题⑩）：发布不再可直达，DRAFT/TESTING 须先 SUBMIT_REVIEW 进入
 * PENDING_REVIEW，由他人 APPROVE 后才到 PUBLISHED（双人原则，提交人不可自审）。
 * PENDING_REVIEW 期间 Agent 冻结（不可编辑/删除/停用），保证「审什么 = 发什么」。
 */
public enum AgentStatus {

    DRAFT("draft", "草稿"),
    TESTING("testing", "测试中"),
    PENDING_REVIEW("pending_review", "审批中"),
    PUBLISHED("published", "已发布"),
    STOPPED("stopped", "已停用"),
    ARCHIVED("archived", "已归档");

    @EnumValue
    private final String code;
    private final String description;

    AgentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Jackson 序列化时使用 code（小写，如 "published"）而非枚举名（"PUBLISHED"），
     * 与数据库存储值和前端状态映射保持一致
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 状态转换
     * @param action 转换动作
     * @return 新状态
     */
    public AgentStatus transition(AgentAction action) {
        return switch (this) {
            case DRAFT -> switch (action) {
                case START_TEST -> TESTING;
                case SUBMIT_REVIEW -> PENDING_REVIEW;
                case ARCHIVE -> ARCHIVED;
                default -> throw new BusinessException(3004, "草稿状态不能执行: " + action.getDescription());
            };
            case TESTING -> switch (action) {
                case SUBMIT_REVIEW -> PENDING_REVIEW;
                case REVERT_TO_DRAFT -> DRAFT;
                case ARCHIVE -> ARCHIVED;
                default -> throw new BusinessException(3004, "测试中状态不能执行: " + action.getDescription());
            };
            case PENDING_REVIEW -> switch (action) {
                case APPROVE -> PUBLISHED;
                case REJECT -> DRAFT;
                case WITHDRAW -> DRAFT;
                default -> throw new BusinessException(3004, "审批中状态不能执行: " + action.getDescription());
            };
            case PUBLISHED -> switch (action) {
                case STOP -> STOPPED;
                default -> throw new BusinessException(3004, "已发布状态不能执行: " + action.getDescription());
            };
            case STOPPED -> switch (action) {
                case REVERT_TO_DRAFT -> DRAFT;
                case ARCHIVE -> ARCHIVED;
                default -> throw new BusinessException(3004, "已停用状态不能执行: " + action.getDescription());
            };
            case ARCHIVED -> throw new BusinessException(3004, "已归档状态不能执行任何操作");
        };
    }

    /**
     * 是否可编辑（只有草稿和测试中可以修改配置；审批中冻结，保证审什么 = 发什么）
     */
    public boolean isEditable() {
        return this == DRAFT || this == TESTING;
    }
}
