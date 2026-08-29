<template>
  <div class="agent-detail-page">
    <!-- Header Card -->
    <div class="detail-header">
      <div class="ad-avatar">
        {{ agent?.name?.charAt(0) || 'A' }}
      </div>
      <div class="ad-info">
        <h1 class="ad-name">{{ agent?.name || '加载中...' }}</h1>
        <div class="ad-desc">{{ agent?.description || '暂无描述' }}</div>
        <div class="ad-meta">
          <div :class="['status-pill', `status-${agent?.status || 'draft'}`]">
            <span class="status-dot"></span>
            {{ statusLabel(agent?.status || 'draft') }}
          </div>
          <div class="badge badge-neutral">v{{ agent?.currentVersion || 1 }}</div>
        </div>
        <div v-if="agent?.status === 'pending_review'" class="ad-review-hint">
          审批中：配置已冻结（审什么 = 发什么），可继续测试对话；如需修改请先撤回申请。
        </div>
      </div>
      <div class="ad-actions">
        <!-- 发布前为「测试对话」、发布后为「试用对话」；停用/归档后端禁止对话（3003），无需暴露入口 -->
        <button
          v-if="canChat"
          class="btn-test-chat"
          @click="chatDrawerVisible = true"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
          </svg>
          {{ chatLabel }}
        </button>
        <button
          v-if="agent?.status === 'pending_review' && isSubmitter"
          class="btn-secondary-custom"
          @click="handleWithdraw"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 14L4 9l5-5" /><path d="M4 9h10a6 6 0 010 12h-3" />
          </svg>
          撤回申请
        </button>
        <button
          class="btn-gradient"
          @click="handleSubmitReview"
          :disabled="agent?.status === 'published' || agent?.status === 'pending_review'"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M22 2L11 13" /><path d="M22 2l-7 20-4-9-9-4 20-7z" />
          </svg>
          提交发布审批
        </button>
        <button class="btn-secondary-custom" @click="router.push('/agents')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12" /><polyline points="12 19 5 12 12 5" />
          </svg>
          返回
        </button>
      </div>
    </div>

    <!-- Pill Tabs -->
    <div class="tab-pills">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-pill"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </div>
    </div>

    <!-- Tab Content -->
    <div class="page-card">
      <PersonaTab v-if="activeTab === 'persona'" :agent="agent" @save="handleSave" />
      <ModelTab v-if="activeTab === 'model'" :agent="agent" @save="handleSave" />
      <SkillsTab v-if="activeTab === 'skills'" :agent="agent" />
      <KnowledgeTab v-if="activeTab === 'knowledge'" :agent="agent" />
      <MemoryTab v-if="activeTab === 'memory'" :agent="agent" @save="handleSave" />
      <AdvancedTab v-if="activeTab === 'advanced'" :agent="agent" @save="handleSave" />
    </div>

    <!-- Chat Drawer -->
    <ChatDrawer v-model:visible="chatDrawerVisible" :agent="agent" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { Agent } from '@/types'
import { getAgent, updateAgent } from '@/services/agent'
import {
  submitPublishRequest,
  listPublishRequests,
  withdrawPublishRequest,
} from '@/services/approval'
import { useAuthStore } from '@/stores/auth'
import PersonaTab from './tabs/PersonaTab.vue'
import ModelTab from './tabs/ModelTab.vue'
import SkillsTab from './tabs/SkillsTab.vue'
import KnowledgeTab from './tabs/KnowledgeTab.vue'
import MemoryTab from './tabs/MemoryTab.vue'
import AdvancedTab from './tabs/AdvancedTab.vue'
import ChatDrawer from '@/components/ChatDrawer.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const agent = ref<Agent | null>(null)
const activeTab = ref('persona')
const chatDrawerVisible = ref(false)
/** 当前 pending 申请单（用于判断「撤回」按钮可见性：仅提交人本人可撤回） */
const pendingRequestId = ref('')
const isSubmitter = ref(false)

const tabs = [
  { key: 'persona', label: '人格与指令' },
  { key: 'model', label: '模型策略' },
  { key: 'skills', label: '能力绑定' },
  { key: 'knowledge', label: '知识库' },
  { key: 'memory', label: '记忆' },
  { key: 'advanced', label: '高级' },
]

