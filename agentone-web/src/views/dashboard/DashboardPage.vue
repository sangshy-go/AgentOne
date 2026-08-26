<template>
  <div class="dashboard-page">
    <!-- 页头：纯排版，靠字号与留白建立层级 -->
    <div class="page-head">
      <div>
        <h2>{{ greeting }}, {{ authStore.nickname || '用户' }}</h2>
        <p>{{ workspaceName }} · 今日工作空间概览</p>
      </div>
      <div class="page-head-date">{{ todayLabel }}</div>
    </div>

    <!-- KPI 卡片 -->
    <div class="stat-grid">
      <div class="stat-card" @click="router.push('/agents')">
        <div class="stat-top">
          <div class="stat-icon tint-purple">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <circle cx="12" cy="9" r="3" />
              <circle cx="8" cy="16" r="2" />
              <circle cx="16" cy="16" r="2" />
              <path d="M12 12v2M9.2 14.5l3 3M14.8 14.5l-3 3" />
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ stats.agentCount }}</div>
        <div class="stat-label">Agent 数</div>
      </div>

      <div class="stat-card" @click="router.push('/knowledge')">
        <div class="stat-top">
          <div class="stat-icon tint-blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ stats.knowledgeCount }}</div>
        <div class="stat-label">知识库</div>
      </div>

      <div class="stat-card" @click="router.push('/monitor')">
        <div class="stat-top">
          <div class="stat-icon tint-green">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7A8.38 8.38 0 014 11.5a8.5 8.5 0 0117 0z" />
              <line x1="8" y1="11" x2="16" y2="11" />
              <line x1="8" y1="15" x2="12" y2="15" />
            </svg>
          </div>
          <div v-if="yesterdayChange" class="stat-change" :class="yesterdayChange.isUp ? 'up' : 'down'">
            {{ yesterdayChange.isUp ? '↑' : '↓' }} {{ yesterdayChange.pct }}%
          </div>
        </div>
        <div class="stat-value">{{ stats.todayChatCount }}</div>
        <div class="stat-label">今日对话</div>
      </div>

      <div class="stat-card" @click="router.push('/monitor')">
        <div class="stat-top">
          <div class="stat-icon tint-orange">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 00-3-3.87" />
              <path d="M16 3.13a4 4 0 010 7.75" />
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ stats.activeUsers7d }}</div>
        <div class="stat-label">活跃用户（7 日）</div>
      </div>
    </div>

    <!-- 中部：趋势图（2fr）+ 快速操作（1fr） -->
    <div class="middle-row">
      <div class="page-card">
        <div class="card-header">
          <h3>近 7 日对话趋势</h3>
          <span class="trend-total">共 {{ trendTotal }} 次会话</span>
        </div>
        <div v-if="trendTotal === 0" class="empty-hint">
          近 7 日暂无对话数据，去「测试对话」试试吧
        </div>
        <div v-else class="trend-chart">
          <div
            v-for="day in dailyChats"
            :key="day.statDate"
            class="trend-col"
            :class="{ 'is-max': day.statCount === trendMax && trendMax > 0 }"
          >
            <div class="trend-count">{{ day.statCount }}</div>
            <div class="trend-bar-wrap">
              <div class="trend-bar" :style="{ height: barHeight(day.statCount) }"></div>
            </div>
            <div class="trend-date">{{ formatDay(day.statDate) }}</div>
          </div>
        </div>
      </div>

      <div class="page-card quick-actions-card">
        <div class="card-header">
          <h3>快速操作</h3>
        </div>
        <div class="quick-actions">
          <button class="qa-btn" @click="router.push('/agents')">
            <span class="qa-icon tint-purple">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="9" r="3" />
                <circle cx="8" cy="16" r="2" />
                <circle cx="16" cy="16" r="2" />
                <path d="M12 12v2" />
              </svg>
            </span>
            <span class="qa-label">新建 Agent</span>
            <svg class="qa-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
          <button class="qa-btn" @click="router.push('/knowledge')">
            <span class="qa-icon tint-blue">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
            </span>
            <span class="qa-label">上传知识库</span>
            <svg class="qa-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
          <button class="qa-btn" @click="router.push('/models')">
            <span class="qa-icon tint-green">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
              </svg>
            </span>
            <span class="qa-label">配置模型</span>
            <svg class="qa-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Recent Agents -->
    <div class="page-card">
      <div class="card-header">
        <h3>最近 Agent</h3>
        <span v-if="recentAgents.length > 0" class="more" @click="router.push('/agents')">查看全部 →</span>
      </div>
      <div v-if="recentAgents.length === 0" class="empty-hint">
        还没有 Agent，点击「新建 Agent」开始创建
      </div>
      <div v-else class="recent-agents">
        <div
          v-for="(agent, idx) in recentAgents"
          :key="agent.id"
          class="recent-agent-item"
          @click="router.push(`/agents/${agent.id}`)"
        >
          <span class="ra-rank">{{ idx + 1 }}</span>
          <div class="ra-avatar">{{ agent.name?.charAt(0) || 'A' }}</div>
          <div class="ra-info">
            <div class="ra-name">{{ agent.name }}</div>
            <div class="ra-desc">{{ agent.category || '暂无分类' }} · 更新于 {{ formatTime(agent.updatedAt) }}</div>
          </div>
          <svg class="ra-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { DailyChatStat } from '@/types'
