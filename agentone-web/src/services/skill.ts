import api from './api'
import type { Result, PageResult } from '@/types'

/** 工作空间内可用 Skill */
export interface SkillItem {
  id: string
  workspaceId: string
  name: string
  type: string
  source: string
  description: string
  inputSchema: string
  outputSchema: string
  config: string
  version: string
  status: string
  installedAt: string
}

/** 创建 / 更新 API 模式 Skill 入参 */
export interface SkillForm {
  name: string
  description?: string
  inputSchema?: string
  outputSchema?: string
  config: string
  version?: string
}

/** Skill 执行结果（测试调用） */
export interface SkillRunResult {
  success: boolean
  data: Record<string, unknown> | null
  errorMessage: string | null
  durationMs: number | null
  tokenCount: number | null
}

/** 调试向导第 1 步：可调试目标 */
export interface DebugTarget {
  id: string
  name: string
  type: string
  description: string
  inputSchema: string
}

/** 调试向导第 2 步：参数预检结果 */
export interface DebugPreviewResult {
  valid: boolean
  errors: string[]
  plan: string
}

/** 调试向导第 3 步：执行结果 */
export interface DebugRunResult {
  success: boolean
  data: Record<string, unknown> | null
  errorMessage: string | null
  durationMs: number | null
  traceId: string
  sessionId: string
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

/** 创建 API 模式 Skill */
export function createSkill(form: SkillForm) {
  return api.post<Result<SkillItem>>('/api/skills', form)
}

/** 更新 API 模式 Skill */
export function updateSkill(skillId: string, form: SkillForm) {
  return api.put<Result<SkillItem>>(`/api/skills/${skillId}`, form)
}

/** 删除 API 模式 Skill（存在 Agent 绑定时后端拒绝） */
export function deleteSkill(skillId: string) {
  return api.delete<Result<void>>(`/api/skills/${skillId}`)
}

/** 测试调用 Skill（真实执行） */
export function testSkill(skillId: string, params: Record<string, unknown>) {
  return api.post<Result<SkillRunResult>>(`/api/skills/${skillId}/test`, { params })
}

/** 调试第 1 步：列出当前工作空间可调试的 Skill */
export function listDebugTargets() {
  return api.get<Result<DebugTarget[]>>('/api/skills/debug/targets')
}

/** 调试第 2 步：参数预检 + 执行计划预览（不真实调用） */
export function debugPreview(skillId: string, params: Record<string, unknown>) {
  return api.post<Result<DebugPreviewResult>>('/api/skills/debug/preview', { skillId, params })
}

/** 调试第 3 步：真实执行（上下文取当前登录态，写调试审计） */
export function debugRun(skillId: string, params: Record<string, unknown>, sessionId?: string) {
  return api.post<Result<DebugRunResult>>('/api/skills/debug/run', { skillId, params, sessionId })
}
