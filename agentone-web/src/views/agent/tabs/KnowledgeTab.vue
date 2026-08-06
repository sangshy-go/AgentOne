<template>
  <div>
    <div class="kb-header">
      <div>
        <div class="kb-title">绑定知识库</div>
        <div class="kb-desc">绑定后 Agent 将基于知识库内容回答用户问题（RAG）</div>
      </div>
      <button class="btn-gradient" @click="showBindModal = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        绑定知识库
      </button>
    </div>

    <!-- Bound list -->
    <div v-if="bindings.length > 0" class="kb-list">
      <div v-for="b in bindings" :key="b.id" class="kb-bound-item">
        <div class="kb-bound-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
          </svg>
        </div>
        <div class="kb-bound-info">
          <div class="kb-bound-name">{{ b.knowledgeName }}</div>
          <div class="kb-bound-params">
            <label class="kb-param">
              <span class="kb-param-label">Top-K</span>
              <input
                type="number"
                class="kb-param-input"
                min="1"
                max="20"
                step="1"
                :value="b.topK"
                title="每次检索返回的最大分块数（1~20）"
                @change="onTopKChange(b, $event)"
              />
            </label>
            <label class="kb-param">
              <span class="kb-param-label">相似度阈值</span>
              <input
                type="number"
                class="kb-param-input"
                min="0"
                max="1"
                step="0.05"
                :value="b.similarityThreshold"
                title="低于该分数的分块不参与回答（0~1）"
                @change="onThresholdChange(b, $event)"
              />
            </label>
          </div>
        </div>
        <button class="btn-unbind" @click="handleUnbind(b.id)">解绑</button>
      </div>
    </div>

    <div v-else class="kb-empty">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary);">
        <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
      </svg>
      <div class="kb-empty-text">暂未绑定知识库</div>
      <div class="kb-empty-hint">点击上方按钮绑定，让 Agent 拥有专业知识</div>
    </div>

    <!-- Bind modal -->
    <Teleport to="body">
      <div v-if="showBindModal" class="modal-backdrop" @click.self="showBindModal = false">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">选择知识库</div>
            <button class="modal-close" @click="showBindModal = false">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div v-if="availableBases.length === 0" class="modal-empty">
              暂无可用知识库，请先在「知识库」页面创建
            </div>
            <div v-else class="kb-picker-list">
              <div
                v-for="kb in availableBases"
                :key="kb.id"
                class="kb-picker-item"
                :class="{ selected: selectedKbId === kb.id }"
                @click="selectedKbId = kb.id"
              >
                <div class="kb-picker-check">
                  <div v-if="selectedKbId === kb.id" class="kb-picker-check-inner" />
                </div>
                <div class="kb-picker-info">
                  <div class="kb-picker-name">{{ kb.name }}</div>
                  <div class="kb-picker-meta">
                    {{ kb.docCount || 0 }} 个文档 · {{ kb.chunkCount || 0 }} 个分块
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="showBindModal = false">取消</button>
            <button class="btn-gradient" :disabled="!selectedKbId" @click="handleBind">确认绑定</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useMessage } from 'naive-ui'
import type { Agent, KnowledgeBase, KnowledgeBinding } from '@/types'
import { listBindings, bindKnowledge, unbindKnowledge, updateBinding } from '@/services/knowledge'
import { listKnowledgeBases } from '@/services/knowledge'

const props = defineProps<{ agent: Agent | null }>()
const message = useMessage()

const bindings = ref<KnowledgeBinding[]>([])
const allBases = ref<KnowledgeBase[]>([])
const showBindModal = ref(false)
const selectedKbId = ref<string | null>(null)

// 未被绑定的知识库
const availableBases = computed(() => {
  const boundIds = new Set(bindings.value.map((b) => b.knowledgeId))
  return allBases.value.filter((kb) => !boundIds.has(kb.id))
})

watch(
  () => props.agent?.id,
  async (id) => {
    if (id) await loadBindings(id)
  },
  { immediate: true }
)

watch(showBindModal, async (v) => {
  if (v) {
    try {
      const res = await listKnowledgeBases(1, 100)
      allBases.value = res.data.data?.records || []
    } catch {
      allBases.value = []
      message.error('知识库列表加载失败，请稍后重试')
    }
    selectedKbId.value = null
  }
})

