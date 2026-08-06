<template>
  <div class="dashboard-page">
    <h1 class="page-title">控制台</h1>
    <p class="page-desc">工作空间运行概览 · 今日数据</p>

    <!-- Stat Cards -->
    <div class="stat-grid">
      <div class="stat-card" @click="router.push('/agents')">
        <div class="stat-icon" style="background: var(--purple-bg); color: var(--purple);">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="9" r="3" />
            <circle cx="8" cy="16" r="2" />
            <circle cx="16" cy="16" r="2" />
            <path d="M12 12v2M9.2 14.5l3 3M14.8 14.5l-3 3" />
          </svg>
        </div>
        <div class="stat-value">{{ stats.agentCount }}</div>
        <div class="stat-label">活跃 Agent</div>
      </div>

      <div class="stat-card" @click="router.push('/knowledge')">
        <div class="stat-icon" style="background: var(--blue-bg); color: var(--blue);">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
          </svg>
        </div>
        <div class="stat-value">{{ stats.knowledgeCount }}</div>
        <div class="stat-label">知识库</div>
      </div>

      <div class="stat-card">
        <div class="stat-icon" style="background: var(--green-bg); color: var(--green);">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7A8.38 8.38 0 014 11.5a8.5 8.5 0 0117 0z" />
            <line x1="8" y1="11" x2="16" y2="11" />
            <line x1="8" y1="15" x2="12" y2="15" />
          </svg>
        </div>
        <div class="stat-value">{{ stats.todayChats }}</div>
        <div class="stat-label">今日对话</div>
      </div>

      <div class="stat-card" @click="router.push('/api-keys')">
        <div class="stat-icon" style="background: var(--orange-bg); color: var(--orange);">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71" />
            <path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71" />
          </svg>
        </div>
        <div class="stat-value">{{ stats.apiCalls }}</div>
        <div class="stat-label">API 调用</div>
      </div>
    </div>

    <!-- Quick Start -->
    <div class="page-card" style="margin-bottom: 24px;">
      <div class="card-header">
        <h3>快速开始</h3>
      </div>
      <n-steps :current="currentStep" size="small">
        <n-step title="配置模型" description="添加 LLM 供应商的 API Key" />
        <n-step title="创建知识库" description="上传文档构建知识体系" />
        <n-step title="创建 Agent" description="配置 AI 助手的人格与能力" />
        <n-step title="开始对话" description="测试 Agent 对话效果" />
      </n-steps>
    </div>

    <!-- Recent Agents -->
    <div class="page-card">
      <div class="card-header">
        <h3>最近 Agent</h3>
        <span v-if="recentAgents.length > 0" class="more" @click="router.push('/agents')">查看全部</span>
      </div>
      <div v-if="recentAgents.length === 0" class="empty-hint">
        还没有 Agent，点击上方步骤创建第一个
      </div>
      <div v-else class="recent-agents">
        <div
          v-for="agent in recentAgents"
          :key="agent.id"
          class="recent-agent-item"
          @click="router.push(`/agents/${agent.id}`)"
        >
          <div class="ra-avatar">{{ agent.name?.charAt(0) || 'A' }}</div>
          <div class="ra-info">
            <div class="ra-name">{{ agent.name }}</div>
            <div class="ra-desc">{{ agent.description || '暂无描述' }}</div>
          </div>
          <div class="badge badge-success">{{ agent.status === 'published' ? '已发布' : '草稿' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NSteps, NStep } from 'naive-ui'
import type { Agent } from '@/types'

const router = useRouter()

const stats = reactive({
  agentCount: 0,
  knowledgeCount: 0,
  todayChats: 0,
  apiCalls: 0,
})

const recentAgents = ref<Agent[]>([])
const currentStep = ref(1)

onMounted(() => {
  // TODO: 从后端加载统计数据
})
</script>

<style scoped>
.dashboard-page {
  animation: pageIn 0.4s ease;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.card-header h3 {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.more {
  font-size: 13px;
  color: var(--primary);
  cursor: pointer;
  font-weight: 600;
}

.more:hover {
  text-decoration: underline;
}

.empty-hint {
  text-align: center;
  padding: 40px 24px;
  color: var(--text-muted);
  font-size: 13px;
}

.recent-agents {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recent-agent-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: var(--transition);
}

.recent-agent-item:hover {
  background: var(--primary-bg);
}

.ra-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
  box-shadow: var(--glow-primary);
}

.ra-info {
  flex: 1;
  min-width: 0;
}

.ra-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.ra-desc {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1440px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
