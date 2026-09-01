import api from './api'
import type { Result, PageResult } from '@/types'

/** MCP Server */
export interface McpServer {
  id: string
  workspaceId: string
  name: string
  description: string
  transport: string
  url: string
  command: string
  args: string[]
  headers: Record<string, string>
  timeoutMs: number
  status: string
  lastConnectedAt: string | null
  createdAt: string
  updatedAt: string
  /** 内存连接是否存活 */
  connected: boolean
  /** 已发现并注册的工具数 */
  toolCount: number
}

/** MCP Server 发现的工具（虚拟 Skill） */
export interface McpTool {
  /** 虚拟 Skill ID：mcp-{serverId}-{toolName} */
  skillId: string
  toolName: string
  description: string
  inputSchema: string
  /** Skill 中心 v2：是否已发布到广场（默认 false，IT 显式发布后才可见/可绑定） */
  published?: boolean | null
  /** Skill 中心 v2：动作型标记（MCP 工具默认有副作用，广场执行需确认） */
  actionType?: boolean | null
}

/** 创建 / 更新 MCP Server 入参 */
export interface McpServerForm {
  name: string
  description?: string
  transport: string
  url?: string
  command?: string
  args?: string[]
  headers?: Record<string, string>
  timeoutMs?: number
  status?: string
}

/** 列表（分页） */
export function listMcpServers(page = 1, size = 20) {
  return api.get<Result<PageResult<McpServer>>>('/api/mcp-servers', { params: { page, size } })
}

/** 详情 */
export function getMcpServer(serverId: string) {
  return api.get<Result<McpServer>>(`/api/mcp-servers/${serverId}`)
}

/** 创建 */
export function createMcpServer(form: McpServerForm) {
  return api.post<Result<McpServer>>('/api/mcp-servers', form)
}

/** 更新 */
export function updateMcpServer(serverId: string, form: McpServerForm) {
  return api.put<Result<McpServer>>(`/api/mcp-servers/${serverId}`, form)
}

/** 删除（仍有工具被 Agent 绑定时后端拒绝） */
export function deleteMcpServer(serverId: string) {
  return api.delete<Result<void>>(`/api/mcp-servers/${serverId}`)
}

/** 连接并发现工具（返回工具列表） */
export function connectMcpServer(serverId: string) {
  return api.post<Result<McpTool[]>>(`/api/mcp-servers/${serverId}/connect`)
}

/** 断开连接（保留配置与工具注册信息） */
export function disconnectMcpServer(serverId: string) {
  return api.post<Result<void>>(`/api/mcp-servers/${serverId}/disconnect`)
}

/** 查看已发现的工具 */
export function listMcpTools(serverId: string) {
  return api.get<Result<McpTool[]>>(`/api/mcp-servers/${serverId}/tools`)
}

/** 工具级「发布到广场」开关（Skill 中心 v2 治理，默认关闭） */
export function publishMcpTool(serverId: string, toolName: string, published: boolean) {
  return api.put<Result<McpTool>>(
    `/api/mcp-servers/${serverId}/tools/${encodeURIComponent(toolName)}/publish`,
    { published },
  )
}
