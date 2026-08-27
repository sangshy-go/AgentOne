<template>
  <div class="page-container">
    <h1 class="page-title">监控日志</h1>
    <p class="page-desc">当前工作空间的对话会话与 Skill 调用链</p>

    <n-tabs v-model:value="activeTab" type="line" @update:value="onTabChange">
      <!-- ========== 会话 ========== -->
      <n-tab-pane name="sessions" tab="会话列表">
        <!-- 下钻：会话时间线 -->
        <template v-if="viewMode === 'timeline' && selectedSessionId">
          <SessionTimeline
            :session-id="selectedSessionId"
            @back="viewMode = 'list'"
          />
        </template>

        <!-- 列表视图 -->
        <template v-else>
          <div class="filter-bar">
            <n-select
              v-model:value="sessionFilter.recentHours"
              :options="recentHoursOptions"
              style="width: 140px;"
              @update:value="resetAndLoadSessions"
            />
            <n-input
              v-model:value="sessionFilter.keyword"
              placeholder="搜索会话标题"
              clearable
              style="width: 220px;"
              @keyup.enter="resetAndLoadSessions"
              @clear="resetAndLoadSessions"
            />
            <n-button type="primary" ghost @click="resetAndLoadSessions">查询</n-button>
          </div>

          <!-- Agent 快速 chips -->
          <div v-if="agentOptions.length > 0" class="agent-chips">
            <button
              class="chip"
              :class="{ active: sessionFilter.agentId === null }"
              @click="pickAgent(null)"
            >全部</button>
            <button
              v-for="a in agentOptions"
              :key="a.value"
              class="chip"
              :class="{ active: sessionFilter.agentId === a.value }"
              @click="pickAgent(a.value)"
            >{{ a.label }}</button>
          </div>

          <n-data-table
            :columns="sessionColumns"
            :data="sessions"
            :bordered="false"
            :loading="sessionsLoading"
            :row-props="sessionRowProps"
          />
          <div v-if="sessionsTotal > sessionPageSize" class="pagination-wrap">
            <n-pagination
              :page="sessionPage"
              :page-size="sessionPageSize"
              :item-count="sessionsTotal"
              @update:page="handleSessionPageChange"
            />
          </div>
          <n-empty v-if="!sessionsLoading && sessions.length === 0" description="暂无对话会话" style="margin-top: 40px;" />
        </template>
      </n-tab-pane>

      <!-- ========== Skill 调用 ========== -->
      <n-tab-pane name="skill-calls" tab="Skill 调用">
        <div class="filter-bar">
          <n-select
            v-model:value="skillFilter.recentHours"
            :options="recentHoursOptions"
            style="width: 140px;"
            @update:value="resetAndLoadSkillCalls"
          />
          <n-select
            v-model:value="skillFilter.agentId"
            :options="agentOptions"
            filterable
            placeholder="选择 Agent（必填）"
            style="width: 200px;"
            @update:value="onSkillAgentChange"
          />
          <n-select
            v-model:value="skillFilter.skillId"
            :options="boundSkillOptions"
            filterable
            :placeholder="skillFilter.agentId ? '选择 Skill' : '请先选 Agent'"
            :disabled="!skillFilter.agentId"
            style="width: 200px;"
            @update:value="resetAndLoadSkillCalls"
          />
          <n-select
            v-model:value="skillFilter.status"
            :options="statusOptions"
            clearable
            placeholder="全部状态"
            style="width: 130px;"
            @update:value="resetAndLoadSkillCalls"
          />
          <n-button type="primary" ghost @click="resetAndLoadSkillCalls">查询</n-button>
        </div>

        <n-data-table
          :columns="skillColumns"
          :data="skillCalls"
          :bordered="false"
          :loading="skillLoading"
          :row-props="skillRowProps"
        />
        <div v-if="skillTotal > skillPageSize" class="pagination-wrap">
          <n-pagination
            :page="skillPage"
            :page-size="skillPageSize"
            :item-count="skillTotal"
            @update:page="handleSkillPageChange"
          />
        </div>
        <n-empty v-if="!skillLoading && skillCalls.length === 0" description="暂无 Skill 调用记录" style="margin-top: 40px;" />
      </n-tab-pane>

      <!-- ========== 审计日志（课题⑩：仅 owner/admin/auditor 可见，后端 2003 兜底） ========== -->
      <n-tab-pane v-if="authStore.canViewAudit" name="audit" tab="审计日志">
        <div class="filter-bar">
          <n-input
            v-model:value="auditFilter.action"
            placeholder="动作（如 create）"
            clearable
            style="width: 160px;"
            @keyup.enter="resetAndLoadAuditLogs"
            @clear="resetAndLoadAuditLogs"
          />
          <n-input
            v-model:value="auditFilter.resourceType"
            placeholder="资源类型（如 agents）"
            clearable
            style="width: 190px;"
            @keyup.enter="resetAndLoadAuditLogs"
            @clear="resetAndLoadAuditLogs"
          />
          <n-input
            v-model:value="auditFilter.keyword"
            placeholder="搜索操作人 / 资源 ID"
            clearable
            style="width: 220px;"
            @keyup.enter="resetAndLoadAuditLogs"
            @clear="resetAndLoadAuditLogs"
          />
          <n-button type="primary" ghost @click="resetAndLoadAuditLogs">查询</n-button>
        </div>

        <n-data-table
          :columns="auditColumns"
          :data="auditLogs"
          :bordered="false"
          :loading="auditLoading"
        />
        <div v-if="auditTotal > auditPageSize" class="pagination-wrap">
          <n-pagination
            :page="auditPage"
            :page-size="auditPageSize"
            :item-count="auditTotal"
            @update:page="handleAuditPageChange"
          />
        </div>
        <n-empty v-if="!auditLoading && auditLogs.length === 0" description="暂无审计日志" style="margin-top: 40px;" />
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import {
  NTabs, NTabPane, NDataTable, NSelect, NInput, NButton,
  NTag, NEmpty, NPagination, NEllipsis, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { MonitorSession, SkillCallRecord, AuditLog } from '@/types'
import { getMonitorSessions, getSkillCalls } from '@/services/monitor'
import { fetchAuditLogs } from '@/services/approval'
import { listAgents } from '@/services/agent'
import { listAgentSkillBindings } from '@/services/skill'
import { useAuthStore } from '@/stores/auth'
import SessionTimeline from './SessionTimeline.vue'

const message = useMessage()
const authStore = useAuthStore()

const activeTab = ref<'sessions' | 'skill-calls' | 'audit'>('sessions')
const viewMode = ref<'list' | 'timeline'>('list')
const selectedSessionId = ref('')

// ---------- 通用选项 ----------
const recentHoursOptions = [
  { label: '最近 1 小时', value: 1 },
  { label: '最近 24 小时', value: 24 },
  { label: '最近 7 天', value: 168 },
  { label: '最近 30 天', value: 720 },
  { label: '全部', value: 0 },
]
const statusOptions = [
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failure' },
]

const agentOptions = ref<{ label: string; value: string }[]>([])

// ---------- 会话列表 ----------
const sessions = ref<MonitorSession[]>([])
const sessionsLoading = ref(false)
const sessionPage = ref(1)
const sessionPageSize = ref(20)
const sessionsTotal = ref(0)
const sessionFilter = reactive({
  agentId: null as string | null,
  keyword: '',
  recentHours: 24,
})

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const res = await getMonitorSessions({
      agentId: sessionFilter.agentId || undefined,
      keyword: sessionFilter.keyword || undefined,
      recentHours: sessionFilter.recentHours || undefined,
      current: sessionPage.value,
      size: sessionPageSize.value,
    })
    const data = res.data.data
    sessions.value = data?.records || []
    sessionsTotal.value = data?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载会话列表失败')
  } finally {
    sessionsLoading.value = false
  }
}

