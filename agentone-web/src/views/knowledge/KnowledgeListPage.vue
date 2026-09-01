<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h1 class="page-title">知识库管理</h1>
        <p class="page-desc" style="margin-bottom: 0;">上传文档，构建 AI 知识体系，让 Agent 拥有专业知识</p>
      </div>
      <button class="btn-gradient" @click="showCreateModal = true">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        创建知识库
      </button>
    </div>

    <div class="kb-layout">
      <!-- Left: Knowledge base list -->
      <div class="kb-list-panel">
        <div class="kb-list-header">
          <span>知识库列表</span>
          <span class="kb-count">{{ baseTotal }}</span>
        </div>
        <div class="kb-list">
          <div
            v-for="kb in bases"
            :key="kb.id"
            class="kb-list-item"
            :class="{ active: selectedId === kb.id }"
            @click="handleSelect(kb.id)"
          >
            <div class="kb-list-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
              </svg>
            </div>
            <div class="kb-list-info">
              <div class="kb-list-name">{{ kb.name }}</div>
              <div class="kb-list-meta">
                {{ kb.docCount || 0 }} 文档 · {{ kb.chunkCount || 0 }} 分块
              </div>
            </div>
            <button class="kb-list-delete" @click.stop="handleDeleteBase(kb)" title="删除">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
              </svg>
            </button>
          </div>
          <div v-if="bases.length === 0" class="kb-list-empty">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary); opacity: 0.4;">
              <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
            </svg>
            <div>暂无知识库</div>
          </div>
        </div>
        <div v-if="baseTotal > basePageSize" class="pagination-wrap">
          <n-pagination
            :page="basePage"
            :page-size="basePageSize"
            :item-count="baseTotal"
            @update:page="handleBasePageChange"
          />
        </div>
      </div>

      <!-- Right: Documents / Details -->
      <div class="kb-detail-panel">
        <template v-if="selectedBase">
          <div class="doc-header">
            <div>
              <div class="doc-header-title">{{ selectedBase.name }}</div>
              <div class="doc-header-desc">{{ selectedBase.description || '暂无描述' }}</div>
              <button class="doc-header-edit" @click="openEditBase">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
                  <path d="M18.5 2.5a2.12 2.12 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
                </svg>
                编辑信息
              </button>
            </div>
            <div class="doc-stats">
              <div class="doc-stat-item">
                <div class="doc-stat-value">{{ selectedBase.docCount || 0 }}</div>
                <div class="doc-stat-label">文档</div>
              </div>
              <div class="doc-stat-item">
                <div class="doc-stat-value">{{ selectedBase.chunkCount || 0 }}</div>
                <div class="doc-stat-label">分块</div>
              </div>
            </div>
          </div>

          <!-- 配置信息 -->
          <div class="kb-config">
            <div class="kb-config-item">
              <span class="kb-config-label">Embedding 模型</span>
              <span class="kb-config-value">{{ embeddingModelDisplayName }}</span>
            </div>
            <div class="kb-config-item">
              <span class="kb-config-label">分块策略</span>
              <span class="kb-config-value">{{ chunkStrategyLabel(selectedBase.chunkStrategy) }}</span>
            </div>
            <div class="kb-config-item">
              <span class="kb-config-label">分块大小（token）</span>
              <span class="kb-config-value">{{ selectedBase.chunkSize ?? '—' }}</span>
            </div>
            <div class="kb-config-item">
              <span class="kb-config-label">重叠大小（字符）</span>
              <span class="kb-config-value">{{ selectedBase.chunkOverlap ?? '—' }}</span>
            </div>
            <div class="kb-config-item">
              <span class="kb-config-label">创建时间</span>
              <span class="kb-config-value">{{ formatDate(selectedBase.createdAt) }}</span>
            </div>
          </div>

          <!-- Retrieval test -->
          <div class="retrieval-test">
            <div class="retrieval-header" @click="showRetrievalTest = !showRetrievalTest">
              <div class="retrieval-title">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--primary);">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
                <span>检索测试</span>
              </div>
              <svg
                width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                :style="{ transform: showRetrievalTest ? 'rotate(180deg)' : 'rotate(0)', transition: 'transform 0.2s' }"
              >
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </div>
            <div v-if="showRetrievalTest" class="retrieval-body">
              <div class="retrieval-input-row">
                <input
                  v-model="retrievalQuery"
                  class="form-input"
                  placeholder="输入测试问题，例如：如何申请退款？"
                  @keyup.enter="handleSearch"
                />
                <select v-model.number="retrievalTopK" class="form-input retrieval-topk">
                  <option :value="3">Top 3</option>
                  <option :value="5">Top 5</option>
                  <option :value="10">Top 10</option>
                </select>
                <button
                  class="btn-gradient"
                  :disabled="!retrievalQuery.trim() || retrievalLoading"
                  @click="handleSearch"
                  style="white-space: nowrap;"
                >
                  <span v-if="retrievalLoading" class="btn-spinner" style="border-color: rgba(255,255,255,0.3); border-top-color: #fff;"></span>
                  {{ retrievalLoading ? '检索中...' : '检索' }}
                </button>
              </div>
              <div v-if="retrievalResults.length > 0" class="retrieval-results">
                <div v-for="(r, idx) in retrievalResults" :key="r.chunkId" class="retrieval-result-item">
                  <div class="retrieval-result-header">
                    <span class="retrieval-result-rank">#{{ idx + 1 }}</span>
                    <span class="retrieval-result-doc">{{ r.documentName }}</span>
                    <span class="retrieval-result-score">
                      相似度 {{ (r.score * 100).toFixed(1) }}%
                    </span>
                  </div>
                  <div class="retrieval-result-content">{{ r.content }}</div>
                </div>
              </div>
              <div v-else-if="retrievalSearched && !retrievalLoading" class="retrieval-empty">
                未检索到相关内容
              </div>
            </div>
          </div>

          <!-- Upload area -->
          <div
            class="upload-area"
            :class="{ dragging: isDragging, uploading: uploading }"
            @dragenter.prevent="isDragging = true"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleDrop"
            @click="triggerFileInput"
          >
            <input
              ref="fileInputRef"
              type="file"
              multiple
              accept=".txt,.md,.pdf,.doc,.docx,.csv"
              style="display: none;"
              @change="handleFileSelect"
            />
            <template v-if="uploading">
              <span class="upload-spinner"></span>
              <div class="upload-text">
                <strong>正在上传并解析文件...</strong>
              </div>
              <div class="upload-hint">上传完成后文档会自动进入处理流程</div>
            </template>
            <template v-else>
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="color: var(--primary);">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
              <div class="upload-text">
                <strong>拖拽文件到此处</strong> 或 点击选择文件
              </div>
              <div class="upload-hint">支持 TXT、Markdown、PDF、Word、HTML、CSV</div>
            </template>
          </div>

          <!-- Document list -->
          <div class="doc-list">
            <template v-if="documents.length > 0">
              <div class="doc-list-header">
                <span>文档列表</span>
                <span class="doc-count">{{ docTotal }}</span>
              </div>
              <div v-for="doc in documents" :key="doc.id" class="doc-item">
                <div class="doc-item-icon">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                  </svg>
                </div>
                <div class="doc-item-info">
                  <div class="doc-item-name">{{ doc.name }}</div>
                  <div class="doc-item-meta">
                    {{ formatSize(doc.size) }} · {{ doc.chunkCount || 0 }} 分块
                    <span v-if="doc.createdAt"> · {{ formatDate(doc.createdAt) }}</span>
                  </div>
                  <div
                    v-if="doc.status === 'error' && doc.errorMsg"
                    class="doc-item-error"
                    :title="doc.errorMsg"
                  >
                    失败原因：{{ doc.errorMsg }}
                  </div>
                </div>
                <div :class="['badge', statusBadge(doc.status)]">
                  {{ statusLabel(doc.status) }}
                </div>
                <button
                  v-if="doc.status === 'error'"
                  class="doc-item-action"
                  @click="handleRetryDoc(doc)"
                  title="重试处理"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="23 4 23 10 17 10" />
                    <path d="M20.49 15a9 9 0 11-2.12-9.36L23 10" />
                  </svg>
                </button>
                <button class="doc-item-delete" @click="handleDeleteDoc(doc)" title="删除">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </div>
            </template>
            <div v-else class="doc-list-empty">
              暂无文档，上传文件开始构建知识
            </div>
          </div>
          <div v-if="docTotal > docPageSize" class="pagination-wrap">
            <n-pagination
              :page="docPage"
              :page-size="docPageSize"
              :item-count="docTotal"
              @update:page="handleDocPageChange"
            />
          </div>
        </template>

        <template v-else>
          <div class="kb-detail-empty">
            <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary); opacity: 0.3;">
              <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
            </svg>
            <div class="kb-detail-empty-text">选择一个知识库查看详情</div>
            <div class="kb-detail-empty-hint">或创建一个新的知识库</div>
          </div>
        </template>
      </div>
    </div>

    <!-- Create modal -->
    <Teleport to="body">
      <div v-if="showCreateModal" class="modal-backdrop" @click.self="showCreateModal = false">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">创建知识库</div>
            <button class="modal-close" @click="showCreateModal = false">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">知识库名称 <span class="required">*</span></label>
              <input
                v-model="createForm.name"
                class="form-input"
                placeholder="例如：产品文档、技术规范"
                maxlength="100"
              />
            </div>
            <div class="form-group">
              <label class="form-label">描述</label>
              <textarea
                v-model="createForm.description"
                class="form-textarea"
                placeholder="描述该知识库的用途"
                rows="3"
                maxlength="500"
              />
            </div>
            <div class="form-group">
              <label class="form-label">Embedding 模型（向量化）<span class="required">*</span></label>
              <select v-model="createForm.embeddingModelId" class="form-input">
                <option value="" disabled>请选择 Embedding 模型</option>
                <option v-for="m in embeddingModels" :key="m.id" :value="m.id">
                  {{ m.displayName || m.modelId }}（{{ m.providerName }} · {{ m.dimensions || 1536 }}维）
                </option>
              </select>
              <div class="form-hint form-hint-warn">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="vertical-align: -1px; margin-right: 3px;">
                  <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
                Embedding 模型创建后不可更改。不同模型的向量维度不同，切换会导致已有数据失效。
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">分块策略</label>
              <select v-model="createForm.chunkStrategy" class="form-input">
                <option value="by-length">按长度切分（通用）</option>
                <option value="by-title">按标题切分（Markdown）</option>
                <option value="by-paragraph">按段落切分</option>
              </select>
              <div class="form-hint">{{ chunkStrategyHint }}</div>
            </div>
            <div class="form-row">
              <div class="form-group form-group-half">
                <label class="form-label">分块大小（token）</label>
                <input
                  v-model.number="createForm.chunkSize"
                  type="number"
                  class="form-input"
                  min="100"
                  max="4000"
                  step="50"
                />
              </div>
              <div class="form-group form-group-half">
                <label class="form-label">重叠大小（字符）</label>
                <input
                  v-model.number="createForm.chunkOverlap"
                  type="number"
                  class="form-input"
                  min="0"
                  max="500"
                  step="10"
                />
              </div>
            </div>
            <div class="form-hint">
              分块大小按 token 计（约 1 个中文字 ≈ 1~2 token）；重叠大小按字符计，用于相邻分块间保留衔接上下文。
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="showCreateModal = false">取消</button>
            <button class="btn-gradient" :disabled="!createForm.name.trim() || !createForm.embeddingModelId" @click="handleCreate">创建</button>
          </div>
        </div>
      </div>

      <!-- Edit knowledge base modal -->
      <div v-if="showEditModal" class="modal-backdrop" @click.self="showEditModal = false">
          <div class="modal-card">
            <div class="modal-header">
              <div class="modal-title">编辑知识库</div>
              <button class="modal-close" @click="showEditModal = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label class="form-label">知识库名称 <span class="required">*</span></label>
                <input v-model="editForm.name" class="form-input" maxlength="100" />
              </div>
              <div class="form-group">
                <label class="form-label">描述</label>
                <textarea v-model="editForm.description" class="form-textarea" rows="3" maxlength="500" />
              </div>
              <div class="form-hint">
                Embedding 模型创建后不可更改；分块参数修改不会重切已有文档，故此处仅可编辑名称与描述。
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn-secondary-custom" @click="showEditModal = false">取消</button>
              <button class="btn-gradient" :disabled="!editForm.name.trim() || savingBase" @click="handleEditBase">
                <span v-if="savingBase" class="btn-spinner"></span>
                {{ savingBase ? '保存中...' : '保存' }}
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
              删除知识库
            </div>
            <button class="modal-close" @click="deleteTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">
              确定要删除知识库「<strong>{{ deleteTarget.name }}</strong>」吗？
            </p>
            <div v-if="deleteDependentAgents.length > 0" class="confirm-dependents">
              <div class="confirm-dependents-title">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
                该知识库正被 {{ deleteDependentAgents.length }} 个 Agent 使用
              </div>
              <div class="confirm-dependents-names">{{ dependentAgentNames }}</div>
              <div class="confirm-dependents-note">删除后这些 Agent 将失去此知识来源，请确认后再操作。</div>
            </div>
            <p class="confirm-hint">
              该知识库下的所有文档和分块数据都会被一并删除，且无法恢复。
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

      <!-- Delete document confirm modal -->
      <div v-if="deleteDocTarget" class="modal-backdrop" @click.self="deleteDocTarget = null">
        <div class="modal-card modal-card-sm">
          <div class="modal-header">
            <div class="modal-title confirm-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="color: var(--red);">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              删除文档
            </div>
            <button class="modal-close" @click="deleteDocTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">
              确定要删除文档「<strong>{{ deleteDocTarget.name }}</strong>」吗？
            </p>
            <p class="confirm-hint">
              已生成的分块和向量数据会被同步清理，且无法恢复。
            </p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="deleteDocTarget = null">取消</button>
            <button class="btn-danger" :disabled="deletingDoc" @click="confirmDeleteDoc">
              <span v-if="deletingDoc" class="btn-spinner"></span>
              {{ deletingDoc ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useMessage, NPagination } from 'naive-ui'
import type { KnowledgeBase, Document, Model, SearchResult, Agent } from '@/types'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  listDocuments,
  uploadDocument,
  deleteDocument,
  retryDocument,
  searchKnowledge,
  listKnowledgeBindings,
} from '@/services/knowledge'
import { listModelsByType } from '@/services/model'
import { listAgents } from '@/services/agent'

