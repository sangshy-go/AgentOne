import api from './api'
import type {
  Result,
  PageResult,
  DashboardStats,
  MonitorSession,
  SessionTimeline,
  SkillCallRecord,
} from '@/types'

/** 仪表盘统计 */
export function getDashboardStats() {
  return api.get<Result<DashboardStats>>('/api/dashboard/stats')
}

/** 监控-会话列表（全工作空间） */
export function getMonitorSessions(params: {
  agentId?: string
  keyword?: string
  recentHours?: number
  current?: number
  size?: number
}) {
  return api.get<Result<PageResult<MonitorSession>>>('/api/monitor/sessions', { params })
}

/** 监控-会话时间线（消息 + Skill 调用归并） */
export function getSessionTimeline(sessionId: string) {
  return api.get<Result<SessionTimeline>>(`/api/monitor/sessions/${sessionId}/timeline`)
}

/** 监控-Skill 调用记录 */
export function getSkillCalls(params: {
  agentId?: string
  skillId?: string
  status?: string
  recentHours?: number
  current?: number
  size?: number
}) {
  return api.get<Result<PageResult<SkillCallRecord>>>('/api/monitor/skill-calls', { params })
}