function resetAndLoadSessions() {
  sessionPage.value = 1
  loadSessions()
}

function handleSessionPageChange(p: number) {
  sessionPage.value = p
  loadSessions()
}

function pickAgent(agentId: string | null) {
  sessionFilter.agentId = agentId
  resetAndLoadSessions()
}

function openTimeline(session: MonitorSession) {
  selectedSessionId.value = session.id
  viewMode.value = 'timeline'
}

function sessionRowProps(row: MonitorSession) {
  return { style: 'cursor: pointer;', onClick: () => openTimeline(row) }
}

// ---------- Skill 调用（Agent → Skill 联动） ----------
const skillCalls = ref<SkillCallRecord[]>([])
const skillLoading = ref(false)
const skillPage = ref(1)
const skillPageSize = ref(20)
const skillTotal = ref(0)
const skillFilter = reactive({
  agentId: null as string | null,
  skillId: null as string | null,
  status: null as string | null,
  recentHours: 24,
})

// 当前 Agent 绑定的 Skill（联动下拉）
const boundSkillOptions = ref<{ label: string; value: string }[]>([])

async function onSkillAgentChange(agentId: string | null) {
  // Agent 切换：清空 Skill 选择，重新加载联动选项
  skillFilter.skillId = null
  boundSkillOptions.value = []
  if (!agentId) return
  try {
    const res = await listAgentSkillBindings(agentId)
    const bindings = res.data.data || []
    boundSkillOptions.value = bindings
      .filter((b) => b.enabled)
      .map((b) => ({ label: b.skillName, value: b.skillId }))
  } catch (e: any) {
    message.warning(e?.message || '加载绑定 Skill 失败')
  }
  resetAndLoadSkillCalls()
}

async function loadSkillCalls() {
  skillLoading.value = true
  try {
    const res = await getSkillCalls({
      agentId: skillFilter.agentId || undefined,
      skillId: skillFilter.skillId || undefined,
      status: skillFilter.status || undefined,
      recentHours: skillFilter.recentHours || undefined,
      current: skillPage.value,
      size: skillPageSize.value,
    })
    const data = res.data.data
    skillCalls.value = data?.records || []
    skillTotal.value = data?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载 Skill 调用记录失败')
  } finally {
    skillLoading.value = false
  }
}

function resetAndLoadSkillCalls() {
  skillPage.value = 1
  loadSkillCalls()
}