const message = useMessage()

const bases = ref<KnowledgeBase[]>([])
const selectedId = ref<string | null>(null)
const documents = ref<Document[]>([])
const basePage = ref(1)
const basePageSize = ref(20)
const baseTotal = ref(0)
const docPage = ref(1)
const docPageSize = ref(20)
const docTotal = ref(0)
const isDragging = ref(false)
const showCreateModal = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const createForm = ref({
  name: '',
  description: '',
  embeddingModelId: '',
  chunkStrategy: 'by-length' as 'by-length' | 'by-title' | 'by-paragraph',
  chunkSize: 400,
  chunkOverlap: 60,
})

// 编辑知识库（H8：仅名称/描述，Embedding 与分块参数创建后不可改）
const showEditModal = ref(false)
const savingBase = ref(false)
const editForm = ref({ name: '', description: '' })

const chunkStrategyHint = computed(() => {
  const map: Record<string, string> = {
    'by-length': '按 token 数切分（在自然断句处断开），适合大多数文档',
    'by-title': '按 Markdown 标题（# / ## / ###）切分，适合结构化文档',
    'by-paragraph': '按段落（双换行）切分，小段落自动合并，适合对话记录',
  }
  return map[createForm.value.chunkStrategy] || ''
})
const embeddingModels = ref<Model[]>([])

