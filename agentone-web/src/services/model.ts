import api from './api'
import type { Result, PageResult, ModelProvider, ModelCheckResult, Model } from '@/types'

/** 创建模型供应商 */
export function createModelProvider(data: {
  name: string
  provider: string
  apiKey: string
  baseUrl: string
}) {
  return api.post<Result<ModelProvider>>('/api/model-providers', data)
}

/** 模型供应商列表（分页） */
export function listModelProviders(page = 1, size = 20) {
  return api.get<Result<PageResult<ModelProvider>>>(`/api/model-providers`, { params: { page, size } })
}

/** 模型供应商详情 */
export function getModelProvider(id: string) {
  return api.get<Result<ModelProvider>>(`/api/model-providers/${id}`)
}

/** 更新模型供应商 */
export function updateModelProvider(
  id: string,
  data: {
    name?: string
    provider?: string
    apiKey?: string
    baseUrl?: string
  }
) {
  return api.put<Result<ModelProvider>>(`/api/model-providers/${id}`, data)
}

/** 删除模型供应商 */
export function deleteModelProvider(id: string) {
  return api.delete<Result<void>>(`/api/model-providers/${id}`)
}

/** 检测模型供应商连通性 */
export function checkModelProvider(id: string) {
  return api.post<Result<ModelCheckResult>>(`/api/model-providers/${id}/check`)
}

// ==================== 模型管理（Provider → Model 两层结构） ====================

/** 创建模型（在指定供应商下） */
export function createModel(providerId: string, data: {
  modelType: string
  modelId: string
  displayName?: string
  contextSize?: number
  maxTokens?: number
}) {
  return api.post<Result<Model>>(`/api/models/providers/${providerId}/models`, data)
}

/** 获取供应商下的模型列表（分页） */
export function listModels(providerId: string, page = 1, size = 20) {
  return api.get<Result<PageResult<Model>>>(`/api/models/providers/${providerId}/models`, { params: { page, size } })
}

/** 按类型获取模型列表（用于知识库选择 embedding/chat 模型） */
export function listModelsByType(modelType: string) {
  return api.get<Result<Model[]>>(`/api/models/by-type/${modelType}`)
}

/** 更新模型 */
export function updateModel(modelId: string, data: {
  modelType?: string
  modelId?: string
  displayName?: string
  contextSize?: number
  maxTokens?: number
}) {
  return api.put<Result<Model>>(`/api/models/${modelId}`, data)
}

/** 删除模型 */
export function deleteModel(modelId: string) {
  return api.delete<Result<void>>(`/api/models/${modelId}`)
}
