import api from './api'
import type { Result, PageResult, KnowledgeBase, Document, KnowledgeBinding, SearchResult } from '@/types'

/** 创建知识库 */
export function createKnowledgeBase(data: {
  name: string
  description?: string
  embeddingModel?: string
  embeddingModelId?: string
  chunkStrategy?: 'by-length' | 'by-title' | 'by-paragraph'
  chunkSize?: number
  chunkOverlap?: number
}) {
  return api.post<Result<KnowledgeBase>>('/api/knowledge/bases', data)
}

/** 知识库列表（分页） */
export function listKnowledgeBases(page = 1, size = 20) {
  return api.get<Result<PageResult<KnowledgeBase>>>('/api/knowledge/bases', {
    params: { page, size },
  })
}

/** 知识库详情 */
export function getKnowledgeBase(id: string) {
  return api.get<Result<KnowledgeBase>>(`/api/knowledge/bases/${id}`)
}

/** 更新知识库 */
export function updateKnowledgeBase(
  id: string,
  data: {
    name?: string
    description?: string
    embeddingModel?: string
    embeddingModelId?: string
  }
) {
  return api.put<Result<KnowledgeBase>>(`/api/knowledge/bases/${id}`, data)
}

/** 删除知识库 */
export function deleteKnowledgeBase(id: string) {
  return api.delete<Result<void>>(`/api/knowledge/bases/${id}`)
}

/** 上传文档（后端同步解析后异步处理，单独放宽超时以兼容大文件解析） */
export function uploadDocument(knowledgeId: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<Result<Document>>(`/api/knowledge/bases/${knowledgeId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

/** 文档列表（分页） */
export function listDocuments(knowledgeId: string, page = 1, size = 20) {
  return api.get<Result<PageResult<Document>>>(`/api/knowledge/bases/${knowledgeId}/documents`, {
    params: { page, size },
  })
}

/** 删除文档 */
export function deleteDocument(documentId: string) {
  return api.delete<Result<void>>(`/api/knowledge/documents/${documentId}`)
}

/** 重试失败的文档处理 */
export function retryDocument(documentId: string) {
  return api.post<Result<Document>>(`/api/knowledge/documents/${documentId}/retry`)
}

/** 更新绑定参数 */
export function updateBinding(
  bindingId: string,
  data: { topK?: number; similarityThreshold?: number }
) {
  return api.put<Result<KnowledgeBinding>>(`/api/knowledge/bindings/${bindingId}`, data)
}

/** 检索知识库 */
export function searchKnowledge(knowledgeId: string, query: string, topK = 5) {
  return api.post<Result<SearchResult[]>>(`/api/knowledge/bases/${knowledgeId}/search`, null, {
    params: { query, topK },
  })
}

/** 绑定知识库到 Agent */
export function bindKnowledge(data: {
  agentId: string
  knowledgeId: string
  topK?: number
  similarityThreshold?: number
}) {
  return api.post<Result<KnowledgeBinding>>('/api/knowledge/bindings', data)
}

/** 解绑知识库 */
export function unbindKnowledge(bindingId: string) {
  return api.delete<Result<void>>(`/api/knowledge/bindings/${bindingId}`)
}

/** Agent 绑定的知识库列表 */
export function listBindings(agentId: string) {
  return api.get<Result<KnowledgeBinding[]>>(`/api/knowledge/agents/${agentId}/bindings`)
}

/** 知识库被哪些 Agent 绑定（删除知识库前提示依赖方） */
export function listKnowledgeBindings(knowledgeId: string) {
  return api.get<Result<KnowledgeBinding[]>>(`/api/knowledge/bases/${knowledgeId}/bindings`)
}