// 删除确认
const deleteTarget = ref<KnowledgeBase | null>(null)
const deleting = ref(false)
const deleteDocTarget = ref<Document | null>(null)
const deletingDoc = ref(false)
// 依赖该知识库的 Agent 列表（删除前提示用）
const deleteDependentAgents = ref<Agent[]>([])
const dependentAgentNames = computed(() =>
  deleteDependentAgents.value.map((a) => a.name).join('、')
)

// 上传队列（简化：串行上传）
const uploading = ref(false)

// 检索测试
const showRetrievalTest = ref(false)
const retrievalQuery = ref('')
const retrievalTopK = ref(5)
const retrievalResults = ref<SearchResult[]>([])
const retrievalLoading = ref(false)
const retrievalSearched = ref(false)

async function handleSearch() {
  if (!selectedId.value || !retrievalQuery.value.trim()) return
  retrievalLoading.value = true
  retrievalSearched.value = true
  try {
    const res = await searchKnowledge(
      selectedId.value,
      retrievalQuery.value.trim(),
      retrievalTopK.value
    )
    retrievalResults.value = res.data.data || []
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '检索失败')
    retrievalResults.value = []
  } finally {
    retrievalLoading.value = false
  }
}

const selectedBase = computed(() => bases.value.find((b) => b.id === selectedId.value) || null)

