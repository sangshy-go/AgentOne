import api from './api'
import type { Result, PageResult } from '@/types'

/** 工种分类受控词表（Skill 中心 v2，前后端保持一致） */
export const SKILL_CATEGORIES = [
  '市场', '销售', '客服', '人事', '财务', '法务合规', '行政', '数据分析', 'IT集成', '其他',
] as const

/** 工作空间内可用 Skill */
export interface SkillItem {
  id: string
  workspaceId: string
  name: string
  type: string
  source: string
  category: string
  description: string
  inputSchema: string
  outputSchema: string
  config: string
  version: string
  status: string
  installedAt: string
  /** Skill 中心 v2：真实调用计数（skill_call_log） */
  callCount?: number | null
  /** Skill 中心 v2：动作型技能（广场执行需二次确认） */
  actionType?: boolean | null
  /** Skill 中心 v2：MCP 工具是否已发布到广场（非 MCP 技能为 null） */
  published?: boolean | null
}

/** 创建 / 更新用户 Skill 入参（type: api=HTTP 封装（缺省）/ prompt=内容型指令） */
export interface SkillForm {
  name: string
  type?: string
  category?: string
  description?: string
  inputSchema?: string
  outputSchema?: string
  config: string
  version?: string
}

/** Skill 导出定义（发布/分享的最小形态，JSON 文件流转） */
export interface SkillExportPayload {
  format: string
  formatVersion: number
  name: string
  type: string
  category: string
  description: string
  config: string
  inputSchema: string
  outputSchema: string
  version: string
  exportedAt: string
}

/** 列表查询条件（课题⑧：keyword 模糊匹配名称/描述，category/type/status 精确；
 * status 不传则包含已停用技能（管理视角），传 'active' 仅启用中（绑定选择器） */
export interface SkillListQuery {
  page?: number
  size?: number
  keyword?: string
  category?: string
  type?: string
  status?: string
}

/** Skill 执行结果（测试调用） */
export interface SkillRunResult {
  success: boolean
  data: Record<string, unknown> | null
  errorMessage: string | null
  durationMs: number | null
  tokenCount: number | null
}

/** 广场调用（试一试）结果。动作型技能两阶段：首次返回 confirmRequired + confirmToken + 草稿，不执行 */
export interface InvokeSkillResult {
  skillId: string
  skillName: string
  confirmRequired: boolean
  confirmToken: string | null
  draftParams: Record<string, unknown> | null
  success: boolean | null
  data: Record<string, unknown> | null
  errorMessage: string | null
  durationMs: number | null
  traceId: string | null
}

/** 技能包文件（详情抽屉文件树 / 导入结果展示） */
export interface SkillPackageFile {
  /** 包内相对路径，如 SKILL.md / scripts/scan.py */
  path: string
  /** script / resource / doc */
  kind: string
  size: number | null
  /** 文本内容；列表接口默认不带 */
  content?: string | null
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

/** 列表：工作空间可用 Skill（分页 + 搜索/筛选） */
export function listSkills(query: SkillListQuery = {}) {
  return api.get<Result<PageResult<SkillItem>>>(`/api/skills`, {
    params: { page: 1, size: 20, ...query },
  })
}

/** 技能广场（Skill 中心 v2）：本空间 active 用户 Skill + builtin + 已发布 MCP 工具 */
export function listPlaza(params: { q?: string; cat?: string } = {}) {
  return api.get<Result<SkillItem[]>>('/api/skills/plaza', { params })
}

/** 列表：某 Agent 已绑定的 Skill */
export function listAgentSkillBindings(agentId: string) {
  return api.get<Result<AgentSkillBinding[]>>(`/api/skills/bindings/${agentId}`)
}

/** 绑定 Skill 到 Agent（未发布的 MCP 工具会被后端拒绝，5016） */
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

/** 创建用户 Skill（api / prompt） */
export function createSkill(form: SkillForm) {
  return api.post<Result<SkillItem>>('/api/skills', form)
}

/** 更新用户 Skill */
export function updateSkill(skillId: string, form: SkillForm) {
  return api.put<Result<SkillItem>>(`/api/skills/${skillId}`, form)
}

/** 删除用户 Skill（存在 Agent 绑定时后端拒绝） */
export function deleteSkill(skillId: string) {
  return api.delete<Result<void>>(`/api/skills/${skillId}`)
}

/** 导出用户 Skill 为 JSON 定义 */
export function exportSkill(skillId: string) {
  return api.get<Result<SkillExportPayload>>(`/api/skills/${skillId}/export`)
}

/** 导入 Skill 定义（同名拒绝 5013） */
export function importSkill(payload: SkillExportPayload) {
  return api.post<Result<SkillItem>>('/api/skills/import', payload)
}

/**
 * 导入技能包（Skill 中心 v2）：整个文件夹（files + paths）或单个 .zip（file）。
 * multipart 上传，后端解析 SKILL.md frontmatter；同名拒绝 5013，包非法 5014。
 */
export function importSkillPackage(payload: { file?: File; files?: File[]; paths?: string[] }) {
  const fd = new FormData()
  if (payload.file) fd.append('file', payload.file)
  if (payload.files) for (const f of payload.files) fd.append('files', f)
  if (payload.paths) for (const p of payload.paths) fd.append('paths', p)
  return api.post<Result<SkillItem>>('/api/skills/import-package', fd)
}

/** 技能包文件树（详情抽屉展示；脚本仅存储不执行） */
export function listPackageFiles(skillId: string) {
  return api.get<Result<SkillPackageFile[]>>(`/api/skills/${skillId}/package-files`)
}

/**
 * 启用/停用技能（我的技能 toggle）：用户 Skill 切换 status；
 * MCP 工具切换发布状态；内置技能系统托管拒绝（5006）
 */
export function setSkillStatus(skillId: string, enabled: boolean) {
  return api.put<Result<SkillItem>>(`/api/skills/${skillId}/status`, null, {
    params: { enabled },
  })
}

/** 广场调用（试一试）：动作型技能两阶段确认，confirmToken 第二次调用才真实执行 */
export function invokeSkill(
  skillId: string,
  params?: Record<string, unknown>,
  confirmToken?: string,
) {
  return api.post<Result<InvokeSkillResult>>(`/api/skills/${skillId}/invoke`, {
    params: params ?? null,
    confirmToken: confirmToken ?? null,
  })
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
