import api from './api'
import type { Result, PageResult, Workspace, Member } from '@/types'

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

/** 成员列表（当前工作空间） */
export function listMembers(params?: { current?: number; size?: number }) {
  return api.get<Result<PageResult<Member>>>('/api/members', { params })
}

/** 添加成员（按已注册邮箱） */
export function addMember(data: { email: string; role: string }) {
  return api.post<Result<Member>>('/api/members', data)
}

/** 变更成员角色 */
export function updateMemberRole(userId: string, role: string) {
  return api.put<Result<void>>(`/api/members/${userId}`, { role })
}

/** 移除成员 */
export function removeMember(userId: string) {
  return api.delete<Result<void>>(`/api/members/${userId}`)
}