// 解析选中知识库的 Embedding 模型展示名（从已加载的 embedding 模型列表中匹配）
const embeddingModelDisplayName = computed(() => {
  const kb = selectedBase.value
  if (!kb) return '—'
  if (kb.embeddingModelId) {
    const m = embeddingModels.value.find((x) => x.id === kb.embeddingModelId)
    if (m) {
      const name = m.displayName || m.modelId
      return m.providerName ? `${name}（${m.providerName}）` : name
    }
  }
  return kb.embeddingModel || '—'
})

function chunkStrategyLabel(strategy?: string): string {
  const map: Record<string, string> = {
    'by-length': '按长度切分',
    'by-title': '按标题切分',
    'by-paragraph': '按段落切分',
  }
  return (strategy && map[strategy]) || strategy || '—'
}

onMounted(async () => {
  await loadBases()
  try {
    const res = await listModelsByType('embedding')
    embeddingModels.value = res.data.data || []
  } catch {
    embeddingModels.value = []
  }
})

// 定时刷新文档状态（pending/processing 时）
let pollTimer: number | null = null
function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    if (!selectedId.value) return
    const hasPending = documents.value.some((d) => d.status === 'pending' || d.status === 'processing')
    if (hasPending) await loadDocuments(selectedId.value)
    else stopPolling()
  }, 3000)
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// Q3: 组件卸载时清理轮询定时器，避免内存泄漏与对已卸载组件的无效请求
onBeforeUnmount(stopPolling)

