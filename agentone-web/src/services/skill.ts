import api from './api'
import type { Result, PageResult } from '@/types'

/** 工作空间内可用 Skill */
export interface SkillItem {
  id: string
  name: string
  type: string
  source: string
  description: string
  version: string
  status: string
}

/** Agent 的 Skill 绑定 */
export interface AgentSkillBinding {
  id: string
  agentId: string
  skillId: string
  skillName: string
  skillType: string
  skillVersion: string
  enabled: boolean
  createdAt: string
}

/** 列表：工作空间可用 Skill（分页） */
export function listSkills(page = 1, size = 20) {
  return api.get<Result<PageResult<SkillItem>>>(`/api/skills`, { params: { page, size } })
}

/** 列表：某 Agent 已绑定的 Skill */
export function listAgentSkillBindings(agentId: string) {
  return api.get<Result<AgentSkillBinding[]>>(`/api/skills/bindings/${agentId}`)
}

/** 绑定 Skill 到 Agent */
export function bindSkill(params: { agentId: string; skillId: string; skillVersion?: string }) {
  return api.post<Result<AgentSkillBinding>>('/api/skills/bind', params)
}

/** 解绑 */
export function unbindSkill(bindingId: string) {
  return api.delete<Result<void>>(`/api/skills/bindings/${bindingId}`)
}

/** 启用 / 禁用 */
export function toggleSkill(bindingId: string, enabled: boolean) {
  return api.put<Result<void>>(`/api/skills/bindings/${bindingId}/toggle?enabled=${enabled}`)
}