function handleSkillPageChange(p: number) {
  skillPage.value = p
  loadSkillCalls()
}

function skillRowProps(row: SkillCallRecord) {
  return { style: 'cursor: pointer;', onClick: () => openSkillCallSession(row) }
}

function openSkillCallSession(row: SkillCallRecord) {
  // Skill 调用行点击：跳到会话列表，并切到该会话的时间线
  activeTab.value = 'sessions'
  selectedSessionId.value = row.sessionId
  viewMode.value = 'timeline'
}

// ---------- 审计日志（课题⑩，首次切到该 Tab 才加载） ----------
const auditLogs = ref<AuditLog[]>([])
const auditLoading = ref(false)
const auditLoaded = ref(false)
const auditPage = ref(1)
const auditPageSize = ref(20)
const auditTotal = ref(0)
const auditFilter = reactive({
  action: '',
  resourceType: '',
  keyword: '',
})

function onTabChange(tab: string) {
  if (tab === 'audit' && !auditLoaded.value) {
    loadAuditLogs()
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const res = await fetchAuditLogs({
      action: auditFilter.action || undefined,
      resourceType: auditFilter.resourceType || undefined,
      keyword: auditFilter.keyword || undefined,
      page: auditPage.value,
      size: auditPageSize.value,
    })
    const data = res.data.data
    auditLogs.value = data?.records || []
    auditTotal.value = data?.total || 0
    auditLoaded.value = true
  } catch (e: any) {
    message.error(e?.message || '加载审计日志失败')
  } finally {
    auditLoading.value = false
  }
}

function resetAndLoadAuditLogs() {
  auditPage.value = 1
  loadAuditLogs()
}

function handleAuditPageChange(p: number) {
  auditPage.value = p
  loadAuditLogs()
}

// ---------- 表格列 ----------
function formatTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}

const sessionColumns: DataTableColumns<MonitorSession> = [
  {
    title: '会话标题',
    key: 'title',
    ellipsis: { tooltip: true },
    render(row) {
      return row.title || '未命名会话'
    },
  },
  { title: 'Agent', key: 'agentName', width: 160, render(row) { return row.agentName || '—' } },
  { title: '用户', key: 'userEmail', width: 200, ellipsis: { tooltip: true } },
  { title: '消息数', key: 'messageCount', width: 90 },
  { title: '开始时间', key: 'createdAt', width: 160, render(row) { return formatTime(row.createdAt) } },
]

const skillColumns: DataTableColumns<SkillCallRecord> = [
  { title: 'Skill', key: 'skillName', width: 180, render(row) { return row.skillName || row.skillId } },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render(row) {
      return h(
        NTag,
        { type: row.status === 'success' ? 'success' : 'error', size: 'small', round: true },
        () => (row.status === 'success' ? '成功' : '失败')
      )
    },
  },
  { title: '耗时', key: 'durationMs', width: 90, render(row) { return `${row.durationMs ?? 0}ms` } },
  {
    title: '错误信息',
    key: 'errorMessage',
    ellipsis: { tooltip: true },
    render(row) {
      return row.errorMessage
        ? h(NEllipsis, { tooltip: true }, () => row.errorMessage)
        : h('span', { style: 'color: var(--text-muted)' }, '—')
    },
  },
  { title: '时间', key: 'createdAt', width: 160, render(row) { return formatTime(row.createdAt) } },
]

const auditColumns: DataTableColumns<AuditLog> = [
  { title: '时间', key: 'createdAt', width: 160, render(row) { return formatTime(row.createdAt) } },
  {
    title: '操作人',
    key: 'operatorEmail',
    width: 210,
    ellipsis: { tooltip: true },
    render(row) { return row.operatorEmail || row.operatorId },
  },
  { title: '动作', key: 'action', width: 110 },
  {
    title: '资源',
    key: 'resourceType',
    width: 220,
    ellipsis: { tooltip: true },
    render(row) { return row.resourceId ? `${row.resourceType} / ${row.resourceId}` : row.resourceType },
  },
  {
    title: '详情',
    key: 'path',
    ellipsis: { tooltip: true },
    render(row) {
      if (!row.path) return h('span', { style: 'color: var(--text-muted)' }, '—')
      return `${row.method || ''} ${row.path}`
    },
  },
]

onMounted(async () => {
  // 拉 Agent 列表（给会话 chips 和 Skill 调用下拉用）
  try {
    const res = await listAgents(1, 100)
    agentOptions.value = (res.data.data?.records || []).map((a) => ({ label: a.name, value: a.id }))
  } catch {
    /* 选项加载失败不阻塞主表 */
  }
  loadSessions()
  // Skill 调用页初始不加载（必须先选 Agent），避免全表扫描
})
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.agent-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.chip {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--n-border-color, #e5e7eb);
  background: transparent;
  color: var(--n-text-color, #374151);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.chip:hover {
  border-color: var(--n-color-target, #3b82f6);
  color: var(--n-color-target, #3b82f6);
}
.chip.active {
  background: var(--n-color-target, #3b82f6);
  color: #fff;
  border-color: var(--n-color-target, #3b82f6);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
