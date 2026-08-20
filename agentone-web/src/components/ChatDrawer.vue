<template>
  <Teleport to="body">
    <!-- Backdrop -->
    <Transition name="fade">
      <div v-if="visible" class="chat-drawer-backdrop" @click="handleClose" />
    </Transition>

    <!-- Drawer -->
    <Transition name="slide-right">
      <div v-if="visible" class="chat-drawer">
        <!-- Header -->
        <div class="drawer-header">
          <div class="drawer-title">
            <div class="drawer-title-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
              </svg>
            </div>
            <div>
              <div class="drawer-title-text">对话测试</div>
              <div class="drawer-title-sub">{{ agent?.name || '' }}</div>
            </div>
          </div>
          <button class="drawer-close" @click="handleClose" title="关闭">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <!-- Body: Session List + Chat Area -->
        <div class="drawer-body">
          <!-- Session Sidebar -->
          <div class="session-sidebar">
            <div class="session-header">
              <span>会话列表</span>
              <button class="session-new" @click="handleNewSession" title="新建会话">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                </svg>
              </button>
            </div>
            <div class="session-list">
              <div
                v-for="session in sessions"
                :key="session.id"
                class="session-item"
                :class="{ active: currentSessionId === session.id }"
                @click="handleSelectSession(session.id)"
              >
                <div class="session-item-title">{{ session.title }}</div>
                <div class="session-item-meta">
                  {{ session.messageCount || 0 }} 条 · {{ formatTime(session.updatedAt) }}
                </div>
              </div>
              <div v-if="sessions.length === 0" class="session-empty">
                <div>暂无会话</div>
                <div class="session-empty-hint">发送消息开始对话</div>
              </div>
            </div>
            <div v-if="sessionTotal > sessionPageSize" class="session-pagination">
              <n-pagination
                :page="sessionPage"
                :page-size="sessionPageSize"
                :item-count="sessionTotal"
                @update:page="handleSessionPageChange"
              />
            </div>
          </div>

          <!-- Chat Area -->
          <div class="chat-area">
            <!-- Messages -->
            <div ref="messagesRef" class="messages-scroll">
              <div class="messages-container">
                <div v-if="messages.length === 0 && !streaming" class="messages-empty">
                  <div class="empty-icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
                      <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                    </svg>
                  </div>
                  <div class="empty-title">开始新对话</div>
                  <div class="empty-hint">向 {{ agent?.name }} 发送消息</div>
                </div>

                <!-- History messages: user right / assistant left -->
                <div
                  v-for="msg in messages"
                  :key="msg.id"
                  class="message"
                  :class="[`message-${msg.role}`]"
                >
                  <div class="message-avatar">
                    {{ msg.role === 'user' ? '我' : 'AI' }}
                  </div>
                  <div class="message-body">
                    <div class="message-role">{{ msg.role === 'user' ? '你' : agent?.name || '助手' }}</div>
                    <div class="message-bubble">
                      <template v-if="msg.role === 'user'">{{ msg.content }}</template>
                      <div v-else class="md-body" v-html="renderMarkdown(msg.content)" />
                    </div>
                    <!-- Skill calls visualization -->
                    <div v-if="msg.skillCalls" class="message-skills">
                      <div class="skill-call-header" @click="toggleSkillExpand(msg.id)">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                          <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
                        </svg>
                        <span>工具调用</span>
                        <svg
                          width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                          :style="{ transform: expandedSkills.has(msg.id) ? 'rotate(180deg)' : '' }"
                        >
                          <polyline points="6 9 12 15 18 9" />
                        </svg>
                      </div>
                      <div v-if="expandedSkills.has(msg.id)" class="skill-call-detail">
                        <pre>{{ formatSkillCalls(msg.skillCalls) }}</pre>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Streaming message -->
                <div v-if="streaming" class="message message-assistant">
                  <div class="message-avatar">AI</div>
                  <div class="message-body">
                    <div class="message-role">{{ agent?.name || '助手' }}</div>

                    <!-- Thinking process -->
                    <div v-if="streamingThinking" class="thinking-block" :class="{ collapsed: !thinkingOpen }">
                      <div class="thinking-header" @click="thinkingOpen = !thinkingOpen">
                        <svg
                          class="thinking-spark"
                          :class="{ active: !textStarted }"
                          width="13" height="13" viewBox="0 0 24 24" fill="currentColor"
                        >
                          <path d="M12 2l2.1 6.5L20 10l-5.9 1.5L12 18l-2.1-6.5L4 10l5.9-1.5L12 2z" />
                        </svg>
                        <span class="thinking-label">
                          {{ textStarted ? `已深度思考 ${(thinkingMs / 1000).toFixed(1)}s` : '思考中...' }}
                        </span>
                        <svg
                          class="thinking-chevron"
                          width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                        >
                          <polyline points="6 9 12 15 18 9" />
                        </svg>
                      </div>
                      <div v-show="thinkingOpen" class="thinking-content">{{ streamingThinking }}</div>
                    </div>

                    <div class="message-bubble">
                      <!-- Waiting dots: no thinking and no text yet -->
                      <span v-if="!textStarted && !streamingThinking" class="waiting-dots">
                        <span /><span /><span />
                      </span>
                      <template v-else>
                        <div class="md-body" v-html="renderMarkdown(streamingContent)" />
                        <span v-if="textStarted" class="streaming-cursor" />
                      </template>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Input Area -->
            <div class="input-area">
              <div class="input-wrapper">
                <textarea
                  ref="inputRef"
                  v-model="inputMessage"
                  class="chat-input"
                  placeholder="发送消息...（Enter 发送，Shift+Enter 换行）"
                  rows="1"
                  :disabled="streaming"
                  @keydown="handleInputKeydown"
                  @input="autoResizeInput"
                />
                <button
                  class="send-btn"
                  :class="{ active: canSend }"
                  :disabled="!canSend"
                  @click="handleSend"
                  title="发送"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
                  </svg>
                </button>
              </div>
              <div class="input-hint">
                <span v-if="streaming" class="streaming-indicator">
                  <span class="streaming-dot" /> {{ textStarted ? '正在生成...' : (streamingThinking ? '正在思考...' : '正在响应...') }}
                </span>
                <span v-else>{{ currentSessionId ? '继续对话' : '新建对话' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useMessage, NPagination } from 'naive-ui'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { Agent, ChatSession, ChatMessage } from '@/types'
import { chatStream, listSessions, getSessionMessages } from '@/services/chat'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  visible: boolean
  agent: Agent | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const message = useMessage()

const sessions = ref<ChatSession[]>([])
const sessionPage = ref(1)
const sessionPageSize = ref(20)
const sessionTotal = ref(0)
const messages = ref<ChatMessage[]>([])
const currentSessionId = ref<string | null>(null)
const inputMessage = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const streamingThinking = ref('')
const thinkingOpen = ref(true)
const thinkingStart = ref(0)
const thinkingMs = ref(0)
const expandedSkills = ref<Set<string>>(new Set())
const abortController = ref<AbortController | null>(null)

const authStore = useAuthStore()

/** C4: 中断在途 SSE 流并重置流式态（用于切换工作空间等场景） */
function stopStreaming() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  streaming.value = false
  streamingContent.value = ''
  streamingThinking.value = ''
}