async function loadBindings(agentId: string) {
  try {
    const res = await listBindings(agentId)
    bindings.value = res.data.data || []
  } catch {
    bindings.value = []
    message.error('绑定列表加载失败，请稍后重试')
  }
}

async function handleBind() {
  if (!props.agent || !selectedKbId.value) return
  try {
    await bindKnowledge({
      agentId: props.agent.id,
      knowledgeId: selectedKbId.value,
      topK: 5,
      similarityThreshold: 0.7,
    })
    message.success('绑定成功')
    showBindModal.value = false
    await loadBindings(props.agent.id)
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '绑定失败')
  }
}

async function handleUnbind(bindingId: string) {
  if (!props.agent) return
  if (!confirm('确认解绑该知识库？')) return
  try {
    await unbindKnowledge(bindingId)
    message.success('已解绑')
    await loadBindings(props.agent.id)
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '解绑失败')
  }
}

// 绑定参数调整：Top-K 与相似度阈值，校验失败时回滚输入框
async function onTopKChange(b: KnowledgeBinding, e: Event) {
  const el = e.target as HTMLInputElement
  const v = Math.round(Number(el.value))
  if (!Number.isFinite(v) || v < 1 || v > 20) {
    message.warning('Top-K 需为 1~20 的整数')
    el.value = String(b.topK)
    return
  }
  await saveBindingParam(b, { topK: v })
}

async function onThresholdChange(b: KnowledgeBinding, e: Event) {
  const el = e.target as HTMLInputElement
  const v = Number(el.value)
  if (!Number.isFinite(v) || v < 0 || v > 1) {
    message.warning('相似度阈值需为 0~1 之间的数')
    el.value = String(b.similarityThreshold)
    return
  }
  await saveBindingParam(b, { similarityThreshold: v })
}

async function saveBindingParam(
  b: KnowledgeBinding,
  patch: { topK?: number; similarityThreshold?: number }
) {
  try {
    await updateBinding(b.id, patch)
    Object.assign(b, patch)
    message.success('已更新绑定参数')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '更新失败')
  }
}
</script>

<style scoped>
.kb-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 16px;
}

.kb-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
}

.kb-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kb-bound-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  transition: var(--transition);
}

.kb-bound-item:hover {
  border-color: var(--primary-border);
  box-shadow: var(--shadow-sm);
}

.kb-bound-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--indigo-bg);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kb-bound-info {
  flex: 1;
  min-width: 0;
}

.kb-bound-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 2px;
}

.kb-bound-params {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.kb-param {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: default;
}

.kb-param-label {
  font-size: 12px;
  color: var(--text-muted);
}

.kb-param-input {
  width: 64px;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}

.kb-param-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.btn-unbind {
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--red-border);
  background: var(--red-bg);
  color: var(--red);
  transition: var(--transition);
}

.btn-unbind:hover {
  background: var(--red);
  color: white;
  border-color: var(--red);
}

.kb-empty {
  text-align: center;
  padding: 60px 24px;
  background: var(--surface-alt);
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}

.kb-empty-text {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
  margin: 12px 0 4px;
}

.kb-empty-hint {
  font-size: 12px;
  color: var(--text-muted);
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
  width: min(520px, 90vw);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
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
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
}

.modal-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--text-muted);
  font-size: 13px;
}

.kb-picker-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kb-picker-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  transition: var(--transition);
}

.kb-picker-item:hover {
  border-color: var(--primary-border);
  background: var(--indigo-bg);
}

.kb-picker-item.selected {
  border-color: var(--primary);
  background: var(--indigo-bg);
  box-shadow: var(--glow-primary);
}

.kb-picker-check {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--border-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: var(--transition);
}

.kb-picker-item.selected .kb-picker-check {
  border-color: var(--primary);
}

.kb-picker-check-inner {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--grad-primary-2);
}

.kb-picker-info {
  flex: 1;
  min-width: 0;
}

.kb-picker-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 2px;
}

.kb-picker-meta {
  font-size: 11px;
  color: var(--text-muted);
}

.modal-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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

.btn-secondary-custom:disabled,
.btn-gradient:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
