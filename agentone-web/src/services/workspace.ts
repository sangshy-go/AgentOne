import api from './api'
import type { Result, Workspace } from '@/types'

/** 获取工作空间列表 */
export function listWorkspaces() {
  return api.get<Result<Workspace[]>>('/api/workspaces')
}

/** 创建工作空间 */
export function createWorkspace(data: { name: string; description?: string }) {
  return api.post<Result<Workspace>>('/api/workspaces', data)
}

/** 更新工作空间 */
export function updateWorkspace(id: string, data: { name: string; description?: string }) {
  return api.put<Result<Workspace>>(`/api/workspaces/${id}`, data)
}

/** 删除工作空间 */
export function deleteWorkspace(id: string) {
  return api.delete<Result<void>>(`/api/workspaces/${id}`)
}
