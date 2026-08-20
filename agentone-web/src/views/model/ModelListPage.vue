<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h1 class="page-title">模型管理</h1>
        <p class="page-desc" style="margin-bottom: 0;">配置 LLM 供应商与模型，供 Agent 和知识库使用</p>
      </div>
      <button class="btn-gradient" @click="openCreateModal">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        添加模型供应商
      </button>
    </div>

    <!-- Provider list -->
    <div v-if="providers.length > 0" class="provider-list">
      <div v-for="p in providers" :key="p.id" class="provider-card">
        <div class="provider-card-header">
          <div class="provider-icon" :style="{ background: providerColor(p.provider) }">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
          </div>
          <div class="provider-info">
            <div class="provider-name">{{ p.name }}</div>
            <div class="provider-meta">
              <span class="badge badge-info">{{ providerLabel(p.provider) }}</span>
              <span class="provider-model-count">{{ (providerModels[p.id] || []).length }} 个模型</span>
            </div>
          </div>
          <div class="provider-actions">
            <button class="action-btn" @click="handleCheck(p)" :disabled="checkingId === p.id" title="测试连通性">
              <svg v-if="checkingId === p.id" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <path d="M21 12a9 9 0 11-6.219-8.56" />
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 11-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
              {{ checkingId === p.id ? '检测中...' : '测试' }}
            </button>
            <button class="action-btn" @click="openEditModal(p)" title="编辑">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
              编辑
            </button>
            <button class="action-btn action-btn-danger" @click="handleDelete(p)" title="删除">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
              </svg>
              删除
            </button>
          </div>
        </div>
        <div class="provider-card-body">
          <div class="provider-detail">
            <span class="provider-detail-label">Base URL</span>
            <span class="provider-detail-value mono">{{ p.baseUrl || '默认' }}</span>
          </div>
          <div class="provider-detail">
            <span class="provider-detail-label">API Key</span>
            <span class="provider-detail-value mono">{{ maskKey(p.apiKey) }}</span>
          </div>
          <div v-if="checkResults[p.id]" class="provider-check-result">
            <span :class="['badge', checkResults[p.id].available ? 'badge-success' : 'badge-danger']">
              {{ checkResults[p.id].available ? '可用' : '不可用' }}
            </span>
            <span class="check-detail">{{ checkResults[p.id].message }}</span>
            <span v-if="checkResults[p.id].latencyMs" class="check-latency">{{ checkResults[p.id].latencyMs }}ms</span>
          </div>
        </div>

        <!-- Models under this provider -->
        <div class="provider-models-section">
          <div class="provider-models-header">
            <span>模型列表</span>
            <button class="add-model-btn" @click="openAddModelModal(p)">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              添加模型
            </button>
          </div>
          <div v-if="(providerModels[p.id] || []).length > 0" class="model-tags">
            <div v-for="m in providerModels[p.id]" :key="m.id" class="model-tag" :class="'model-tag-' + m.modelType">
              <span class="model-tag-type">{{ modelTypeLabel(m.modelType) }}</span>
              <span class="model-tag-name">{{ m.displayName || m.modelId }}</span>
              <span v-if="m.modelType === 'embedding' && m.dimensions" class="model-tag-dims">{{ m.dimensions }}维</span>
              <button class="model-tag-delete" @click="handleDeleteModel(m)" title="删除模型">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
          </div>
          <div v-else class="model-tags-empty">
            暂无模型，请点击「添加模型」配置 Chat / Embedding 等模型
          </div>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="providerTotal > providerPageSize" class="pagination-wrap">
      <n-pagination
        :page="providerPage"
        :page-size="providerPageSize"
        :item-count="providerTotal"
        @update:page="handleProviderPageChange"
      />
    </div>

    <!-- Empty state -->
    <div v-else class="page-card" style="text-align: center; padding: 80px 24px;">
      <div style="margin-bottom: 16px;">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary); opacity: 0.4;">
          <path d="M12 2L2 7l10 5 10-5-10-5z" />
          <path d="M2 17l10 5 10-5" />
          <path d="M2 12l10 5 10-5" />
        </svg>
      </div>
      <h3 style="font-size: 17px; color: var(--text-secondary); margin-bottom: 8px; font-weight: 700;">暂无模型配置</h3>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 20px;">添加 LLM 供应商的 API Key，开始使用 AI 能力</p>
      <button class="btn-gradient" @click="openCreateModal">添加第一个模型</button>
    </div>

    <!-- Create / Edit Provider modal -->
    <Teleport to="body">
      <div v-if="showFormModal" class="modal-backdrop" @click.self="closeFormModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">{{ editingProvider ? '编辑模型供应商' : '添加模型供应商' }}</div>
            <button class="modal-close" @click="closeFormModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">名称 <span class="required">*</span></label>
              <input
                v-model="form.name"
                class="form-input"
                placeholder="例如：OpenAI 官方、DeepSeek"
                maxlength="100"
              />
            </div>
            <div class="form-group">
              <label class="form-label">供应商类型 <span class="required">*</span></label>
              <select v-model="form.provider" class="form-input">
                <option value="" disabled>选择供应商</option>
                <option value="openai">OpenAI</option>
                <option value="deepseek">DeepSeek</option>
                <option value="dashscope">阿里云 DashScope</option>
                <option value="custom">自定义（OpenAI 兼容）</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">API Key <span class="required">*</span></label>
              <input
                v-model="form.apiKey"
                class="form-input"
                type="password"
                :placeholder="editingProvider ? '留空则不修改' : 'sk-...'"
              />
            </div>
            <div class="form-group">
              <label class="form-label">Base URL</label>
              <input
                v-model="form.baseUrl"
                class="form-input"
                placeholder="https://api.openai.com/v1（留空使用默认）"
              />
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeFormModal">取消</button>
            <button class="btn-gradient" :disabled="!canSubmit || submitting" @click="handleSubmit">
              <span v-if="submitting" class="btn-spinner"></span>
              {{ submitting ? '保存中...' : (editingProvider ? '保存' : '添加') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Add Model modal -->
    <Teleport to="body">
      <div v-if="showAddModelModal" class="modal-backdrop" @click.self="closeAddModelModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">添加模型 - {{ addModelProvider?.name }}</div>
            <button class="modal-close" @click="closeAddModelModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">模型类型 <span class="required">*</span></label>
              <select v-model="addModelForm.modelType" class="form-input">
                <option value="chat">Chat（对话模型）</option>
                <option value="embedding">Embedding（向量化模型）</option>
                <option value="rerank">Rerank（重排序模型）</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">模型 ID <span class="required">*</span></label>
              <input
                v-model="addModelForm.modelId"
                class="form-input"
                placeholder="API 调用时的 model 值，如 gpt-4o、text-embedding-v3"
              />
              <div class="form-hint">这是调用 API 时传给 /v1/chat/completions 或 /v1/embeddings 的 model 字段</div>
            </div>
            <div class="form-group">
              <label class="form-label">显示名称</label>
              <input
                v-model="addModelForm.displayName"
                class="form-input"
                placeholder="例如：GPT-4o（留空则使用模型 ID）"
              />
            </div>
            <div class="form-group">
              <label class="form-label">上下文长度</label>
              <input
                v-model.number="addModelForm.contextSize"
                class="form-input"
                type="number"
                placeholder="4096"
              />
            </div>
            <div v-if="addModelForm.modelType === 'embedding'" class="form-group">
              <div class="dim-auto-hint">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--text-muted); flex-shrink: 0;">
                  <circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" />
                </svg>
                <span>向量维度无需填写，系统会在首次使用该模型时自动探测</span>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeAddModelModal">取消</button>
            <button class="btn-gradient" :disabled="!addModelForm.modelType || !addModelForm.modelId.trim() || addingModel" @click="handleAddModel">
              <span v-if="addingModel" class="btn-spinner"></span>
              {{ addingModel ? '添加中...' : '添加' }}
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
              删除模型供应商
            </div>
            <button class="modal-close" @click="deleteTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">
              确定要删除模型供应商「<strong>{{ deleteTarget.name }}</strong>」吗？
            </p>
            <p class="confirm-hint">
              该供应商下的所有模型配置都会被一并删除，已关联的知识库将回退到全局默认配置。
            </p>
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

    <!-- Delete model confirm modal -->
    <Teleport to="body">
      <div v-if="deleteModelTarget" class="modal-backdrop" @click.self="deleteModelTarget = null">
        <div class="modal-card modal-card-sm">
          <div class="modal-header">
            <div class="modal-title confirm-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="color: var(--red);">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              删除模型
            </div>
            <button class="modal-close" @click="deleteModelTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">
              确定要删除模型「<strong>{{ deleteModelTarget.displayName || deleteModelTarget.modelId }}</strong>」吗？
            </p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="deleteModelTarget = null">取消</button>
            <button class="btn-danger" :disabled="deletingModel" @click="confirmDeleteModel">
              <span v-if="deletingModel" class="btn-spinner"></span>
              {{ deletingModel ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMessage, NPagination } from 'naive-ui'
import type { ModelProvider, ModelCheckResult, Model } from '@/types'
import {
  listModelProviders,
  createModelProvider,
  updateModelProvider,
  deleteModelProvider,
  checkModelProvider,
  listModels,
  createModel,
  deleteModel,
} from '@/services/model'

const message = useMessage()

const providers = ref<ModelProvider[]>([])
const providerModels = ref<Record<string, Model[]>>({})
const showFormModal = ref(false)
const editingProvider = ref<ModelProvider | null>(null)
const submitting = ref(false)
const checkingId = ref<string | null>(null)
const checkResults = ref<Record<string, ModelCheckResult>>({})
const deleteTarget = ref<ModelProvider | null>(null)
const deleting = ref(false)
const providerPage = ref(1)
const providerPageSize = ref(20)
const providerTotal = ref(0)

// Delete model confirmation
const deleteModelTarget = ref<Model | null>(null)
const deletingModel = ref(false)

// Add model modal
const showAddModelModal = ref(false)
const addModelProvider = ref<ModelProvider | null>(null)
const addingModel = ref(false)
const addModelForm = ref({
  modelType: 'chat',
  modelId: '',
  displayName: '',
  contextSize: 4096,
})

const form = ref({
  name: '',
  provider: '',
  apiKey: '',
  baseUrl: '',
})

const canSubmit = computed(() => {
  return form.value.name.trim() && form.value.provider &&
    (editingProvider.value ? true : form.value.apiKey.trim())
})

onMounted(async () => {
  await loadProviders()
  await loadAllModels()
})

async function loadProviders() {
  try {
    const res = await listModelProviders(providerPage.value, providerPageSize.value)
    const data = res.data.data
    providers.value = data?.records || []
    providerTotal.value = data?.total || 0
  } catch {
    providers.value = []
  }
}

async function loadAllModels() {
  for (const p of providers.value) {
    try {
      const res = await listModels(p.id)
      providerModels.value[p.id] = res.data.data?.records || []
    } catch {
      providerModels.value[p.id] = []
    }
  }
}

function handleProviderPageChange(p: number) {
  providerPage.value = p
  loadProviders().then(loadAllModels)
}

function openCreateModal() {
  editingProvider.value = null
  form.value = { name: '', provider: '', apiKey: '', baseUrl: '' }
  showFormModal.value = true
}

function openEditModal(p: ModelProvider) {
  editingProvider.value = p
  form.value = {
    name: p.name,
    provider: p.provider,
    apiKey: '',
    baseUrl: p.baseUrl,
  }
  showFormModal.value = true
}

function closeFormModal() {
  showFormModal.value = false
  editingProvider.value = null
}

async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    if (editingProvider.value) {
      const data: Record<string, string> = {
        name: form.value.name.trim(),
        provider: form.value.provider,
        baseUrl: form.value.baseUrl.trim(),
      }
      if (form.value.apiKey.trim()) {
        data.apiKey = form.value.apiKey.trim()
      }
      await updateModelProvider(editingProvider.value.id, data)
      message.success('更新成功')
    } else {
      await createModelProvider({
        name: form.value.name.trim(),
        provider: form.value.provider,
        apiKey: form.value.apiKey.trim(),
        baseUrl: form.value.baseUrl.trim(),
      })
      message.success('添加成功')
    }
    closeFormModal()
    await loadProviders()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

function handleDelete(p: ModelProvider) {
  deleteTarget.value = p
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteModelProvider(deleteTarget.value.id)
    message.success(`模型供应商「${deleteTarget.value.name}」已删除`)
    deleteTarget.value = null
    await loadProviders()
    await loadAllModels()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

async function handleCheck(p: ModelProvider) {
  checkingId.value = p.id
  try {
    const res = await checkModelProvider(p.id)
    checkResults.value[p.id] = res.data.data
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '检测失败')
  } finally {
    checkingId.value = null
  }
}

function openAddModelModal(p: ModelProvider) {
  addModelProvider.value = p
  addModelForm.value = { modelType: 'chat', modelId: '', displayName: '', contextSize: 4096 }
  showAddModelModal.value = true
}

function closeAddModelModal() {
  showAddModelModal.value = false
  addModelProvider.value = null
}

async function handleAddModel() {
  if (!addModelProvider.value || !addModelForm.value.modelId.trim()) return
  addingModel.value = true
  try {
    await createModel(addModelProvider.value.id, {
      modelType: addModelForm.value.modelType,
      modelId: addModelForm.value.modelId.trim(),
      displayName: addModelForm.value.displayName.trim() || undefined,
      contextSize: addModelForm.value.contextSize || undefined,
    })
    message.success('模型添加成功')
    closeAddModelModal()
    await loadAllModels()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    addingModel.value = false
  }
}

function handleDeleteModel(m: Model) {
  deleteModelTarget.value = m
}

async function confirmDeleteModel() {
  const m = deleteModelTarget.value
  if (!m) return
  deletingModel.value = true
  try {
    await deleteModel(m.id)
    message.success(`模型「${m.displayName || m.modelId}」已删除`)
    deleteModelTarget.value = null
    await loadAllModels()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deletingModel.value = false
  }
}

function modelTypeLabel(type: string): string {
  const map: Record<string, string> = {
    chat: 'Chat',
    embedding: 'Embedding',
    rerank: 'Rerank',
    image2text: 'Vision',
  }
  return map[type] || type
}

function providerLabel(provider: string): string {
  const map: Record<string, string> = {
    openai: 'OpenAI',
    deepseek: 'DeepSeek',
    dashscope: 'DashScope',
    custom: '自定义',
  }
  return map[provider] || provider
}

function providerColor(provider: string): string {
  const map: Record<string, string> = {
    openai: 'linear-gradient(135deg, #10a37f20, #10a37f10)',
    deepseek: 'linear-gradient(135deg, #4f6ef720, #4f6ef710)',
    dashscope: 'linear-gradient(135deg, #ff6a0020, #ff6a0010)',
    custom: 'linear-gradient(135deg, #6366f120, #6366f110)',
  }
  return map[provider] || map.custom
}

/** 脱敏展示 API Key：仅保留后 4 位，其余以 **** 替代 */
function maskKey(key: string | undefined): string {
  if (!key) return '—'
  if (key.length <= 4) return '****'
  return '****' + key.slice(-4)
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
  margin-bottom: 24px;
  gap: 14px;
}

.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.provider-card {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  overflow: hidden;
  transition: var(--transition);
}

.provider-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--border);
}