// C4: 切换工作空间时，中止在途 SSE，避免把旧空间会话内容继续推流
watch(
  () => authStore.workspaceId,
  () => stopStreaming()
)

const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)

const canSend = computed(() => inputMessage.value.trim().length > 0 && !streaming.value)
const textStarted = computed(() => streamingContent.value.length > 0)

// 当 drawer 打开时，加载会话列表
watch(
  () => props.visible,
  async (v) => {
    if (v && props.agent) {
      sessionPage.value = 1
      await loadSessions()
      nextTick(() => inputRef.value?.focus())
    }
  }
)

// 切换会话时，加载消息
watch(currentSessionId, async (id) => {
  if (id) {
    await loadMessages(id)
  } else {
    messages.value = []
  }
})

async function loadSessions() {
  if (!props.agent) return
  try {
    const res = await listSessions(props.agent.id, sessionPage.value, sessionPageSize.value)
    const data = res.data.data
    sessions.value = data?.records || []
    sessionTotal.value = data?.total || 0
    // 默认选中第一个
    if (sessions.value.length > 0 && !currentSessionId.value) {
      currentSessionId.value = sessions.value[0].id
    }
  } catch {
    message.error('会话列表加载失败，请稍后重试')
  }
}

function handleSessionPageChange(p: number) {
  sessionPage.value = p
  loadSessions()
}

