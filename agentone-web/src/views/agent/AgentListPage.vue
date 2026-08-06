<template>
  <div class="agent-page">
    <!-- Toolbar -->
    <div class="toolbar">
      <div>
        <h1 class="page-title">Agent 管理</h1>
        <p class="page-desc" style="margin-bottom: 0;">创建和管理你的 AI 助手</p>
      </div>
      <button class="btn-gradient" @click="showCreate = true">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        创建 Agent
      </button>
    </div>

    <!-- Agent Grid -->
    <div v-if="agents.length > 0" class="agent-grid">
      <div
        v-for="agent in agents"
        :key="agent.id"
        class="agent-card"
        @click="goDetail(agent.id)"
      >
        <!-- Hover Actions -->
        <div class="ac-actions">
          <button class="ac-action-btn" title="编辑" @click.stop="goDetail(agent.id)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
          </button>
          <button class="ac-action-btn danger" title="删除" @click.stop="handleDelete(agent)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6" />
              <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
            </svg>
          </button>
        </div>

        <div class="ac-header">
          <div class="ac-avatar" :style="{ background: getGradient(agent.name) }">
            {{ agent.name?.charAt(0) || 'A' }}
          </div>
          <div class="ac-info">
            <div class="ac-name">{{ agent.name }}</div>
            <div class="ac-model">v{{ agent.currentVersion || 1 }}</div>
          </div>
        </div>
        <div class="ac-desc">{{ agent.description || '暂无描述，点击查看详情' }}</div>
        <div class="ac-meta">
          <div :class="['status-pill', `status-${agent.status || 'draft'}`]">
            <span class="status-dot"></span>
            {{ statusLabel(agent.status) }}
          </div>
          <div class="badge badge-neutral">{{ categoryLabel(agent.category) }}</div>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="total > pageSize" class="pagination-wrap">
      <n-pagination
        :page="page"
        :page-size="pageSize"
        :item-count="total"
        show-size-picker
        :page-sizes="[10, 20, 50]"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <!-- Empty State -->
    <div v-else class="page-card" style="text-align: center; padding: 80px 24px;">
      <div style="font-size: 56px; opacity: 0.3; margin-bottom: 16px;">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary);">
          <circle cx="12" cy="9" r="3" /><circle cx="8" cy="16" r="2" /><circle cx="16" cy="16" r="2" />
          <path d="M12 12v2M9.2 14.5l3 3M14.8 14.5l-3 3" />
        </svg>
      </div>
      <h3 style="font-size: 17px; color: var(--text-secondary); margin-bottom: 8px; font-weight: 700;">还没有 Agent</h3>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 20px;">创建你的第一个 AI 助手，开始智能对话之旅</p>
      <button class="btn-gradient" @click="showCreate = true">创建第一个 Agent</button>
    </div>

    <!-- Create Modal -->
    <n-modal v-model:show="showCreate" preset="card" title="创建 Agent" style="width: 540px;">
      <n-form ref="createFormRef" :model="createForm" :rules="createRules" label-placement="left" label-width="80">
        <n-form-item label="名称" path="name">
          <n-input v-model:value="createForm.name" placeholder="给 Agent 起个名字" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="createForm.description" type="textarea" :rows="3" placeholder="描述这个 Agent 的用途" />
        </n-form-item>
        <n-form-item label="分类" path="category">
          <n-select v-model:value="createForm.category" :options="categoryOptions" placeholder="选择分类" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" :loading="creating" @click="handleCreate">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NModal, NForm, NFormItem, NInput, NSelect, NSpace, NButton, NPagination, useMessage, useDialog } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import type { Agent } from '@/types'
import { listAgents, createAgent, deleteAgent } from '@/services/agent'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const agents = ref<Agent[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showCreate = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInst | null>(null)

const createForm = reactive({
  name: '',
  description: '',
  category: 'assistant',
})

const categoryOptions = [
  { label: '通用助手', value: 'assistant' },
  { label: '客服', value: 'customer_service' },
  { label: '编程', value: 'coding' },
  { label: '写作', value: 'writing' },
  { label: '分析', value: 'analysis' },
  { label: '其他', value: 'other' },
]

const createRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const gradients = [
  'var(--grad-primary)',
  'var(--grad-blue)',
  'var(--grad-purple)',
  'var(--grad-green)',
  'var(--grad-orange)',
]

function getGradient(name: string) {
  const idx = name?.charCodeAt(0) || 0
  return gradients[idx % gradients.length]
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    published: '已发布',
    testing: '测试中',
    draft: '草稿',
    stopped: '已停用',
    archived: '已归档',
  }
  return map[status] || status
}

function categoryLabel(cat: string) {
  const map: Record<string, string> = {
    assistant: '通用助手',
    customer_service: '客服',
    coding: '编程',
    writing: '写作',
    analysis: '分析',
    other: '其他',
  }
  return map[cat] || cat
}

function goDetail(id: string) {
  router.push(`/agents/${id}`)
}

function handleDelete(agent: Agent) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除 Agent「${agent.name}」吗？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteAgent(agent.id)
        message.success('已删除')
        await loadAgents()
      } catch (e: unknown) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

async function handleCreate() {
  try {
    await createFormRef.value?.validate()
  } catch { return }

  creating.value = true
  try {
    const res = await createAgent(createForm)
    message.success('创建成功')
    showCreate.value = false
    createForm.name = ''
    createForm.description = ''
    createForm.category = 'assistant'
    router.push(`/agents/${res.data.data.id}`)
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    creating.value = false
  }
}

async function loadAgents() {
  try {
    const res = await listAgents(page.value, pageSize.value)
    const data = res.data.data
    agents.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    agents.value = []
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadAgents()
}

function handlePageSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadAgents()
}

onMounted(loadAgents)
</script>

<style scoped>
.agent-page {
  animation: pageIn 0.4s ease;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 14px;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.agent-card {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: 22px;
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: var(--transition);
  position: relative;
  overflow: hidden;
}

.agent-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--grad-primary);
  opacity: 0;
  transition: var(--transition);
}

.agent-card:hover {
  box-shadow: var(--shadow-lg), var(--glow-primary);
  transform: translateY(-4px);
  border-color: var(--primary-border);
}

.agent-card:hover::before {
  opacity: 1;
}

.ac-actions {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  gap: 5px;
  opacity: 0;
  transition: var(--transition);
  z-index: 2;
}

.agent-card:hover .ac-actions {
  opacity: 1;
}

.ac-action-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: #FFFFFF;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-secondary);
  transition: var(--transition);
}

.ac-action-btn:hover {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
  box-shadow: var(--glow-primary);
}

.ac-action-btn.danger:hover {
  background: var(--red);
  border-color: var(--red);
  box-shadow: 0 0 16px rgba(239, 68, 68, 0.3);
}

.ac-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 14px;
}

.ac-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  color: white;
  font-weight: 800;
  box-shadow: var(--glow-primary);
}

.ac-info {
  flex: 1;
  min-width: 0;
}

.ac-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 3px;
}

.ac-model {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.ac-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
  line-height: 1.6;
}

.ac-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

@media (max-width: 1440px) {
  .agent-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .agent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