.provider-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.provider-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text);
}

.provider-info {
  flex: 1;
  min-width: 0;
}

.provider-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
}

.provider-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-model-count {
  font-size: 12px;
  color: var(--text-muted);
}

.provider-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
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

.action-btn-danger:hover:not(:disabled) {
  border-color: var(--red);
  color: var(--red);
  background: var(--red-bg);
}

.provider-card-body {
  padding: 0 20px 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.provider-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.provider-detail-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.provider-detail-value {
  font-size: 13px;
  color: var(--text-secondary);
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

.provider-check-result {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
}

.check-detail {
  font-size: 12px;
  color: var(--text-secondary);
  flex: 1;
}

.check-latency {
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
  font-family: 'JetBrains Mono', monospace;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Models section */
.provider-models-section {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--border-light);
}

.provider-models-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.add-model-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  border: 1px dashed var(--primary-border);
  background: transparent;
  color: var(--primary);
  transition: var(--transition);
  text-transform: none;
  letter-spacing: 0;
}

.add-model-btn:hover {
  background: var(--indigo-bg);
  border-color: var(--primary);
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.model-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  border: 1px solid var(--border);
  background: var(--surface-alt);
  transition: var(--transition);
}

.model-tag:hover {
  border-color: var(--border);
}

.model-tag-chat {
  border-color: #6366f130;
  background: #6366f108;
}

.model-tag-embedding {
  border-color: #10a37f30;
  background: #10a37f08;
}

.model-tag-rerank {
  border-color: #f59e0b30;
  background: #f59e0b08;
}

.model-tag-type {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 1px 5px;
  border-radius: 3px;
  color: white;
}

.model-tag-chat .model-tag-type { background: #6366f1; }
.model-tag-embedding .model-tag-type { background: #10a37f; }
.model-tag-rerank .model-tag-type { background: #f59e0b; }

.model-tag-name {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--text);
  font-weight: 500;
}

.model-tag-dims {
  font-size: 10px;
  font-weight: 600;
  color: #10a37f;
  background: #10a37f12;
  padding: 1px 5px;
  border-radius: 3px;
}

.model-tag-delete {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: var(--transition);
}

.model-tag-delete:hover {
  background: var(--red-bg);
  color: var(--red);
}

.model-tags-empty {
  font-size: 12px;
  color: var(--text-muted);
  padding: 8px 0;
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
  box-shadow: var(--shadow-xl);
  animation: modalIn 0.25s ease;
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
}

.modal-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

.dim-auto-hint {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--bg-muted, #f8fafc);
  border: 1px dashed var(--border, #e2e8f0);
  color: var(--text-muted, #64748b);
  font-size: 12px;
  line-height: 1.5;
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

.modal-card-sm {
  width: min(420px, 90vw);
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
  color: var(--text);
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
</style>