import { getDashboardStats } from '@/services/monitor'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const workspaceName = computed(() => {
  const ws = authStore.workspaces.find((w) => w.id === authStore.workspaceId)
  return ws?.name || '当前工作空间'
})

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 · 周${week}`
})

const stats = reactive({
  agentCount: 0,
  knowledgeCount: 0,
  todayChatCount: 0,
  activeUsers7d: 0,
})

const dailyChats = ref<DailyChatStat[]>([])
const recentAgents = ref<{ id: string; name: string; category: string; updatedAt: string }[]>([])

const trendTotal = computed(() => dailyChats.value.reduce((sum, d) => sum + d.statCount, 0))
const trendMax = computed(() => Math.max(1, ...dailyChats.value.map((d) => d.statCount)))

// 从 dailyChats 自动派生"今日 vs 昨日"变化率
const yesterdayChange = computed(() => {
  const chats = dailyChats.value
  if (chats.length < 2) return null
  const today = chats[chats.length - 1].statCount
  const yesterday = chats[chats.length - 2].statCount
  if (yesterday === 0 || today === 0) return null
  const pct = ((today - yesterday) / yesterday) * 100
  return { pct: Math.abs(pct).toFixed(1), isUp: pct >= 0 }
})

function barHeight(count: number) {
  return `${Math.max(4, Math.round((count / trendMax.value) * 100))}%`
}

function formatDay(date: string) {
  return date.slice(5) // YYYY-MM-DD → MM-DD
}

function formatTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  try {
    const res = await getDashboardStats()
    const data = res.data.data
    stats.agentCount = data.agentCount
    stats.knowledgeCount = data.knowledgeCount
    stats.todayChatCount = data.todayChatCount
    stats.activeUsers7d = data.activeUsers7d
    dailyChats.value = data.dailyChats
    recentAgents.value = data.recentAgents
  } catch (e) {
    message.error('加载统计数据失败')
  }
})
</script>

<style scoped>
/* ============================================================
   Executive Dashboard 风格：中性灰白底 + 靛蓝单一主色，
   色彩只出现在图标浅色块与涨跌徽章；层级靠字号与留白。
   ============================================================ */
.dashboard-page {
  animation: pageIn 0.4s ease;
  padding: 28px 32px;
}

/* ========== 页头 ========== */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-head h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.01em;
  margin-bottom: 4px;
}

.page-head p {
  font-size: 14px;
  color: var(--text-muted);
}

.page-head-date {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  padding-bottom: 2px;
}

/* ========== KPI 卡片 ========== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
  width: 100%;
}

/* 覆盖 global.css 的装饰（彩色 top-border / 辉光圆），回归中性卡片 */
.stat-card {
  border-top: 1px solid var(--border);
}

.stat-card::after {
  display: none;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  margin-bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 图标浅色块：单一饱和色 + 10% 底，全页唯一的彩色信号之一 */
.tint-purple { background: #F5F3FF; color: #7C3AED; }
.tint-blue { background: #EFF6FF; color: #2563EB; }
.tint-green { background: #ECFDF5; color: #059669; }
.tint-orange { background: #FFFBEB; color: #D97706; }

/* 数字：实色近黑，tabular-nums 对齐，不用渐变字 */
.stat-value {
  background: none;
  -webkit-text-fill-color: currentColor;
  color: var(--text);
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
}

.stat-change {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
}

.stat-change.up { color: #059669; background: #ECFDF5; }
.stat-change.down { color: #DC2626; background: #FEF2F2; }

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
}

.card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.trend-total {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
}

/* ========== 趋势图 ========== */
.trend-chart {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  height: 200px;
  background-image: linear-gradient(#F1F5F9 1px, transparent 1px);
  background-size: 100% 25%;
  background-position: 0 12.5%;
}

.trend-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.trend-count {
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.trend-bar-wrap {
  width: 100%;
  max-width: 44px;
  display: flex;
  align-items: flex-end;
  flex: 1;
}

/* 单色体系：普通柱浅靛蓝，峰值柱实色主色 */
.trend-bar {
  width: 100%;
  border-radius: 6px 6px 2px 2px;
  background: #C7D2FE;
  transition: height 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.trend-col.is-max .trend-bar {
  background: #6366F1;
}

.trend-col.is-max .trend-count {
  color: #4F46E5;
  font-weight: 600;
}

.trend-date {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
}

/* ========== 中部行 ========== */
.middle-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

/* ========== 快速操作 ========== */
.quick-actions-card {
  display: flex;
  flex-direction: column;
}

.quick-actions {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.qa-btn {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 200ms ease;
  font-family: inherit;
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
}

.qa-btn:hover {
  background: #F8FAFC;
}

.qa-btn:hover .qa-arrow {
  opacity: 1;
  transform: translateX(2px);
  color: var(--primary);
}

.qa-icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.qa-label {
  flex: 1;
  text-align: left;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.qa-arrow {
  flex-shrink: 0;
  color: var(--text-muted);
  opacity: 0.45;
  transition: all 200ms ease;
}

/* ========== 更多链接 ========== */
.more {
  font-size: 13px;
  color: var(--primary);
  cursor: pointer;
  font-weight: 500;
  transition: color 200ms ease;
}

.more:hover {
  color: var(--primary-hover);
  text-decoration: underline;
}

/* ========== 空状态 ========== */
.empty-hint {
  text-align: center;
  padding: 48px 24px;
  color: var(--text-muted);
  font-size: 14px;
}

/* ========== 最近 Agent ========== */
.recent-agents {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-agent-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 200ms ease;
}

.recent-agent-item:hover {
  background: #F8FAFC;
}

.recent-agent-item:hover .ra-arrow {
  opacity: 1;
  transform: translateX(2px);
}

.ra-rank {
  width: 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
  text-align: center;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.ra-avatar {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: #EEF2FF;
  color: #4F46E5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.ra-info {
  flex: 1;
  min-width: 0;
}

.ra-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 2px;
}

.ra-desc {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ra-arrow {
  opacity: 0.35;
  transition: all 200ms ease;
  flex-shrink: 0;
  color: var(--text-muted);
}

/* ========== 响应式 ========== */
@media (max-width: 1100px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .middle-row { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .dashboard-page { padding: 16px; }
  .stat-grid { grid-template-columns: 1fr; }
  .page-head { flex-direction: column; align-items: flex-start; gap: 4px; }
}
</style>