async function loadMessages(sessionId: string) {
  try {
    const res = await getSessionMessages(sessionId)
    messages.value = res.data.data || []
    await nextTick()
    scrollToBottom()
  } catch {
    messages.value = []
    message.error('消息加载失败，请稍后重试')
  }
}

function handleNewSession() {
  // 正在流式时先中断在途 SSE，否则旧会话的回复会串入新会话消息列表
  if (streaming.value) {
    stopStreaming()
  }
  currentSessionId.value = null
  messages.value = []
  inputMessage.value = ''
  nextTick(() => inputRef.value?.focus())
}

function handleSelectSession(sessionId: string) {
  // 正在流式且切换到不同会话时，先中断在途 SSE，避免旧会话内容污染新会话
  if (streaming.value && sessionId !== currentSessionId.value) {
    stopStreaming()
  }
  currentSessionId.value = sessionId
}

function handleClose() {
  // 如果正在流式，先中断
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
    streaming.value = false
  }
  emit('update:visible', false)
}

async function handleSend() {
  if (!canSend.value || !props.agent) return
  const text = inputMessage.value.trim()
  inputMessage.value = ''
  resetInput()

  // 乐观追加用户消息
  const tempId = `temp-${Date.now()}`
  messages.value.push({
    id: tempId,
    role: 'user',
    content: text,
    tokenCount: 0,
    skillCalls: null,
    durationMs: null,
    traceId: null,
    createdAt: new Date().toISOString(),
  } as ChatMessage)
  await nextTick()
  scrollToBottom()

  // 开始流式
  streaming.value = true
  streamingContent.value = ''
  streamingThinking.value = ''
  thinkingOpen.value = true
  thinkingStart.value = Date.now()
  thinkingMs.value = 0

  abortController.value = chatStream(
    props.agent.id,
    text,
    currentSessionId.value || undefined,
    // onDelta
    (chunk) => {
      // 正文开始输出时，自动折叠思考过程
      if (!streamingContent.value && streamingThinking.value) {
        thinkingOpen.value = false
        thinkingMs.value = Date.now() - thinkingStart.value
      }
      streamingContent.value += chunk
      scrollToBottom()
    },
    // onThinking
    (chunk) => {
      streamingThinking.value += chunk
      scrollToBottom()
    },
    // onDone
    async (sessionId) => {
      // 保存完整回复到消息列表
      messages.value.push({
        id: `msg-${Date.now()}`,
        role: 'assistant',
        content: streamingContent.value,
        tokenCount: 0,
        skillCalls: null,
        durationMs: null,
        traceId: null,
        createdAt: new Date().toISOString(),
      } as ChatMessage)
      streaming.value = false
      streamingContent.value = ''
      streamingThinking.value = ''
      abortController.value = null

      // 更新 sessionId（如果是新会话）
      if (sessionId && sessionId !== currentSessionId.value) {
        currentSessionId.value = sessionId
      }
      // 刷新会话列表
      await loadSessions()
    },
    // onError
    (err) => {
      message.error(`对话失败: ${err}`)
      streaming.value = false
      streamingContent.value = ''
      streamingThinking.value = ''
      abortController.value = null
    }
  )
}

function handleInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function autoResizeInput() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function resetInput() {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function toggleSkillExpand(msgId: string) {
  if (expandedSkills.value.has(msgId)) {
    expandedSkills.value.delete(msgId)
  } else {
    expandedSkills.value.add(msgId)
  }
}

/** Markdown 渲染（marked 解析 + DOMPurify 消毒，与 PersonaTab 预览一致） */
function renderMarkdown(content: string): string {
  if (!content) return ''
  const raw = marked.parse(content, { async: false, breaks: true }) as string
  return DOMPurify.sanitize(raw)
}

function formatSkillCalls(skillCalls: string): string {
  try {
    const parsed = JSON.parse(skillCalls)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return skillCalls
  }
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays}天前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// ESC 关闭
function handleEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    handleClose()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEsc)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleEsc)
  if (abortController.value) {
    abortController.value.abort()
  }
})
</script>

