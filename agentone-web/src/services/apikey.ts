import api from './api'
import type { Result, PageResult, ApiKey } from '@/types'

/** 创建 API Key 的返回（仅创建时返回完整明文） */
export interface CreateApiKeyResult {
  id: string
  apiKey: string
  keyPrefix: string
  env: string
  status: string
}

/** 列表（分页） */
export function listApiKeys(page = 1, size = 20) {
  return api.get<Result<PageResult<ApiKey>>>(`/api/api-keys`, { params: { page, size } })
}

/** 创建 */
export function createApiKey(params: { env: string; dailyLimit?: number }) {
  return api.post<Result<CreateApiKeyResult>>('/api/api-keys', params)
}

/** 停用 */
export function disableApiKey(id: string) {
  return api.delete<Result<void>>(`/api/api-keys/${id}`)
}