async function loadBases() {
  try {
    const res = await listKnowledgeBases(basePage.value, basePageSize.value)
    const data = res.data.data
    bases.value = data?.records || []
    baseTotal.value = data?.total || 0
    // 如果当前选中不存在，清空
    if (selectedId.value && !bases.value.find((b) => b.id === selectedId.value)) {
      selectedId.value = null
      documents.value = []
    }
  } catch {
    bases.value = []
  }
}

function handleBasePageChange(p: number) {
  basePage.value = p
  loadBases()
}

function handleDocPageChange(p: number) {
  docPage.value = p
  if (selectedId.value) loadDocuments(selectedId.value)
}

async function loadDocuments(kbId: string) {
  try {
    const res = await listDocuments(kbId, docPage.value, docPageSize.value)
    const data = res.data.data
    documents.value = data?.records || []
    docTotal.value = data?.total || 0
  } catch {
    documents.value = []
  }
}

async function handleSelect(kbId: string) {
  selectedId.value = kbId
  retrievalResults.value = []
  retrievalSearched.value = false
  retrievalQuery.value = ''
  docPage.value = 1
  await loadDocuments(kbId)
  startPolling()
}

async function handleCreate() {
  if (!createForm.value.name.trim()) return
  try {
    const res = await createKnowledgeBase({
      name: createForm.value.name.trim(),
      description: createForm.value.description.trim(),
      embeddingModelId: createForm.value.embeddingModelId || undefined,
      chunkStrategy: createForm.value.chunkStrategy,
      chunkSize: createForm.value.chunkSize,
      chunkOverlap: createForm.value.chunkOverlap,
    })
    const newBase = res.data.data
    message.success('创建成功')
    showCreateModal.value = false
    createForm.value = {
      name: '',
      description: '',
      embeddingModelId: '',
      chunkStrategy: 'by-length',
      chunkSize: 400,
      chunkOverlap: 60,
    }
    await loadBases()
    if (newBase) {
      selectedId.value = newBase.id
      await loadDocuments(newBase.id)
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '创建失败')
  }
}