<style scoped>
/* Backdrop */
.chat-drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.3);
  backdrop-filter: blur(4px);
  z-index: 1000;
}

/* Drawer */
.chat-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(900px, 90vw);
  background: #F0F2F8;
  z-index: 1001;
  display: flex;
  flex-direction: column;
  box-shadow: -20px 0 60px rgba(15, 23, 42, 0.15);
  animation: slideInRight 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideInRight {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

/* Transitions */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.slide-right-enter-active, .slide-right-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from, .slide-right-leave-to {
  transform: translateX(100%);
}

/* Header */
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #FFFFFF;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer-title-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius);
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: var(--glow-primary);
}

.drawer-title-text {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.drawer-title-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 1px;
}

.drawer-close {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: #FFFFFF;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: var(--transition);
}

.drawer-close:hover {
  background: var(--red-bg);
  border-color: var(--red-border);
  color: var(--red);
}

/* Body */
.drawer-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* Session sidebar */
.session-sidebar {
  width: 220px;
  background: #FFFFFF;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.session-header {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.session-new {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: #FFFFFF;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: var(--transition);
}

.session-new:hover {
  background: var(--primary);
  border-color: var(--primary);
  color: white;
  box-shadow: var(--glow-primary);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: 2px;
  transition: var(--transition);
}

.session-item:hover {
  background: var(--surface-alt);
}

.session-item.active {
  background: var(--indigo-bg);
  border: 1px solid var(--indigo-border);
}

.session-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item-meta {
  font-size: 11px;
  color: var(--text-muted);
}

.session-empty {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-muted);
  font-size: 13px;
}

.session-empty-hint {
  font-size: 11px;
  margin-top: 4px;
  color: var(--text-placeholder);
}

/* Chat area */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.messages-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.messages-container {
  max-width: 720px;
  margin: 0 auto;
}

.messages-empty {
  text-align: center;
  padding: 80px 24px;
  color: var(--text-muted);
}

.empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--indigo-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  color: var(--primary);
}

.empty-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
}

.empty-hint {
  font-size: 13px;
  color: var(--text-muted);
}

/* Message — two-sided layout */
.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeIn 0.3s ease;
}

.message-user {
  flex-direction: row-reverse;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.message-user .message-avatar {
  background: var(--grad-primary-2);
  color: white;
  box-shadow: var(--glow-primary);
}

.message-assistant .message-avatar {
  background: #FFFFFF;
  color: var(--primary);
  border: 1.5px solid var(--indigo-border);
}

.message-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.message-user .message-body {
  align-items: flex-end;
}

.message-role {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.message-bubble {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: var(--radius);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.message-user .message-bubble {
  background: var(--grad-primary-2);
  color: #FFFFFF;
  border-top-right-radius: 4px;
  box-shadow: var(--glow-primary);
  white-space: pre-wrap;
}

.message-assistant .message-bubble {
  background: #FFFFFF;
  color: var(--text);
  border: 1px solid var(--border);
  border-top-left-radius: 4px;
  box-shadow: var(--shadow-sm);
}

/* Waiting dots */
.waiting-dots {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 2px;
}

.waiting-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  animation: dotBounce 1.2s infinite ease-in-out;
}

.waiting-dots span:nth-child(2) { animation-delay: 0.15s; }
.waiting-dots span:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-5px); opacity: 1; }
}

/* Thinking process */
.thinking-block {
  width: 100%;
  max-width: 85%;
  margin-bottom: 8px;
  border: 1px solid var(--purple-border);
  border-left: 3px solid var(--purple);
  border-radius: var(--radius-sm);
  background: var(--purple-bg);
  overflow: hidden;
  transition: var(--transition);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: var(--purple);
  user-select: none;
}

.thinking-header:hover {
  background: rgba(139, 92, 246, 0.08);
}

.thinking-spark {
  flex-shrink: 0;
  opacity: 0.7;
}

.thinking-spark.active {
  animation: sparkPulse 1s infinite;
}

