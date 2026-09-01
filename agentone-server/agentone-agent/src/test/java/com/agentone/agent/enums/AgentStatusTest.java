package com.agentone.agent.enums;

import com.agentone.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentStatus 状态机单测（课题⑩：发布改为审批流后的迁移规则）
 */
class AgentStatusTest {

    @Test
    void transition_draftSubmitReview_pendingReview() {
        assertEquals(AgentStatus.PENDING_REVIEW, AgentStatus.DRAFT.transition(AgentAction.SUBMIT_REVIEW));
    }

    @Test
    void transition_testingSubmitReview_pendingReview() {
        assertEquals(AgentStatus.PENDING_REVIEW, AgentStatus.TESTING.transition(AgentAction.SUBMIT_REVIEW));
    }

    @Test
    void transition_pendingReviewApprove_published() {
        assertEquals(AgentStatus.PUBLISHED, AgentStatus.PENDING_REVIEW.transition(AgentAction.APPROVE));
    }

    @Test
    void transition_pendingReviewReject_draft() {
        assertEquals(AgentStatus.DRAFT, AgentStatus.PENDING_REVIEW.transition(AgentAction.REJECT));
    }

    @Test
    void transition_pendingReviewWithdraw_draft() {
        assertEquals(AgentStatus.DRAFT, AgentStatus.PENDING_REVIEW.transition(AgentAction.WITHDRAW));
    }

    @Test
    void transition_pendingReviewFreeze_stopAndRevertRejected() {
        // 审批中冻结：不可停用/退回/归档，只能 通过/驳回/撤回
        assertThrows(BusinessException.class, () -> AgentStatus.PENDING_REVIEW.transition(AgentAction.STOP));
        assertThrows(BusinessException.class, () -> AgentStatus.PENDING_REVIEW.transition(AgentAction.REVERT_TO_DRAFT));
        assertThrows(BusinessException.class, () -> AgentStatus.PENDING_REVIEW.transition(AgentAction.ARCHIVE));
    }

    @Test
    void publishAction_removed_noDirectPublishBypass() {
        // PUBLISH 直达动作已删除：发布唯一路径 = 审批，不存在绕过审批的动作
        assertFalse(hasPublishAction(), "PUBLISH 动作应已删除");
    }

    @Test
    void transition_publishedStop_stopped() {
        assertEquals(AgentStatus.STOPPED, AgentStatus.PUBLISHED.transition(AgentAction.STOP));
    }

    @Test
    void transition_stoppedRevert_draft() {
        assertEquals(AgentStatus.DRAFT, AgentStatus.STOPPED.transition(AgentAction.REVERT_TO_DRAFT));
    }

    @Test
    void transition_archived_anyActionThrows() {
        assertThrows(BusinessException.class, () -> AgentStatus.ARCHIVED.transition(AgentAction.SUBMIT_REVIEW));
    }

    @Test
    void isEditable_pendingReviewFalse() {
        assertTrue(AgentStatus.DRAFT.isEditable());
        assertTrue(AgentStatus.TESTING.isEditable());
        assertFalse(AgentStatus.PENDING_REVIEW.isEditable(), "审批中冻结编辑，保证审什么 = 发什么");
        assertFalse(AgentStatus.PUBLISHED.isEditable());
    }

    private boolean hasPublishAction() {
        for (AgentAction action : AgentAction.values()) {
            if (action.name().equals("PUBLISH")) {
                return true;
            }
        }
        return false;
    }
}
