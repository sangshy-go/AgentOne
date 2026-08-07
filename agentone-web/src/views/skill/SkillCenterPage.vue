<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h1 class="page-title">Skill 中心</h1>
        <p class="page-desc" style="margin-bottom: 0;">管理内置能力、API 模式 Skill 与 MCP 工具，支持测试与三步调试</p>
      </div>
      <div class="toolbar-actions">
        <button class="btn-secondary-custom" @click="openDebugWizard()">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="6 3 20 12 6 21 6 3" />
          </svg>
          调试向导
        </button>
        <button class="btn-gradient" @click="openCreateModal">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          创建 Skill
        </button>
      </div>
    </div>

    <!-- Skill list -->
    <div v-if="skills.length > 0" class="skill-list">
      <div v-for="s in skills" :key="s.id" class="skill-card">
        <div class="skill-icon" :class="'skill-icon-' + s.type">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
          </svg>
        </div>
        <div class="skill-info">
          <div class="skill-name-row">
            <span class="skill-name">{{ s.name }}</span>
            <span class="badge" :class="typeBadge(s.type)">{{ typeLabel(s.type) }}</span>
            <span v-if="s.status !== 'active'" class="badge badge-neutral">{{ s.status }}</span>
          </div>
          <div class="skill-desc">{{ s.description || '暂无描述' }}</div>
          <div class="skill-meta">
            <span class="mono">v{{ s.version }}</span>
            <span v-if="s.source" class="skill-source">{{ s.source }}</span>
          </div>
        </div>
        <div class="skill-actions">
          <button class="action-btn" @click="openDebugWizard(s)" title="三步调试">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="6 3 20 12 6 21 6 3" />
            </svg>
            调试
          </button>
          <button v-if="s.type === 'api'" class="action-btn" @click="openTestModal(s)" title="测试调用">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
            </svg>
            测试
          </button>
          <button v-if="s.type === 'api'" class="action-btn" @click="openEditModal(s)" title="编辑">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
            编辑
          </button>
          <button v-if="s.type === 'api'" class="action-btn action-btn-danger" @click="deleteTarget = s" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6" />
              <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
            </svg>
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="total > pageSize" class="pagination-wrap">
      <n-pagination :page="page" :page-size="pageSize" :item-count="total" @update:page="handlePageChange" />
    </div>

    <!-- Empty state -->
    <div v-else-if="!loading" class="page-card" style="text-align: center; padding: 80px 24px;">
      <h3 style="font-size: 17px; color: var(--text-secondary); margin-bottom: 8px; font-weight: 700;">暂无可用 Skill</h3>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 20px;">创建 API 模式 Skill，或在 MCP 集成页连接外部工具</p>
      <button class="btn-gradient" @click="openCreateModal">创建第一个 Skill</button>
    </div>

    <!-- Create / Edit modal -->
    <Teleport to="body">
      <div v-if="showFormModal" class="modal-backdrop" @click.self="closeFormModal">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">{{ editingSkill ? '编辑 Skill' : '创建 API 模式 Skill' }}</div>
            <button class="modal-close" @click="closeFormModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">名称 <span class="required">*</span></label>
              <input v-model="form.name" class="form-input" placeholder="例如：订单查询" maxlength="100" />
            </div>
            <div class="form-group">
              <label class="form-label">描述</label>
              <input v-model="form.description" class="form-input" placeholder="这个 Skill 做什么（供 LLM 理解何时调用）" />
            </div>
            <div class="form-group">
              <label class="form-label">API 配置（JSON） <span class="required">*</span></label>
              <textarea
                v-model="form.config"
                class="form-input form-textarea mono"
                rows="5"
                placeholder='{"url": "https://api.example.com/query", "method": "POST", "headers": {}, "timeout": 10000}'
              ></textarea>
              <div class="form-hint">必须包含 url；method 缺省 GET；支持 headers、timeout（毫秒）</div>
            </div>
            <div class="form-group">
              <label class="form-label">输入参数 JSON Schema</label>
              <textarea
                v-model="form.inputSchema"
                class="form-input form-textarea mono"
                rows="6"
                placeholder='{"type": "object", "properties": {"orderId": {"type": "string"}}, "required": ["orderId"]}'
              ></textarea>
              <div class="form-hint">供 LLM function calling 生成参数；留空表示无参数</div>
            </div>
            <div class="form-group">
              <label class="form-label">版本号</label>
              <input v-model="form.version" class="form-input" placeholder="1.0.0" />
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeFormModal">取消</button>
            <button class="btn-gradient" :disabled="!canSubmit || submitting" @click="handleSubmit">
              <span v-if="submitting" class="btn-spinner"></span>
              {{ submitting ? '保存中...' : (editingSkill ? '保存' : '创建') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Test modal -->
    <Teleport to="body">
      <div v-if="testTarget" class="modal-backdrop" @click.self="closeTestModal">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">测试 Skill - {{ testTarget.name }}</div>
            <button class="modal-close" @click="closeTestModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">调用参数（JSON）</label>
              <textarea v-model="testParamsText" class="form-input form-textarea mono" rows="5" placeholder="{}"></textarea>
            </div>
            <div v-if="testResult" class="run-result">
              <div class="run-result-head">
                <span class="badge" :class="testResult.success ? 'badge-success' : 'badge-danger'">
                  {{ testResult.success ? '成功' : '失败' }}
                </span>
                <span v-if="testResult.durationMs != null" class="run-latency">{{ testResult.durationMs }}ms</span>
              </div>
              <pre v-if="testResult.success" class="run-result-body">{{ formatJson(testResult.data) }}</pre>
              <div v-else class="run-result-error">{{ testResult.errorMessage }}</div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeTestModal">关闭</button>
            <button class="btn-gradient" :disabled="testing" @click="handleTest">
              <span v-if="testing" class="btn-spinner"></span>
              {{ testing ? '执行中...' : '执行测试' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Debug wizard modal -->
    <Teleport to="body">
      <div v-if="showDebugModal" class="modal-backdrop" @click.self="closeDebugWizard">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">Skill 调试向导</div>
            <div class="debug-steps">
              <span v-for="i in 3" :key="i" class="debug-step" :class="{ active: debugStep === i, done: debugStep > i }">{{ i }}</span>
            </div>
            <button class="modal-close" @click="closeDebugWizard">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <!-- Step 1: choose target -->
            <template v-if="debugStep === 1">
              <div class="form-group">
                <label class="form-label">选择要调试的 Skill</label>
                <div v-if="debugTargets.length > 0" class="debug-target-list">
                  <div
                    v-for="t in debugTargets"
                    :key="t.id"
                    class="debug-target"
                    :class="{ selected: selectedTargetId === t.id }"
                    @click="selectTarget(t)"
                  >
                    <span class="badge" :class="typeBadge(t.type)">{{ typeLabel(t.type) }}</span>
                    <div class="debug-target-text">
                      <div class="debug-target-name">{{ t.name }}</div>
                      <div class="debug-target-desc">{{ t.description || '暂无描述' }}</div>
                    </div>
                  </div>
                </div>
                <div v-else class="debug-target-empty">当前工作空间没有可调试的 Skill</div>
              </div>
            </template>

            <!-- Step 2: params + preview -->
            <template v-else-if="debugStep === 2">
              <div class="debug-context">
                <span class="badge" :class="typeBadge(selectedTarget?.type || '')">{{ typeLabel(selectedTarget?.type || '') }}</span>
                <span class="debug-context-name">{{ selectedTarget?.name }}</span>
              </div>
              <div class="form-group">
                <label class="form-label">调用参数（JSON）</label>
                <textarea v-model="debugParamsText" class="form-input form-textarea mono" rows="6" placeholder="{}"></textarea>
                <div class="form-hint">上下文（用户 / 工作空间）自动注入当前登录态，无需填写</div>
              </div>
              <div v-if="preview" class="run-result">
                <div class="run-result-head">
                  <span class="badge" :class="preview.valid ? 'badge-success' : 'badge-danger'">
                    {{ preview.valid ? '参数校验通过' : '参数校验未通过' }}
                  </span>
                </div>
                <ul v-if="!preview.valid" class="run-result-errors">
                  <li v-for="(err, idx) in preview.errors" :key="idx">{{ err }}</li>
                </ul>
                <div v-else class="run-result-plan">
                  <span class="run-result-plan-label">执行计划</span>
                  <span class="mono">{{ preview.plan }}</span>
                </div>
              </div>
            </template>

            <!-- Step 3: run -->
            <template v-else>
              <div class="debug-context">
                <span class="badge" :class="typeBadge(selectedTarget?.type || '')">{{ typeLabel(selectedTarget?.type || '') }}</span>
                <span class="debug-context-name">{{ selectedTarget?.name }}</span>
                <span v-if="preview" class="mono debug-context-plan">{{ preview.plan }}</span>
              </div>
              <div class="form-group">
                <label class="form-label">关联会话 ID（可选）</label>
                <input v-model="debugSessionId" class="form-input mono" placeholder="留空则生成 debug- 前缀的调试会话" />
              </div>
              <div v-if="debugResult" class="run-result">
                <div class="run-result-head">
                  <span class="badge" :class="debugResult.success ? 'badge-success' : 'badge-danger'">
                    {{ debugResult.success ? '执行成功' : '执行失败' }}
                  </span>
                  <span v-if="debugResult.durationMs != null" class="run-latency">{{ debugResult.durationMs }}ms</span>
                </div>
                <pre v-if="debugResult.success" class="run-result-body">{{ formatJson(debugResult.data) }}</pre>
                <div v-else class="run-result-error">{{ debugResult.errorMessage }}</div>
                <div class="run-result-trace">
                  traceId: <span class="mono">{{ debugResult.traceId }}</span>
                  ｜ sessionId: <span class="mono">{{ debugResult.sessionId }}</span>
                </div>
              </div>
            </template>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeDebugWizard">取消</button>
            <button v-if="debugStep > 1" class="btn-secondary-custom" @click="debugStep--">上一步</button>
            <button v-if="debugStep === 1" class="btn-gradient" :disabled="!selectedTargetId" @click="goStep2">下一步</button>
            <button v-else-if="debugStep === 2" class="btn-gradient" :disabled="previewing" @click="handlePreview">
              <span v-if="previewing" class="btn-spinner"></span>
              {{ previewing ? '预检中...' : '参数预检' }}
            </button>
            <button v-else class="btn-gradient" :disabled="running" @click="handleRun">
              <span v-if="running" class="btn-spinner"></span>
              {{ running ? '执行中...' : '真实执行' }}
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
              删除 Skill
            </div>
            <button class="modal-close" @click="deleteTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">确定要删除 Skill「<strong>{{ deleteTarget.name }}</strong>」吗？</p>
            <p class="confirm-hint">若该 Skill 仍被 Agent 绑定，删除将被拒绝；请先在 Agent 详情中解绑。</p>
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
  listSkills, createSkill, updateSkill, deleteSkill, testSkill,
  listDebugTargets, debugPreview, debugRun,
  type SkillItem, type SkillForm, type SkillRunResult,
  type DebugTarget, type DebugPreviewResult, type DebugRunResult,
} from '@/services/skill'

const message = useMessage()

// ---------- list ----------
const skills = ref<SkillItem[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

onMounted(loadSkills)

async function loadSkills() {
  loading.value = true
  try {
    const res = await listSkills(page.value, pageSize.value)
    skills.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadSkills()
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { builtin: '内置', api: 'API', mcp: 'MCP' }
  return map[type] || type
}

function typeBadge(type: string): string {
  const map: Record<string, string> = { builtin: 'badge-neutral', api: 'badge-indigo', mcp: 'badge-purple' }
  return map[type] || 'badge-neutral'
}

// ---------- create / edit ----------
const showFormModal = ref(false)
const editingSkill = ref<SkillItem | null>(null)
const submitting = ref(false)
const form = ref({ name: '', description: '', config: '', inputSchema: '', version: '' })

const canSubmit = computed(() => form.value.name.trim() && form.value.config.trim())

function openCreateModal() {
  editingSkill.value = null
  form.value = {
    name: '',
    description: '',
    config: '{\n  "url": "",\n  "method": "GET"\n}',
    inputSchema: '',
    version: '1.0.0',
  }
  showFormModal.value = true
}

function openEditModal(s: SkillItem) {
  editingSkill.value = s
  form.value = {
    name: s.name,
    description: s.description || '',
    config: prettyOrRaw(s.config),
    inputSchema: prettyOrRaw(s.inputSchema),
    version: s.version || '',
  }
  showFormModal.value = true
}

function closeFormModal() {
  showFormModal.value = false
  editingSkill.value = null
}

async function handleSubmit() {
  if (!canSubmit.value) return
  // 前端先做 JSON 预检，错误提示比后端 400 更友好
  try {
    JSON.parse(form.value.config)
  } catch {
    message.error('API 配置不是合法 JSON')
    return
  }
  if (form.value.inputSchema.trim()) {
    try {
      JSON.parse(form.value.inputSchema)
    } catch {
      message.error('输入参数 Schema 不是合法 JSON')
      return
    }
  }
  submitting.value = true
  try {
    const payload: SkillForm = {
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
      config: form.value.config.trim(),
      inputSchema: form.value.inputSchema.trim() || undefined,
      version: form.value.version.trim() || undefined,
    }
    if (editingSkill.value) {
      await updateSkill(editingSkill.value.id, payload)
      message.success('更新成功')
    } else {
      await createSkill(payload)
      message.success('创建成功')
    }
    closeFormModal()
    await loadSkills()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

// ---------- delete ----------
const deleteTarget = ref<SkillItem | null>(null)
const deleting = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteSkill(deleteTarget.value.id)
    message.success(`Skill「${deleteTarget.value.name}」已删除`)
    deleteTarget.value = null
    await loadSkills()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

// ---------- test ----------
const testTarget = ref<SkillItem | null>(null)
const testParamsText = ref('{}')
const testResult = ref<SkillRunResult | null>(null)
const testing = ref(false)

function openTestModal(s: SkillItem) {
  testTarget.value = s
  testParamsText.value = sampleParamsOf(s.inputSchema)
  testResult.value = null
}

function closeTestModal() {
  testTarget.value = null
  testResult.value = null
}

async function handleTest() {
  if (!testTarget.value) return
  const params = parseParams(testParamsText.value)
  if (params === null) return
  testing.value = true
  try {
    const res = await testSkill(testTarget.value.id, params)
    testResult.value = res.data.data
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '测试失败')
  } finally {
    testing.value = false
  }
}

// ---------- debug wizard ----------
const showDebugModal = ref(false)
const debugStep = ref(1)
const debugTargets = ref<DebugTarget[]>([])
const selectedTargetId = ref('')
const selectedTarget = ref<DebugTarget | null>(null)
const debugParamsText = ref('{}')
const debugSessionId = ref('')
const preview = ref<DebugPreviewResult | null>(null)
const previewing = ref(false)
const debugResult = ref<DebugRunResult | null>(null)
const running = ref(false)

async function openDebugWizard(preset?: SkillItem) {
  debugStep.value = 1
  selectedTargetId.value = ''
  selectedTarget.value = null
  debugParamsText.value = '{}'
  debugSessionId.value = ''
  preview.value = null
  debugResult.value = null
  showDebugModal.value = true
  try {
    const res = await listDebugTargets()
    debugTargets.value = res.data.data || []
    if (preset) {
      const t = debugTargets.value.find((x) => x.id === preset.id)
      if (t) selectTarget(t)
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载调试目标失败')
  }
}

function closeDebugWizard() {
  showDebugModal.value = false
}

function selectTarget(t: DebugTarget) {
  selectedTargetId.value = t.id
  selectedTarget.value = t
}

function goStep2() {
  if (!selectedTarget.value) return
  debugParamsText.value = sampleParamsOf(selectedTarget.value.inputSchema)
  preview.value = null
  debugStep.value = 2
}

async function handlePreview() {
  if (!selectedTarget.value) return
  const params = parseParams(debugParamsText.value)
  if (params === null) return
  previewing.value = true
  try {
    const res = await debugPreview(selectedTarget.value.id, params)
    preview.value = res.data.data
    if (preview.value?.valid) {
      debugStep.value = 3
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '预检失败')
  } finally {
    previewing.value = false
  }
}

async function handleRun() {
  if (!selectedTarget.value) return
  const params = parseParams(debugParamsText.value)
  if (params === null) return
  running.value = true
  try {
    const res = await debugRun(selectedTarget.value.id, params, debugSessionId.value.trim() || undefined)
    debugResult.value = res.data.data
    message.success(debugResult.value?.success ? '执行成功（已写入调试审计）' : '执行完成，结果为失败')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '执行失败')
  } finally {
    running.value = false
  }
}

// ---------- helpers ----------
function parseParams(text: string): Record<string, unknown> | null {
  const raw = text.trim() || '{}'
  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      message.error('参数必须是 JSON 对象')
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    message.error('参数不是合法 JSON')
    return null
  }
}

/** 按 inputSchema 生成示例参数骨架，降低手填成本 */
function sampleParamsOf(inputSchema: string): string {
  try {
    const schema = JSON.parse(inputSchema) as { properties?: Record<string, { type?: string }> }
    const props = schema?.properties
    if (!props || typeof props !== 'object') return '{}'
    const sample: Record<string, unknown> = {}
    for (const [key, prop] of Object.entries(props)) {
      switch (prop?.type) {
        case 'integer':
        case 'number':
          sample[key] = 0
          break
        case 'boolean':
          sample[key] = true
          break
        case 'object':
          sample[key] = {}
          break
        case 'array':
          sample[key] = []
          break
        default:
          sample[key] = ''
      }
    }
    return JSON.stringify(sample, null, 2)
  } catch {
    return '{}'
  }
}

function prettyOrRaw(json: string | null | undefined): string {
  if (!json) return ''
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

function formatJson(data: unknown): string {
  return prettyOrRaw(data as string) || '(空)'
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

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skill-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  padding: 16px 20px;
  transition: var(--transition);
}

.skill-card:hover {
  box-shadow: var(--shadow-md);
}

.skill-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.skill-icon-builtin { background: var(--indigo-bg); color: var(--primary); }
.skill-icon-api { background: var(--green-bg); color: var(--green); }
.skill-icon-mcp { background: #f3e8ff; color: #9333ea; }

.skill-info {
  flex: 1;
  min-width: 0;
}

.skill-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.skill-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.skill-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 6px;
}

.skill-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.skill-source {
  font-size: 12px;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

.skill-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
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

@keyframes spin {
  to { transform: rotate(360deg); }
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

/* Debug wizard */
.debug-steps {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.debug-step {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border: 1.5px solid var(--border);
  color: var(--text-muted);
  background: #FFFFFF;
}

.debug-step.active {
  border-color: var(--primary);
  color: white;
  background: var(--primary);
}

.debug-step.done {
  border-color: var(--green);
  color: var(--green);
  background: var(--green-bg);
}

.debug-target-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}

.debug-target {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition);
}

.debug-target:hover {
  border-color: var(--primary-border);
}

.debug-target.selected {
  border-color: var(--primary);
  background: var(--indigo-bg);
}

.debug-target-text {
  flex: 1;
  min-width: 0;
}

.debug-target-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 2px;
}

.debug-target-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.debug-target-empty {
  font-size: 13px;
  color: var(--text-muted);
  padding: 24px;
  text-align: center;
}

.debug-context {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  flex-wrap: wrap;
}

.debug-context-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.debug-context-plan {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Run / preview result */
.run-result {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.run-result-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--surface-alt);
}

.run-latency {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
  font-family: 'JetBrains Mono', monospace;
}

.run-result-body {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', monospace;
}

.run-result-error {
  padding: 12px;
  font-size: 13px;
  color: var(--red);
  line-height: 1.6;
}

.run-result-errors {
  margin: 0;
  padding: 12px 12px 12px 28px;
  font-size: 13px;
  color: var(--red);
  line-height: 1.8;
}

.run-result-plan {
  padding: 12px;
  font-size: 13px;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.run-result-plan-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.run-result-trace {
  padding: 8px 12px;
  border-top: 1px solid var(--border-light);
  font-size: 11px;
  color: var(--text-muted);
}
</style>
