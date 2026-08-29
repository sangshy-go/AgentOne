<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h1 class="page-title">IM 机器人</h1>
        <p class="page-desc" style="margin-bottom: 0;">把 Agent 接入钉钉，支持通知推送与 @机器人对话</p>
      </div>
      <button class="btn-gradient" @click="openCreate">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        创建机器人
      </button>
    </div>

    <div class="hint-bar">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink: 0; margin-top: 1px;">
        <circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" />
      </svg>
      <div>
        钉钉自定义机器人（webhook）仅支持单向发送通知；<strong>@机器人对话</strong>需钉钉企业应用凭证（callback 模式），创建弹窗内含开放平台配置引导。凭证加密存储，创建后不再展示。
      </div>
    </div>

    <!-- Bot list -->
    <div v-if="bots.length > 0" class="bot-list">
      <div v-for="bot in bots" :key="bot.id" class="bot-card" :class="{ 'bot-card-disabled': bot.status !== 'active' }">
        <div class="bot-card-header">
          <div class="bot-icon" :class="bot.platform === 'dingtalk' ? 'bot-icon-dingtalk' : 'bot-icon-wecom'">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 8V4H8" /><rect width="16" height="12" x="4" y="8" rx="2" />
              <path d="M2 14h2" /><path d="M20 14h2" /><path d="M15 13v2" /><path d="M9 13v2" />
            </svg>
          </div>
          <div class="bot-info">
            <div class="bot-name-row">
              <span class="bot-name">{{ bot.name }}</span>
              <span class="badge" :class="bot.platform === 'dingtalk' ? 'badge-info' : 'badge-success'">
                {{ PLATFORM_LABELS[bot.platform] || bot.platform }}
              </span>
              <span class="badge badge-neutral">{{ MODE_LABELS[bot.mode] || bot.mode }}</span>
              <span class="badge" :class="bot.status === 'active' ? 'badge-success' : 'badge-warning'">
                {{ bot.status === 'active' ? '启用' : '停用' }}
              </span>
            </div>
            <div class="bot-desc">
              <template v-if="bot.agentId">
                绑定 Agent：<strong>{{ agentNameMap[bot.agentId] || bot.agentId }}</strong>
              </template>
              <template v-else>纯通知机器人（未绑定 Agent）</template>
            </div>
            <div class="bot-meta mono">{{ maskedConfigText(bot) }}</div>
            <div v-if="bot.mode === 'callback'" class="bot-meta mono callback-row">
              回调地址：{{ callbackUrl(bot) }}
              <button class="copy-btn" @click="copyCallback(bot)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
                  <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
                </svg>
                复制
              </button>
            </div>
          </div>
          <div class="bot-actions">
            <button
              v-if="bot.platform === 'dingtalk' && bot.mode === 'webhook' && bot.status !== 'active'"
              class="action-btn action-btn-primary"
              @click="openSend(bot)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="m22 2-7 20-4-9-9-4Z" /><path d="M22 2 11 13" />
              </svg>
              发送测试
            </button>
            <button class="action-btn" @click="openEdit(bot)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
              编辑
            </button>
            <button class="action-btn" @click="handleToggleStatus(bot)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18.36 6.64a9 9 0 1 1-12.73 0" /><line x1="12" y1="2" x2="12" y2="12" />
              </svg>
              {{ bot.status === 'active' ? '停用' : '启用' }}
            </button>
            <button class="action-btn action-btn-danger" @click="deleteTarget = bot">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
              </svg>
              删除
            </button>
          </div>
        </div>
        <div class="bot-card-footer">创建于 {{ formatTime(bot.createdAt) }}</div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else-if="!loading" class="page-card" style="text-align: center; padding: 80px 24px;">
      <div style="margin-bottom: 16px;">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary); opacity: 0.4;">
          <path d="M12 8V4H8" /><rect width="16" height="12" x="4" y="8" rx="2" />
          <path d="M2 14h2" /><path d="M20 14h2" /><path d="M15 13v2" /><path d="M9 13v2" />
        </svg>
      </div>
      <h3 style="font-size: 17px; color: var(--text-secondary); margin-bottom: 8px; font-weight: 700;">暂无 IM 机器人</h3>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 20px;">把 Agent 接入钉钉，支持通知推送与 @机器人对话</p>
      <button class="btn-gradient" @click="openCreate">创建第一个机器人</button>
    </div>

    <!-- Create modal -->
    <Teleport to="body">
      <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">{{ editingBot ? '编辑 IM 机器人' : '创建 IM 机器人' }}</div>
            <button class="modal-close" @click="showCreate = false">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">名称 <span class="required">*</span></label>
              <input v-model="form.name" class="form-input" placeholder="例如：客服群通知机器人" maxlength="100" autocomplete="off" />
            </div>

            <div class="form-group">
              <label class="form-label">平台 <span class="required">*</span></label>
              <div class="platform-grid">
                <button type="button" class="platform-opt" :class="{ 'platform-opt-selected': form.platform === 'dingtalk' }" :disabled="!!editingBot" @click="selectPlatform('dingtalk')">
                  <span class="platform-opt-icon platform-opt-icon-dingtalk">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 8V4H8" /><rect width="16" height="12" x="4" y="8" rx="2" />
                      <path d="M2 14h2" /><path d="M20 14h2" /><path d="M15 13v2" /><path d="M9 13v2" />
                    </svg>
                  </span>
                  <span class="platform-opt-text">
                    <span class="platform-opt-name">钉钉</span>
                    <span class="platform-opt-hint">webhook 通知 / 企业应用回调</span>
                  </span>
                </button>
                <div class="platform-opt platform-opt-todo" :class="{ 'platform-opt-selected': !!editingBot && editingBot.platform === 'wecom' }" aria-disabled="true" title="能力待建设，暂缓接入">
                  <span class="platform-opt-icon platform-opt-icon-todo">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z" />
                    </svg>
                  </span>
                  <span class="platform-opt-text">
                    <span class="platform-opt-name">企业微信 <span class="badge badge-warning todo-badge">待建设</span></span>
                    <span class="platform-opt-hint">微信生态，官方回调接入暂缓</span>
                  </span>
                </div>
                <div class="platform-opt platform-opt-todo" aria-disabled="true" title="能力待建设，暂未排期">
                  <span class="platform-opt-icon platform-opt-icon-todo">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20.24 12.24a6 6 0 0 0-8.49-8.49L5 10.5V19h8.5z" />
                      <line x1="16" y1="8" x2="2" y2="22" /><line x1="17.5" y1="15" x2="9" y2="15" />
                    </svg>
                  </span>
                  <span class="platform-opt-text">
                    <span class="platform-opt-name">飞书 <span class="badge badge-warning todo-badge">待建设</span></span>
                    <span class="platform-opt-hint">官方机器人接入，暂未排期</span>
                  </span>
                </div>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">模式 <span class="required">*</span></label>
              <div class="mode-list">
                <button v-if="form.platform === 'dingtalk'" type="button" class="mode-opt" :class="{ 'mode-opt-selected': form.mode === 'webhook' }" :disabled="!!editingBot" @click="form.mode = 'webhook'">
                  <span class="mode-radio"></span>
                  <span class="mode-text">
                    <span class="mode-name">通知（webhook）</span>
                    <span class="mode-hint">自定义机器人，单向发送，适合群通知 / 告警推送</span>
                  </span>
                </button>
                <button type="button" class="mode-opt" :class="{ 'mode-opt-selected': form.mode === 'callback' }" :disabled="!!editingBot" @click="form.mode = 'callback'">
                  <span class="mode-radio"></span>
                  <span class="mode-text">
                    <span class="mode-name">对话（callback）</span>
                    <span class="mode-hint">{{ form.platform === 'dingtalk' ? '钉钉企业应用' : '企微自建应用' }}回调，@机器人触发 Agent 多轮对话</span>
                  </span>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">绑定 Agent</label>
              <select v-model="form.agentId" class="form-input">
                <option value="">可空：纯通知机器人</option>
                <option v-for="a in agents" :key="a.id" :value="a.id">{{ a.name }}</option>
              </select>
              <p class="form-hint">对话模式必须绑定已发布 Agent；通知模式可留空。</p>
            </div>

            <p v-if="editingBot" class="edit-hint">
              凭证加密不回显，下方凭证输入框默认为空（= 保持不变）；如需轮换凭证，需填全整套，保存后整体加密覆盖。
            </p>

            <!-- 钉钉 webhook -->
            <template v-if="form.platform === 'dingtalk' && form.mode === 'webhook'">
              <div class="form-group">
                <label class="form-label">Webhook URL <span class="required">*</span></label>
                <input v-model="form.config.webhookUrl" class="form-input mono" placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." autocomplete="off" />
                <p class="form-hint">钉钉群 → 智能群助手 → 添加自定义机器人，复制 Webhook 地址。</p>
              </div>
              <div class="form-group">
                <label class="form-label">加签 Secret</label>
                <input v-model="form.config.secret" type="password" class="form-input mono" placeholder="可选；安全设置为「加签」时必填（SEC 开头）" autocomplete="new-password" />
              </div>
            </template>

            <!-- 钉钉企业应用回调 -->
            <template v-else-if="form.platform === 'dingtalk' && form.mode === 'callback'">
              <div class="guide-box">
                <div class="guide-title">钉钉开放平台配置引导</div>
                <ol class="guide-steps">
                  <li>登录 open.dingtalk.com → 应用开发 → 企业内部开发 → 创建应用，并开启「机器人」能力；</li>
                  <li>在应用的「凭证与基础信息」复制 AppSecret 填入下方，创建本机器人并绑定已发布 Agent；</li>
                  <li>创建成功后，在机器人卡片上复制「回调地址」，填入钉钉开放平台 → 该应用 → 机器人 → 消息接收地址；</li>
                  <li>在卡片上点击「启用」，发布应用版本并将机器人加入群聊，@机器人即可对话。回调地址需公网可达（本地环境需内网穿透）。</li>
                </ol>
              </div>
              <div class="form-group">
                <label class="form-label">AppSecret <span class="required">*</span></label>
                <input v-model="form.config.appSecret" type="password" class="form-input mono" placeholder="企业应用 AppSecret（回调验签）" autocomplete="new-password" />
              </div>
            </template>

            <!-- 企微自建应用回调 -->
            <template v-else>
              <div class="form-group">
                <label class="form-label">CorpID <span class="required">*</span></label>
                <input v-model="form.config.corpId" class="form-input mono" placeholder="企业 ID" autocomplete="off" />
              </div>
              <div class="form-group">
                <label class="form-label">AgentID <span class="required">*</span></label>
                <input v-model="form.config.agentId" class="form-input mono" placeholder="自建应用 AgentId" autocomplete="off" />
              </div>
              <div class="form-group">
                <label class="form-label">Secret <span class="required">*</span></label>
                <input v-model="form.config.secret" type="password" class="form-input mono" placeholder="自建应用 Secret" autocomplete="new-password" />
              </div>
              <div class="form-group">
                <label class="form-label">Token <span class="required">*</span></label>
                <input v-model="form.config.token" class="form-input mono" placeholder="回调 Token" autocomplete="off" />
              </div>
              <div class="form-group">
                <label class="form-label">EncodingAESKey <span class="required">*</span></label>
                <input v-model="form.config.encodingAesKey" class="form-input mono" placeholder="回调 EncodingAESKey（43 位）" autocomplete="off" />
                <p class="form-hint">创建后在企微「接收消息」配置回调：URL 为 /api/im/callback/wecom/{机器人ID}（需公网可达）。</p>
              </div>
            </template>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="showCreate = false">取消</button>
            <button class="btn-gradient" :disabled="submitting" @click="handleSubmit">
              <span v-if="submitting" class="btn-spinner"></span>
              {{ submitting ? (editingBot ? '保存中...' : '创建中...') : (editingBot ? '保存' : '创建') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Send test modal -->
    <Teleport to="body">
      <div v-if="sendTarget" class="modal-backdrop" @click.self="sendTarget = null">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">发送测试消息 - {{ sendTarget.name }}</div>
            <button class="modal-close" @click="sendTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">消息类型</label>
              <div class="segment">
                <button type="button" class="segment-opt" :class="{ 'segment-opt-selected': sendForm.msgType === 'text' }" @click="sendForm.msgType = 'text'">文本</button>
                <button type="button" class="segment-opt" :class="{ 'segment-opt-selected': sendForm.msgType === 'markdown' }" @click="sendForm.msgType = 'markdown'">Markdown</button>
              </div>
            </div>
            <div v-if="sendForm.msgType === 'markdown'" class="form-group">
              <label class="form-label">标题</label>
              <input v-model="sendForm.title" class="form-input" placeholder="Markdown 消息标题" />
            </div>
            <div class="form-group">
              <label class="form-label">内容 <span class="required">*</span></label>
              <textarea v-model="sendForm.text" class="form-input form-textarea" rows="4" placeholder="消息内容"></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="sendTarget = null">取消</button>
            <button class="btn-gradient" :disabled="sending" @click="handleSend">
              <span v-if="sending" class="btn-spinner"></span>
              {{ sending ? '发送中...' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete confirm modal -->
    <Teleport to="body">
      <div v-if="deleteTarget" class="modal-backdrop" @click.self="deleteTarget = null">
        <div class="modal-card modal-card-sm">
          <div class="modal-header">
            <div class="modal-title confirm-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="color: var(--red);">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              删除 IM 机器人
            </div>
            <button class="modal-close" @click="deleteTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">确定删除机器人「<strong>{{ deleteTarget.name }}</strong>」吗？</p>
            <p class="confirm-hint">平台回调将立即失效，发送者的会话映射一并清理，不可恢复。</p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="deleteTarget = null">取消</button>
            <button class="btn-danger" :disabled="deleting" @click="confirmDelete">
              <span v-if="deleting" class="btn-spinner"></span>
              {{ deleting ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import type { ImBot, Agent } from '@/types'
import { listImBots, createImBot, updateImBot, deleteImBot, sendImBotTest } from '@/services/im'
import { listAgents } from '@/services/agent'

const message = useMessage()

const loading = ref(false)
const bots = ref<ImBot[]>([])
const agents = ref<Agent[]>([])

const PLATFORM_LABELS: Record<string, string> = { dingtalk: '钉钉', wecom: '企业微信' }
const MODE_LABELS: Record<string, string> = { webhook: '通知（仅发送）', callback: '对话（回调）' }

onMounted(async () => {
  await Promise.all([loadBots(), loadAgents()])
})

async function loadBots() {
  loading.value = true
  try {
    const { data } = await listImBots()
    bots.value = data.data || []
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAgents() {
  try {
    const { data } = await listAgents(1, 100)
    agents.value = data.data?.records || []
  } catch {
    agents.value = []
  }
}

const agentNameMap = computed(() => {
  const map: Record<string, string> = {}
  agents.value.forEach(a => { map[a.id] = a.name })
  return map
})

function maskedConfigText(bot: ImBot): string {
  return Object.entries(bot.configMasked || {})
    .map(([k, v]) => `${k}: ${v}`)
    .join(' · ')
}

function formatTime(t: string): string {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

function callbackUrl(bot: ImBot): string {
  return `${window.location.origin}/api/im/callback/${bot.platform}/${bot.id}`
}

async function copyCallback(bot: ImBot) {
  try {
    await navigator.clipboard.writeText(callbackUrl(bot))
    message.success('回调地址已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

// ==================== 创建 ====================

const showCreate = ref(false)
const submitting = ref(false)
const editingBot = ref<ImBot | null>(null)
const form = ref(buildEmptyForm())

function buildEmptyForm() {
  return {
    name: '',
    platform: 'dingtalk' as 'dingtalk' | 'wecom',
    mode: 'webhook' as 'webhook' | 'callback',
    agentId: '',
    config: {} as Record<string, string>,
  }
}

function selectPlatform(platform: 'dingtalk' | 'wecom') {
  if (form.value.platform === platform) return
  form.value.platform = platform
  form.value.mode = platform === 'wecom' ? 'callback' : 'webhook'
  form.value.config = {}
}

function openCreate() {
  form.value = buildEmptyForm()
  editingBot.value = null
  showCreate.value = true
}

function openEdit(bot: ImBot) {
  form.value = {
    name: bot.name,
    platform: bot.platform,
    mode: bot.mode,
    agentId: bot.agentId || '',
    config: {},
  }
  editingBot.value = bot
  showCreate.value = true
}

// 各 platform/mode 组合的凭证必填项（钉钉 webhook 的 secret 为可选，不在其中）
const REQUIRED_KEYS: Record<string, string[]> = {
  'dingtalk/webhook': ['webhookUrl'],
  'dingtalk/callback': ['appSecret'],
  'wecom/callback': ['corpId', 'agentId', 'secret', 'token', 'encodingAesKey'],
}

function validateForm(): string | null {
  const f = form.value
  if (!f.name.trim()) return '请填写名称'
  const keys = REQUIRED_KEYS[`${f.platform}/${f.mode}`] || []
  const missing = keys.filter(k => !f.config[k]?.trim())
  if (!editingBot.value) return missing.length ? `请填写 ${missing[0]}` : null
  const touched = Object.values(f.config).some(v => v?.trim())
  if (touched && missing.length) return '修改凭证需填全整套（留空则保持不变）'
  return null
}

async function handleSubmit() {
  const err = validateForm()
  if (err) {
    message.warning(err)
    return
  }
  submitting.value = true
  const configTouched = Object.values(form.value.config).some(v => v?.trim())
  try {
    if (editingBot.value) {
      await updateImBot(editingBot.value.id, {
        name: form.value.name.trim(),
        agentId: form.value.agentId,
        config: configTouched ? form.value.config : undefined,
      })
      message.success('已保存')
    } else {
      await createImBot({
        name: form.value.name.trim(),
        platform: form.value.platform,
        mode: form.value.mode,
        agentId: form.value.agentId || undefined,
        config: form.value.config,
      })
      message.success('机器人已创建')
    }
    showCreate.value = false
    await loadBots()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

// ==================== 启停 / 删除 ====================

async function handleToggleStatus(bot: ImBot) {
  try {
    await updateImBot(bot.id, { status: bot.status === 'active' ? 'disabled' : 'active' })
    message.success(bot.status === 'active' ? '已停用' : '已启用')
    await loadBots()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

const deleteTarget = ref<ImBot | null>(null)
const deleting = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteImBot(deleteTarget.value.id)
    message.success(`机器人「${deleteTarget.value.name}」已删除`)
    deleteTarget.value = null
    await loadBots()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

// ==================== 发送测试 ====================

const sendTarget = ref<ImBot | null>(null)
const sending = ref(false)
const sendForm = ref({ msgType: 'text', title: '', text: '' })

function openSend(bot: ImBot) {
  sendTarget.value = bot
  sendForm.value = { msgType: 'text', title: '', text: '' }
}

async function handleSend() {
  if (!sendTarget.value) return
  if (!sendForm.value.text.trim()) {
    message.warning('请填写消息内容')
    return
  }
  sending.value = true
  try {
    await sendImBotTest(sendTarget.value.id, {
      text: sendForm.value.text,
      msgType: sendForm.value.msgType,
      title: sendForm.value.title || undefined,
    })
    message.success('已发送，请到钉钉群里确认')
    sendTarget.value = null
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '发送失败')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 14px;
}

.hint-bar {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  padding: 10px 14px;
  background: var(--indigo-bg);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--primary);
  margin-bottom: 16px;
}

.bot-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bot-card {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  overflow: hidden;
  transition: var(--transition);
}

.bot-card:hover {
  box-shadow: var(--shadow-md);
}

.bot-card-disabled {
  opacity: 0.75;
}

.bot-card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 20px;
}

.bot-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bot-icon-dingtalk {
  background: var(--blue-bg);
  color: var(--blue);
}

.bot-icon-wecom {
  background: var(--green-bg);
  color: var(--green);
}

.bot-info {
  flex: 1;
  min-width: 0;
}

.bot-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.bot-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.bot-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 6px;
}

.bot-meta {
  font-size: 12px;
  color: var(--text-muted);
  word-break: break-all;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

.bot-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.bot-card-footer {
  padding: 10px 20px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-muted);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--border);
  background: #FFFFFF;
  color: var(--text-secondary);
  transition: var(--transition);
}

.action-btn:hover:not(:disabled) {
  border-color: var(--primary-border);
  color: var(--primary);
  background: var(--indigo-bg);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn-primary {
  border-color: var(--primary-border);
  color: var(--primary);
  background: var(--indigo-bg);
}

.action-btn-primary:hover:not(:disabled) {
  background: var(--primary);
  color: white;
}

.action-btn-danger:hover:not(:disabled) {
  border-color: var(--red);
  color: var(--red);
  background: var(--red-bg);
}

/* Platform / mode pickers */
.platform-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.platform-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  background: #FFFFFF;
  cursor: pointer;
  text-align: left;
  transition: var(--transition);
}

.platform-opt:hover:not(:disabled) {
  border-color: var(--primary-border);
}

.platform-opt:disabled,
.mode-opt:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.platform-opt-selected {
  border-color: var(--primary);
  background: var(--indigo-bg);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.platform-opt-icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.platform-opt-icon-dingtalk {
  background: var(--blue-bg);
  color: var(--blue);
}

/* 待建设平台（企业微信 / 飞书）：虚线灰底、不可选 */
.platform-opt-todo {
  border-style: dashed;
  background: var(--surface-alt);
  cursor: not-allowed;
  opacity: 0.8;
}

.platform-opt.platform-opt-todo:hover {
  border-color: var(--border);
}

.platform-opt-icon-todo {
  background: #FFFFFF;
  color: var(--text-muted);
  border: 1px solid var(--border);
}

.todo-badge {
  margin-left: 4px;
  padding: 1px 8px;
  font-size: 10px;
  vertical-align: 1px;
}

.guide-box {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--indigo-bg);
  border-left: 2px solid var(--primary);
  border-radius: var(--radius-sm);
}

.guide-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 8px;
}

.guide-steps {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.callback-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: #FFFFFF;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
  flex-shrink: 0;
}

.copy-btn:hover {
  border-color: var(--primary-border);
  color: var(--primary);
  background: var(--indigo-bg);
}

.platform-opt-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.platform-opt-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.platform-opt-hint {
  font-size: 11px;
  color: var(--text-muted);
}

.mode-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mode-opt {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  background: #FFFFFF;
  cursor: pointer;
  text-align: left;
  transition: var(--transition);
}

.mode-opt:hover:not(:disabled) {
  border-color: var(--primary-border);
}

.mode-opt-selected {
  border-color: var(--primary);
  background: var(--indigo-bg);
}

.mode-radio {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1.5px solid var(--border-strong);
  flex-shrink: 0;
  margin-top: 2px;
  box-sizing: border-box;
  transition: var(--transition);
}

.mode-opt-selected .mode-radio {
  border: 5px solid var(--primary);
}

.mode-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mode-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.mode-hint {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
}

/* Segment control */
.segment {
  display: inline-flex;
  padding: 3px;
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  gap: 2px;
}

.segment-opt {
  padding: 5px 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
}

.segment-opt-selected {
  background: #FFFFFF;
  color: var(--primary);
  box-shadow: var(--shadow-sm);
}

/* Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(4px);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-card {
  background: #FFFFFF;
  border-radius: var(--radius-lg);
  width: min(480px, 90vw);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-xl);
  animation: modalIn 0.25s ease;
}

.modal-card-lg {
  width: min(640px, 92vw);
}

.modal-card-sm {
  width: min(420px, 90vw);
}

@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95) translateY(16px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: var(--transition);
}

.modal-close:hover {
  background: var(--surface-alt);
  color: var(--text);
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
}

.modal-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
}

.required {
  color: var(--red);
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-family: inherit;
  color: var(--text);
  background: #FFFFFF;
  transition: var(--transition);
  outline: none;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.form-input::placeholder {
  color: var(--text-placeholder);
}

select.form-input {
  cursor: pointer;
}

.form-textarea {
  resize: vertical;
  min-height: 60px;
  line-height: 1.6;
}

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin: 4px 0 0;
  line-height: 1.5;
}

.edit-hint {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 16px;
  padding: 8px 12px;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--primary);
}

.btn-secondary-custom {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
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
  box-shadow: var(--shadow-sm);
}

.btn-gradient:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: none;
  background: var(--red);
  color: #ffffff;
  transition: var(--transition);
  min-width: 88px;
}

.btn-danger:hover:not(:disabled) {
  background: #dc2626;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.confirm-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
}

.confirm-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
  line-height: 1.6;
  margin: 0;
}

.confirm-text strong {
  font-weight: 700;
}

.confirm-hint {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
  margin: 10px 0 0;
  padding: 10px 12px;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--red);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
