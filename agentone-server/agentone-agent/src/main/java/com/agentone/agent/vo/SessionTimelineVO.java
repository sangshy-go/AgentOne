package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话时间线：消息与 Skill 调用按发生时间归并（Skill 调用链视图）
 */
@Data
public class SessionTimelineVO {

    private MonitorSessionVO session;
    private List<TimelineItem> items;

    @Data
    public static class TimelineItem {
        /** message / skill_call */
        private String kind;
        private String id;
        /** message: user/assistant/system/tool */
        private String role;
        private String content;
        private String skillId;
        private String skillName;
        /** skill_call: success/failure */
        private String status;
        private String errorMessage;
        private Long durationMs;
        private Integer tokenCount;
        private LocalDateTime createdAt;
    }
}