function openEditBase() {
  if (!selectedBase.value) return
  editForm.value = {
    name: selectedBase.value.name,
    description: selectedBase.value.description || '',
  }
  showEditModal.value = true
}

async function handleEditBase() {
  if (!selectedBase.value || !editForm.value.name.trim()) return
  savingBase.value = true
  try {
    await updateKnowledgeBase(selectedBase.value.id, {
      name: editForm.value.name.trim(),
      description: editForm.value.description.trim(),
    })
    message.success('已更新知识库信息')
    showEditModal.value = false
    await loadBases()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    savingBase.value = false
  }
}

async function handleDeleteBase(kb: KnowledgeBase) {
  deleteTarget.value = kb
  deleteDependentAgents.value = []
  // 查询该知识库被哪些 Agent 使用，删除前给出明确提示
  try {
    const [bindRes, agentRes] = await Promise.all([
      listKnowledgeBindings(kb.id),
      listAgents(1, 100),
    ])
    const bindings = bindRes.data.data || []
    const agents = agentRes.data.data?.records || []
    const boundAgentIds = new Set(bindings.map((b) => b.agentId))
    deleteDependentAgents.value = agents.filter((a) => boundAgentIds.has(a.id))
  } catch {
    // 依赖查询失败不阻塞删除流程，按"无依赖"展示
    deleteDependentAgents.value = []
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteKnowledgeBase(deleteTarget.value.id)
    message.success(`知识库「${deleteTarget.value.name}」已删除`)
    if (selectedId.value === deleteTarget.value.id) {
      selectedId.value = null
      documents.value = []
    }
    deleteTarget.value = null
    await loadBases()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败，请稍后重试'
    message.error(msg)
  } finally {
    deleting.value = false
  }
}

function triggerFileInput() {
  if (uploading.value) return
  fileInputRef.value?.click()
}

async function handleDrop(e: DragEvent) {
  isDragging.value = false
  if (uploading.value) return
  const files = Array.from(e.dataTransfer?.files || [])
  await uploadFiles(files)
}

async function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  const files = Array.from(target.files || [])
  target.value = ''
  await uploadFiles(files)
}

async function uploadFiles(files: File[]) {
  if (!selectedId.value || files.length === 0) return
  if (uploading.value) {
    message.warning('正在上传中，请稍候')
    return
  }
  uploading.value = true
  let successCount = 0
  for (const file of files) {
    try {
      await uploadDocument(selectedId.value, file)
      successCount++
    } catch (e: unknown) {
      message.error(`上传 ${file.name} 失败: ${e instanceof Error ? e.message : '未知错误'}`)
    }
  }
  uploading.value = false
  if (successCount > 0) {
    message.success(`成功上传 ${successCount} 个文件`)
    await loadDocuments(selectedId.value)
    await loadBases()
    startPolling()
  }
}

