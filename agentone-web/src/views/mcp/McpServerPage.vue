<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h1 class="page-title">MCP 集成</h1>
        <p class="page-desc" style="margin-bottom: 0;">连接外部 MCP Server，自动发现工具并注册为虚拟 Skill 供 Agent 使用</p>
      </div>
      <button class="btn-gradient" @click="openCreateModal">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        添加 MCP Server
      </button>
    </div>

    <!-- Server list -->
    <div v-if="servers.length > 0" class="server-list">
      <div v-for="s in servers" :key="s.id" class="server-card">
        <div class="server-card-header">
          <div class="server-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="2" width="20" height="8" rx="2" />
              <rect x="2" y="14" width="20" height="8" rx="2" />
              <circle cx="6" cy="6" r="1" fill="currentColor" />
              <circle cx="6" cy="18" r="1" fill="currentColor" />
            </svg>
          </div>
          <div class="server-info">
            <div class="server-name-row">
              <span class="server-name">{{ s.name }}</span>
              <span class="badge badge-info">{{ transportLabel(s.transport) }}</span>
              <span class="badge" :class="s.connected ? 'badge-success' : 'badge-neutral'">
                {{ s.connected ? '已连接' : '未连接' }}
              </span>
              <span v-if="s.status !== 'active'" class="badge badge-warning">已停用</span>
            </div>
            <div class="server-desc">{{ s.description || '暂无描述' }}</div>
            <div class="server-meta mono">
              <template v-if="s.transport === 'stdio'">{{ s.command }} {{ (s.args || []).join(' ') }}</template>
              <template v-else>{{ s.url }}</template>
            </div>
          </div>
          <div class="server-actions">
            <button v-if="!s.connected" class="action-btn action-btn-primary" :disabled="busyId === s.id" @click="handleConnect(s)">
              <svg v-if="busyId === s.id" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <path d="M21 12a9 9 0 11-6.219-8.56" />
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14" /><path d="M12 5l7 7-7 7" />
              </svg>
              {{ busyId === s.id ? '连接中...' : '连接' }}
            </button>
            <button v-else class="action-btn" :disabled="busyId === s.id" @click="handleDisconnect(s)">
              {{ busyId === s.id ? '断开中...' : '断开' }}
            </button>
            <button class="action-btn" @click="openToolsModal(s)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
              </svg>
              工具 {{ s.toolCount > 0 ? `(${s.toolCount})` : '' }}
            </button>
            <button class="action-btn" @click="openEditModal(s)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
              编辑
            </button>
            <button class="action-btn action-btn-danger" @click="deleteTarget = s">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
              </svg>
              删除
            </button>
          </div>
        </div>
        <div v-if="s.lastConnectedAt" class="server-card-footer">
          最近连接：{{ formatTime(s.lastConnectedAt) }}
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="total > pageSize" class="pagination-wrap">
      <n-pagination :page="page" :page-size="pageSize" :item-count="total" @update:page="handlePageChange" />
    </div>

    <!-- Empty state -->
    <div v-else-if="!loading" class="page-card" style="text-align: center; padding: 80px 24px;">
      <div style="margin-bottom: 16px;">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary); opacity: 0.4;">
          <rect x="2" y="2" width="20" height="8" rx="2" />
          <rect x="2" y="14" width="20" height="8" rx="2" />
          <circle cx="6" cy="6" r="1" fill="currentColor" />
          <circle cx="6" cy="18" r="1" fill="currentColor" />
        </svg>
      </div>
      <h3 style="font-size: 17px; color: var(--text-secondary); margin-bottom: 8px; font-weight: 700;">暂无 MCP Server</h3>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 20px;">支持 stdio / SSE / Streamable HTTP 三种传输方式</p>
      <button class="btn-gradient" @click="openCreateModal">添加第一个 MCP Server</button>
    </div>

    <!-- Create / Edit modal -->
    <Teleport to="body">
      <div v-if="showFormModal" class="modal-backdrop" @click.self="closeFormModal">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">{{ editingServer ? '编辑 MCP Server' : '添加 MCP Server' }}</div>
            <button class="modal-close" @click="closeFormModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">名称 <span class="required">*</span></label>
              <input v-model="form.name" class="form-input" placeholder="例如：本地工具集" maxlength="100" />
            </div>
            <div class="form-group">
              <label class="form-label">描述</label>
              <input v-model="form.description" class="form-input" placeholder="这个 MCP Server 提供什么能力" />
            </div>
            <div class="form-group">
              <label class="form-label">传输方式 <span class="required">*</span></label>
              <select v-model="form.transport" class="form-input">
                <option value="stdio">stdio（本地子进程）</option>
                <option value="sse">SSE（HTTP 长连接）</option>
                <option value="streamable_http">Streamable HTTP</option>
              </select>
            </div>
            <template v-if="form.transport === 'stdio'">
              <div class="form-group">
                <label class="form-label">启动命令 <span class="required">*</span></label>
                <input v-model="form.command" class="form-input mono" placeholder="python3 / npx / node" />
              </div>
              <div class="form-group">
                <label class="form-label">命令参数（每行一个）</label>
                <textarea v-model="form.argsText" class="form-input form-textarea mono" rows="3" placeholder="/path/to/server.py"></textarea>
              </div>
            </template>
            <template v-else>
              <div class="form-group">
                <label class="form-label">服务地址 <span class="required">*</span></label>
                <input v-model="form.url" class="form-input mono" placeholder="https://mcp.example.com/sse" />
              </div>
              <div class="form-group">
                <label class="form-label">自定义请求头（JSON）</label>
                <textarea v-model="form.headersText" class="form-input form-textarea mono" rows="3" placeholder='{"Authorization": "Bearer xxx"}'></textarea>
              </div>
            </template>
            <div class="form-group">
              <label class="form-label">超时时间（毫秒）</label>
              <input v-model.number="form.timeoutMs" class="form-input" type="number" min="1000" placeholder="30000" />
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeFormModal">取消</button>
            <button class="btn-gradient" :disabled="!canSubmit || submitting" @click="handleSubmit">
              <span v-if="submitting" class="btn-spinner"></span>
              {{ submitting ? '保存中...' : (editingServer ? '保存' : '添加') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Tools modal -->
    <Teleport to="body">
      <div v-if="toolsServer" class="modal-backdrop" @click.self="toolsServer = null">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">已发现工具 - {{ toolsServer.name }}</div>
            <button class="modal-close" @click="toolsServer = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div v-if="toolsLoading" class="tools-empty">加载中...</div>
            <div v-else-if="tools.length === 0" class="tools-empty">
              尚未发现工具。请先连接该 Server；连接成功后工具会自动注册为虚拟 Skill。
            </div>
            <div v-else class="tool-list">
              <div v-for="t in tools" :key="t.skillId" class="tool-item">
                <div class="tool-head">
                  <span class="tool-name mono">{{ t.toolName }}</span>
                  <span class="tool-skill-id mono">{{ t.skillId }}</span>
                </div>
                <div class="tool-desc">{{ t.description || '暂无描述' }}</div>
                <details class="tool-schema">
                  <summary>输入 Schema</summary>
                  <pre class="mono">{{ prettyJson(t.inputSchema) }}</pre>
                </details>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="toolsServer = null">关闭</button>
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
              删除 MCP Server
            </div>
            <button class="modal-close" @click="deleteTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">确定要删除 MCP Server「<strong>{{ deleteTarget.name }}</strong>」吗？</p>
            <p class="confirm-hint">
              连接将被关闭，发现的虚拟工具会被注销。若仍有工具被 Agent 绑定，删除将被拒绝。
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage, NPagination } from 'naive-ui'
import {
  listMcpServers, createMcpServer, updateMcpServer, deleteMcpServer,
  connectMcpServer, disconnectMcpServer, listMcpTools,
  type McpServer, type McpServerForm, type McpTool,
} from '@/services/mcp'

const message = useMessage()

// ---------- list ----------
const servers = ref<McpServer[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const busyId = ref<string | null>(null)

onMounted(loadServers)

async function loadServers() {
  loading.value = true
  try {
    const res = await listMcpServers(page.value, pageSize.value)
    servers.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadServers()
}

function transportLabel(t: string): string {
  const map: Record<string, string> = { stdio: 'stdio', sse: 'SSE', streamable_http: 'Streamable HTTP' }
  return map[t] || t
}

function formatTime(t: string): string {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

// ---------- connect / disconnect ----------
async function handleConnect(s: McpServer) {
  busyId.value = s.id
  try {
    const res = await connectMcpServer(s.id)
    const tools = res.data.data || []
    message.success(`连接成功，发现 ${tools.length} 个工具`)
    await loadServers()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '连接失败')
  } finally {
    busyId.value = null
  }
}

async function handleDisconnect(s: McpServer) {
  busyId.value = s.id
  try {
    await disconnectMcpServer(s.id)
    message.success('已断开连接')
    await loadServers()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '断开失败')
  } finally {
    busyId.value = null
  }
}

// ---------- create / edit ----------
const showFormModal = ref(false)
const editingServer = ref<McpServer | null>(null)
const submitting = ref(false)
const form = ref({
  name: '',
  description: '',
  transport: 'stdio',
  url: '',
  command: '',
  argsText: '',
  headersText: '',
  timeoutMs: 30000,
})

const canSubmit = computed(() => {
  if (!form.value.name.trim() || !form.value.transport) return false
  if (form.value.transport === 'stdio') return !!form.value.command.trim()
  return !!form.value.url.trim()
})

function openCreateModal() {
  editingServer.value = null
  form.value = {
    name: '',
    description: '',
    transport: 'stdio',
    url: '',
    command: '',
    argsText: '',
    headersText: '',
    timeoutMs: 30000,
  }
  showFormModal.value = true
}

function openEditModal(s: McpServer) {
  editingServer.value = s
  form.value = {
    name: s.name,
    description: s.description || '',
    transport: s.transport,
    url: s.url || '',
    command: s.command || '',
    argsText: (s.args || []).join('\n'),
    headersText: s.headers && Object.keys(s.headers).length > 0 ? JSON.stringify(s.headers, null, 2) : '',
    timeoutMs: s.timeoutMs || 30000,
  }
  showFormModal.value = true
}

function closeFormModal() {
  showFormModal.value = false
  editingServer.value = null
}

async function handleSubmit() {
  if (!canSubmit.value) return
  let headers: Record<string, string> | undefined
  if (form.value.transport !== 'stdio' && form.value.headersText.trim()) {
    try {
      headers = JSON.parse(form.value.headersText)
    } catch {
      message.error('请求头不是合法 JSON')
      return
    }
  }
  submitting.value = true
  try {
    const payload: McpServerForm = {
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
      transport: form.value.transport,
      timeoutMs: form.value.timeoutMs || undefined,
      headers,
    }
    if (form.value.transport === 'stdio') {
      payload.command = form.value.command.trim()
      payload.args = form.value.argsText
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l.length > 0)
    } else {
      payload.url = form.value.url.trim()
    }
    if (editingServer.value) {
      await updateMcpServer(editingServer.value.id, payload)
      message.success('更新成功')
    } else {
      await createMcpServer(payload)
      message.success('添加成功，可点击「连接」发现工具')
    }
    closeFormModal()
    await loadServers()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

// ---------- delete ----------
const deleteTarget = ref<McpServer | null>(null)
const deleting = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteMcpServer(deleteTarget.value.id)
    message.success(`MCP Server「${deleteTarget.value.name}」已删除`)
    deleteTarget.value = null
    await loadServers()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

// ---------- tools ----------
const toolsServer = ref<McpServer | null>(null)
const tools = ref<McpTool[]>([])
const toolsLoading = ref(false)

async function openToolsModal(s: McpServer) {
  toolsServer.value = s
  tools.value = []
  toolsLoading.value = true
  try {
    const res = await listMcpTools(s.id)
    tools.value = res.data.data || []
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载工具失败')
  } finally {
    toolsLoading.value = false
  }
}

function prettyJson(json: string | null | undefined): string {
  if (!json) return '(无)'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
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
  margin-bottom: 24px;
  gap: 14px;
}

.server-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.server-card {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  overflow: hidden;
  transition: var(--transition);
}

.server-card:hover {
  box-shadow: var(--shadow-md);
}

.server-card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 20px;
}

.server-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--indigo-bg);
  color: var(--primary);
}

.server-info {
  flex: 1;
  min-width: 0;
}

.server-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.server-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.server-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 6px;
}

.server-meta {
  font-size: 12px;
  color: var(--text-muted);
  word-break: break-all;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

.server-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.server-card-footer {
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

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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
  margin-top: 4px;
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

/* Tools modal */
.tools-empty {
  font-size: 13px;
  color: var(--text-muted);
  padding: 24px;
  text-align: center;
  line-height: 1.6;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tool-item {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px;
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.tool-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.tool-skill-id {
  font-size: 11px;
  color: var(--text-muted);
  word-break: break-all;
}

.tool-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 6px;
}

.tool-schema summary {
  font-size: 12px;
  font-weight: 600;
  color: var(--primary);
  cursor: pointer;
}

.tool-schema pre {
  margin: 8px 0 0;
  padding: 10px;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  font-size: 11px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
