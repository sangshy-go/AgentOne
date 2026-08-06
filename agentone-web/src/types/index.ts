/** 统一 API 响应体 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应包装 */
export interface PageResult<T = unknown> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 用户信息 */
export interface UserInfo {
  userId: string
  email: string
  nickname: string
  workspaceId: string
}

/** 工作空间 */
export interface Workspace {
  id: string
  name: string
  description: string
  role: string
  createdAt: string
}

/** Agent */
export interface Agent {
  id: string
  workspaceId: string
  name: string
  description: string
  category: string
  status: string
  agentsMd: string
  modelConfig: string
  modelProviderId?: string
  memoryConfig: string
  advancedConfig: string
  icon: string
  avatarUrl: string
  currentVersion: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** 知识库 */
export interface KnowledgeBase {
  id: string
  name: string
  description: string
  docCount: number
  chunkCount: number
  embeddingModel?: string
  embeddingModelId?: string
  chunkStrategy?: 'by-length' | 'by-title' | 'by-paragraph'
  chunkSize?: number
  chunkOverlap?: number
  createdAt: string
}

/** 检索结果 */
export interface SearchResult {
  chunkId: string
  documentId: string
  documentName: string
  content: string
  score: number
  chunkIndex: number
}

/** API Key */
export interface ApiKey {
  id: string
  keyPrefix: string
  env: string
  status: string
  allowedAgents: string[]
  dailyLimit: number
  createdAt: string
}

/** 会话 */
export interface ChatSession {
  id: string
  agentId: string
  title: string
  tokenCount: number
  messageCount: number
  createdAt: string
  updatedAt: string
}

/** 消息 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  tokenCount: number
  skillCalls: string | null
  durationMs: number | null
  traceId: string | null
  createdAt: string
}

/** 对话响应（同步） */
export interface ChatResponse {
  sessionId: string
  reply: string
  tokenCount: number
  durationMs: number
  traceId: string
}

/** 知识库文档 */
export interface Document {
  id: string
  knowledgeId: string
  name: string
  type: string
  size: number
  chunkCount: number
  status: 'pending' | 'processing' | 'ready' | 'error'
  errorMsg: string | null
  createdAt: string
}

/** Agent 知识库绑定 */
export interface KnowledgeBinding {
  id: string
  agentId: string
  knowledgeId: string
  knowledgeName: string
  topK: number
  similarityThreshold: number
  createdAt: string
}

/** 模型供应商（只存凭证） */
export interface ModelProvider {
  id: string
  workspaceId: string
  name: string
  provider: string
  apiKey: string
  baseUrl: string
  status: string
  createdAt: string
  updatedAt: string
}

/** 模型（属于某个供应商，区分 chat/embedding/rerank 等类型） */
export interface Model {
  id: string
  providerId: string
  providerName?: string
  modelType: 'chat' | 'embedding' | 'rerank' | 'image2text'
  modelId: string
  displayName: string
  contextSize: number
  maxTokens: number
  dimensions?: number
  status: string
  createdAt: string
  updatedAt: string
}

/** 模型检测结果 */
export interface ModelCheckResult {
  model: string
  available: boolean
  message: string
  latencyMs: number
}