async function handleRetryDoc(doc: Document) {
  try {
    await retryDocument(doc.id)
    message.success(`文档「${doc.name}」已开始重新处理，请稍候`)
    if (selectedId.value) {
      await loadDocuments(selectedId.value)
      await loadBases()
      startPolling()
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '重试失败')
  }
}

function handleDeleteDoc(doc: Document) {
  deleteDocTarget.value = doc
}

async function confirmDeleteDoc() {
  if (!deleteDocTarget.value) return
  deletingDoc.value = true
  try {
    await deleteDocument(deleteDocTarget.value.id)
    message.success(`文档「${deleteDocTarget.value.name}」已删除`)
    if (selectedId.value) {
      await loadDocuments(selectedId.value)
      await loadBases()
    }
    deleteDocTarget.value = null
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败，请稍后重试'
    message.error(msg)
  } finally {
    deletingDoc.value = false
  }
}

function statusBadge(status: string): string {
  const map: Record<string, string> = {
    ready: 'badge-success',
    processing: 'badge-info',
    pending: 'badge-neutral',
    error: 'badge-danger',
  }
  return map[status] || 'badge-neutral'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    ready: '已就绪',
    processing: '处理中',
    pending: '待处理',
    error: '失败',
  }
  return map[status] || status
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
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

/* Layout: 左侧列表 + 右侧详情 */
.kb-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
  min-height: calc(100vh - 280px);
}

/* Left panel: knowledge base list */
.kb-list-panel {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.kb-list-header {
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-count {
  background: var(--indigo-bg);
  color: var(--primary);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
}

.kb-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.kb-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: 2px;
  transition: var(--transition);
  position: relative;
}

.kb-list-item:hover {
  background: var(--surface-alt);
}

.kb-list-item.active {
  background: var(--indigo-bg);
  box-shadow: inset 0 0 0 1.5px var(--primary-border);
}

.kb-list-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--indigo-bg);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kb-list-item.active .kb-list-icon {
  background: var(--grad-primary-2);
  color: white;
  box-shadow: var(--glow-primary);
}

.kb-list-info {
  flex: 1;
  min-width: 0;
}

.kb-list-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.kb-list-meta {
  font-size: 11px;
  color: var(--text-muted);
}

.kb-list-delete {
  opacity: 0;
  width: 24px;
  height: 24px;
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

.kb-list-item:hover .kb-list-delete {
  opacity: 1;
}

.kb-list-delete:hover {
  background: var(--red-bg);
  color: var(--red);
}

.kb-list-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--text-muted);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

/* Right panel: documents */
.kb-detail-panel {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  padding: 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.kb-detail-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 80px 24px;
}

.kb-detail-empty-text {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-top: 16px;
}

.kb-detail-empty-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* Doc header */
.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 16px;
}

.doc-header-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--text);
  margin-bottom: 4px;
  letter-spacing: -0.3px;
}

.doc-header-desc {
  font-size: 13px;
  color: var(--text-muted);
}

.doc-header-edit {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 200ms ease;
}

.doc-header-edit:hover {
  color: var(--primary);
  border-color: var(--primary);
}

.doc-stats {
  display: flex;
  gap: 20px;
  flex-shrink: 0;
}

.doc-stat-item {
  text-align: center;
}

.doc-stat-value {
  font-size: 22px;
  font-weight: 800;
  background: var(--grad-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1.2;
}

.doc-stat-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 600;
  margin-top: 2px;
}

/* 知识库配置信息 */
.kb-config {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding: 12px 14px;
  background: var(--surface-alt);
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  margin-bottom: 16px;
}

.kb-config-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}

.kb-config-label {
  color: var(--text-muted);
  font-weight: 600;
}

.kb-config-value {
  color: var(--text);
  font-weight: 700;
}

/* Retrieval test */
.retrieval-test {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  margin-bottom: 16px;
  background: var(--surface-alt);
  overflow: hidden;
}

.retrieval-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: var(--transition);
}

.retrieval-header:hover {
  background: rgba(99, 102, 241, 0.04);
}

.retrieval-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.retrieval-body {
  padding: 0 14px 14px;
}

