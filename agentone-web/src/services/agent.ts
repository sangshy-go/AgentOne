import api from './api'
import type { Result, PageResult, Agent } from '@/types'

/** Agent 列表（分页） */
export function listAgents(page = 1, size = 20) {
  return api.get<Result<PageResult<Agent>>>('/api/agents', { params: { page, size } })
}

/** Agent 详情 */
export function getAgent(id: string) {
  return api.get<Result<Agent>>(`/api/agents/${id}`)
}

/** 创建 Agent */
export function createAgent(data: Partial<Agent>) {
  return api.post<Result<Agent>>('/api/agents', data)
}

/** 更新 Agent */
export function updateAgent(id: string, data: Partial<Agent>) {
  return api.put<Result<Agent>>(`/api/agents/${id}`, data)
}

/** 删除 Agent */
export function deleteAgent(id: string) {
  return api.delete<Result<void>>(`/api/agents/${id}`)
}

/** 发布 Agent */
export function publishAgent(id: string) {
  return api.post<Result<void>>(`/api/agents/${id}/publish`)
}