/**
 * 对话入口可见性（与后端 ChatServiceImpl.loadAgent 放行范围一致）：
 * - draft/testing/pending_review → 「测试对话」（发布前验证；审批中提示文案承诺可继续测试）
 * - published → 「试用对话」（控制台是发布后最基础的消费渠道，API/IM 是附加渠道而非替代）
 * - stopped/archived → 隐藏（后端统一 3003 拒绝对话）
 */
const CHATTABLE_STATUSES = ['draft', 'testing', 'pending_review', 'published']
const canChat = computed(() => !!agent.value && CHATTABLE_STATUSES.includes(agent.value.status))
const chatLabel = computed(() => (agent.value?.status === 'published' ? '试用对话' : '测试对话'))

function statusLabel(status: string) {
  const map: Record<string, string> = {
    published: '已发布',
    pending_review: '审批中',
    testing: '测试中',
    draft: '草稿',
    stopped: '已停用',
    archived: '已归档',
  }
  return map[status] || status
}

async function loadAgent() {
  const id = route.params.id as string
  try {
    const res = await getAgent(id)
    agent.value = res.data.data
    await loadPendingRequest()
  } catch {
    message.error('加载 Agent 失败')
  }
}

/** 审批中时拉取本 Agent 的 pending 申请，判定当前用户是否为提交人 */
async function loadPendingRequest() {
  pendingRequestId.value = ''
  isSubmitter.value = false
  if (agent.value?.status !== 'pending_review') return
  try {
    const res = await listPublishRequests({ agentId: agent.value.id, status: 'pending', page: 1, size: 1 })
    const record = res.data.data?.records?.[0]
    if (record) {
      pendingRequestId.value = record.id
      isSubmitter.value = record.submitterId === authStore.userId
    }
  } catch {
    /* 申请单信息加载失败不影响详情页主流程 */
  }
}

async function handleSave(data: Partial<Agent>) {
  if (!agent.value) return
  try {
    const res = await updateAgent(agent.value.id, data)
    agent.value = res.data.data
    message.success('保存成功')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '保存失败')
  }
}

/** 课题⑩：发布唯一路径 = 提交审批，由他人复核通过后上线 */
async function handleSubmitReview() {
  if (!agent.value) return
  try {
    await submitPublishRequest(agent.value.id)
    agent.value.status = 'pending_review'
    await loadPendingRequest()
    message.success('已提交发布审批，等待管理员审核')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '提交审批失败')
  }
}

async function handleWithdraw() {
  if (!pendingRequestId.value) return
  try {
    await withdrawPublishRequest(pendingRequestId.value)
    if (agent.value) agent.value.status = 'draft'
    pendingRequestId.value = ''
    isSubmitter.value = false
    message.success('申请已撤回，Agent 回到草稿状态')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '撤回失败')
  }
}

onMounted(loadAgent)
</script>

<style scoped>
.agent-detail-page {
  animation: pageIn 0.4s ease;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: 24px;
  box-shadow: var(--shadow);
}

.ad-avatar {
  width: 64px;
  height: 64px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
  font-weight: 800;
  background: var(--grad-primary);
  box-shadow: var(--glow-primary-md);
  flex-shrink: 0;
}

.ad-info {
  flex: 1;
}

.ad-name {
  font-size: 22px;
  font-weight: 800;
  color: var(--text);
  margin-bottom: 4px;
  letter-spacing: -0.3px;
}

.ad-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.ad-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ad-review-hint {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--orange-bg);
  border: 1px solid var(--orange-border);
  color: var(--orange);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}

.ad-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.btn-secondary-custom {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 18px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid var(--border);
  background: #FFFFFF;
  color: var(--text);
  transition: var(--transition);
}

.btn-secondary-custom:hover {
  border-color: var(--primary-border);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.btn-test-chat {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 18px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: 1.5px solid var(--primary);
  background: var(--indigo-bg);
  color: var(--primary);
  transition: var(--transition);
}

.btn-test-chat:hover {
  background: var(--grad-primary-2);
  color: white;
  border-color: transparent;
  box-shadow: var(--glow-primary-md);
  transform: translateY(-1px);
}

/* Pill Tabs */
.tab-pills {
  display: flex;
  gap: 6px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.tab-pill {
  padding: 8px 18px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  background: #FFFFFF;
  border: 1px solid var(--border);
  transition: var(--transition);
}

.tab-pill:hover {
  color: var(--primary);
  border-color: var(--primary-border);
}

.tab-pill.active {
  background: var(--grad-primary-2);
  color: white;
  border-color: transparent;
  box-shadow: var(--glow-primary);
}
</style>