.retrieval-input-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.retrieval-input-row .form-input {
  flex: 1;
}

.retrieval-topk {
  flex: 0 0 100px !important;
}

.retrieval-results {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.retrieval-result-item {
  background: #ffffff;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  transition: var(--transition);
}

.retrieval-result-item:hover {
  border-color: var(--primary-border);
  box-shadow: var(--shadow-sm);
}

.retrieval-result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 11px;
}

.retrieval-result-rank {
  background: var(--grad-primary-2);
  color: white;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 11px;
  flex-shrink: 0;
}

.retrieval-result-doc {
  font-weight: 700;
  color: var(--text);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.retrieval-result-score {
  color: var(--primary);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.retrieval-result-content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  max-height: 120px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  white-space: pre-wrap;
  word-break: break-word;
}

.retrieval-empty {
  text-align: center;
  padding: 20px 12px;
  font-size: 12px;
  color: var(--text-muted);
}

/* Upload area */
.upload-area {
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  padding: 28px 24px;
  text-align: center;
  cursor: pointer;
  transition: var(--transition);
  background: var(--surface-alt);
  margin-bottom: 20px;
}

.upload-area:hover {
  border-color: var(--primary-border);
  background: var(--indigo-bg);
}

.upload-area.dragging {
  border-color: var(--primary);
  background: var(--indigo-bg);
  box-shadow: var(--glow-primary);
  transform: scale(1.01);
}

.upload-area.uploading {
  cursor: progress;
  border-color: var(--primary-border);
  background: var(--indigo-bg);
}

.upload-spinner {
  width: 28px;
  height: 28px;
  margin: 0 auto;
  border: 3px solid var(--primary-border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.upload-text {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 12px;
}

.upload-text strong {
  color: var(--primary);
}

.upload-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* Document list */
.doc-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}

.doc-count {
  background: var(--indigo-bg);
  color: var(--primary);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  margin-bottom: 6px;
  transition: var(--transition);
}

.doc-item:hover {
  border-color: var(--border);
  background: var(--surface-alt);
}

.doc-item-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--indigo-bg);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-item-info {
  flex: 1;
  min-width: 0;
}

.doc-item-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.doc-item-meta {
  font-size: 11px;
  color: var(--text-muted);
}

.doc-item-error {
  margin-top: 4px;
  font-size: 11px;
  color: var(--red);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-item-delete {
  opacity: 0;
  width: 26px;
  height: 26px;
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

.doc-item:hover .doc-item-delete {
  opacity: 1;
}

.doc-item-delete:hover {
  background: var(--red-bg);
  color: var(--red);
}

.doc-item-action {
  opacity: 0;
  width: 26px;
  height: 26px;
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

.doc-item:hover .doc-item-action {
  opacity: 1;
}

.doc-item-action:hover {
  background: var(--indigo-bg);
  color: var(--primary);
}

.doc-list-empty {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-muted);
  font-size: 13px;
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

/* Form */
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

.form-input,
.form-textarea {
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
  resize: vertical;
}

.form-input:focus,
.form-textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: var(--text-placeholder);
}

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-group-half {
  flex: 1;
}

.form-hint-warn {
  display: flex;
  align-items: flex-start;
  padding: 8px 10px;
  background: #f59e0b08;
  border: 1px solid #f59e0b30;
  border-radius: 6px;
  color: #b45309;
  font-weight: 500;
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

/* Confirm modal */
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

.confirm-dependents {
  margin: 10px 0 0;
  padding: 10px 12px;
  background: #f59e0b0d;
  border: 1px solid #f59e0b40;
  border-radius: var(--radius-sm);
  border-left: 2px solid #f59e0b;
}

.confirm-dependents-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 700;
  color: #b45309;
}

.confirm-dependents-names {
  margin-top: 5px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  line-height: 1.5;
  word-break: break-word;
}

.confirm-dependents-note {
  margin-top: 4px;
  font-size: 11.5px;
  color: #b45309;
  line-height: 1.5;
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
</style>