@keyframes sparkPulse {
  0%, 100% { opacity: 0.4; transform: scale(0.85) rotate(0deg); }
  50% { opacity: 1; transform: scale(1.15) rotate(18deg); }
}

.thinking-chevron {
  margin-left: auto;
  transition: transform 0.25s ease;
}

.thinking-block.collapsed .thinking-chevron {
  transform: rotate(-90deg);
}

.thinking-content {
  padding: 0 12px 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow-y: auto;
}

/* Streaming cursor */
.streaming-cursor {
  display: inline-block;
  width: 6px;
  height: 14px;
  background: var(--primary);
  margin-left: 2px;
  animation: blink 1s infinite;
  vertical-align: text-bottom;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* Markdown body (assistant bubble) */
.md-body :deep(> :first-child) { margin-top: 0; }
.md-body :deep(> :last-child) { margin-bottom: 0; }

.md-body :deep(p) {
  margin: 6px 0;
}

.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3),
.md-body :deep(h4), .md-body :deep(h5), .md-body :deep(h6) {
  margin: 12px 0 6px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
}

.md-body :deep(h1) { font-size: 17px; }
.md-body :deep(h2) { font-size: 15.5px; }
.md-body :deep(h3) { font-size: 14.5px; }
.md-body :deep(h4), .md-body :deep(h5), .md-body :deep(h6) { font-size: 14px; }

.md-body :deep(ul), .md-body :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}

.md-body :deep(li) {
  margin: 3px 0;
}

.md-body :deep(li > ul), .md-body :deep(li > ol) {
  margin: 2px 0;
}

.md-body :deep(pre) {
  background: #1E293B;
  color: #E2E8F0;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12.5px;
  overflow-x: auto;
  margin: 8px 0;
  line-height: 1.5;
}

.md-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: inherit;
}

.md-body :deep(code) {
  background: var(--indigo-bg);
  color: var(--primary);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12.5px;
}

.md-body :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid var(--primary-border);
  color: var(--text-secondary);
  background: var(--surface-alt);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
  width: 100%;
}

.md-body :deep(th), .md-body :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}

.md-body :deep(th) {
  background: var(--surface-alt);
  font-weight: 700;
}

.md-body :deep(a) {
  color: var(--primary);
  text-decoration: none;
  border-bottom: 1px solid var(--primary-border);
}

.md-body :deep(a:hover) {
  border-bottom-color: var(--primary);
}

.md-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 10px 0;
}

.md-body :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-sm);
}

.md-body :deep(strong) {
  font-weight: 700;
  color: var(--text);
}

/* Skill calls */
.message-skills {
  margin-top: 8px;
  max-width: 85%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-alt);
  overflow: hidden;
}

.skill-call-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 600;
  transition: var(--transition);
}

.skill-call-header:hover {
  background: var(--indigo-bg);
  color: var(--primary);
}

.skill-call-detail {
  border-top: 1px solid var(--border);
  padding: 8px 10px;
  background: #1E293B;
}

.skill-call-detail pre {
  color: #E2E8F0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  line-height: 1.5;
  margin: 0;
  overflow-x: auto;
}

/* Input area */
.input-area {
  padding: 16px 24px 20px;
  background: #FFFFFF;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: #F8FAFF;
  border: 1.5px solid var(--border);
  border-radius: var(--radius);
  padding: 10px 12px;
  transition: var(--transition);
}

.input-wrapper:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
  background: #FFFFFF;
}

.chat-input {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  outline: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text);
  min-height: 24px;
  max-height: 160px;
}

.chat-input::placeholder {
  color: var(--text-placeholder);
}

.chat-input:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.send-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--border);
  color: var(--text-muted);
  cursor: not-allowed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: var(--transition);
}

.send-btn.active {
  background: var(--grad-primary-2);
  color: white;
  cursor: pointer;
  box-shadow: var(--glow-primary);
}

.send-btn.active:hover {
  transform: translateY(-1px);
  box-shadow: var(--glow-primary-md);
}

.input-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 6px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  font-weight: 600;
}

.streaming-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulseDot 1.2s infinite;
}

@keyframes pulseDot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}
</style>
