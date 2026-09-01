<template>
  <div class="session-timeline">
    <div class="tl-header">
      <n-button size="small" quaternary @click="emit('back')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7" />
        </svg>
        返回会话列表
      </n-button>
      <div v-if="timeline" class="tl-session-meta">
        <span class="tl-title">{{ timeline.session.title || '未命名会话' }}</span>
        <n-tag size="small" :bordered="false">{{ timeline.session.agentName || '未知 Agent' }}</n-tag>
        <span class="tl-muted">{{ timeline.session.userEmail }} · {{ timeline.session.messageCount }} 条消息 · {{ formatTime(timeline.session.createdAt) }}</span>
      </div>
    </div>

    <n-spin :show="loading">
      <div v-if="timeline && timeline.items.length > 0" class="tl-feed">
        <div v-for="item in timeline.items" :key="item.kind + '-' + item.id" class="tl-item">
          <div class="tl-time">{{ formatTime(item.createdAt) }}</div>

          <!-- 消息：user 靠右，assistant 靠左，system/tool 居中弱化 -->
          <div v-if="item.kind === 'message'" class="tl-msg" :class="'role-' + (item.role || 'system')">
            <div class="tl-msg-bubble">
              <div class="tl-msg-role">{{ roleLabel(item.role) }}</div>
              <div class="tl-msg-content">{{ item.content }}</div>
            </div>
          </div>

          <!-- Skill 调用卡片 -->
          <div v-else class="tl-skill">
            <div class="tl-skill-head">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
              </svg>
              <span class="tl-skill-name">{{ item.skillName || item.skillId }}</span>
              <n-tag :type="item.status === 'success' ? 'success' : 'error'" size="small" round>
                {{ item.status === 'success' ? '成功' : '失败' }}
              </n-tag>
              <span class="tl-muted">{{ item.durationMs ?? 0 }}ms</span>
            </div>
            <div v-if="item.errorMessage" class="tl-skill-error">{{ item.errorMessage }}</div>
          </div>
        </div>
      </div>
      <n-empty v-else-if="!loading" description="该会话暂无消息记录" style="margin-top: 40px;" />
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { NButton, NTag, NSpin, NEmpty, useMessage } from 'naive-ui'
import type { SessionTimeline } from '@/types'
import { getSessionTimeline } from '@/services/monitor'

const props = defineProps<{ sessionId: string }>()
const emit = defineEmits<{ (e: 'back'): void }>()

const message = useMessage()
const loading = ref(false)
const timeline = ref<SessionTimeline | null>(null)

watch(
  () => props.sessionId,
  async (id) => {
    if (!id) return
    loading.value = true
    try {
      const res = await getSessionTimeline(id)
      timeline.value = res.data.data
    } catch (e: any) {
      message.error(e?.message || '加载会话时间线失败')
      timeline.value = null
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)

function roleLabel(role?: string) {
  const map: Record<string, string> = { user: '用户', assistant: 'Agent', system: '系统', tool: '工具' }
  return map[role || ''] || '系统'
}

function formatTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 19) : ''
}
</script>

<style scoped>
.tl-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.tl-session-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.tl-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.tl-muted {
  font-size: 12px;
  color: var(--text-muted);
}

.tl-feed {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tl-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tl-time {
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
}

.tl-msg {
  display: flex;
}

.tl-msg.role-user {
  justify-content: flex-end;
}

.tl-msg.role-system,
.tl-msg.role-tool {
  justify-content: center;
}

.tl-msg-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: var(--radius);
  background: var(--surface, #fff);
  border: 1px solid var(--border);
}

.role-user .tl-msg-bubble {
  background: var(--primary-bg);
  border-color: var(--primary-border);
}

.role-system .tl-msg-bubble,
.role-tool .tl-msg-bubble {
  max-width: 85%;
  background: transparent;
  border-style: dashed;
  opacity: 0.85;
}

.tl-msg-role {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.tl-msg-content {
  font-size: 13px;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.tl-skill {
  align-self: center;
  width: min(560px, 90%);
  border: 1px solid var(--border);
  border-left: 3px solid var(--purple, #8b5cf6);
  border-radius: var(--radius);
  padding: 10px 14px;
  background: var(--surface, #fff);
}

.tl-skill-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--purple, #8b5cf6);
}

.tl-skill-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.tl-skill-error {
  margin-top: 6px;
  font-size: 12px;
  color: var(--red, #ef4444);
  word-break: break-word;
}
</style>
