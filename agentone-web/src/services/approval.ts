import api from './api'
import type { Result, PageResult, PublishRequest, AuditLog } from '@/types'

/** 提交发布审批（课题⑩） */
export function submitPublishRequest(agentId: string) {
  return api.post<Result<PublishRequest>>('/api/publish-requests', { agentId })
}

/** 审批列表：?status=&agentId= 过滤；可见范围由后端按角色分流 */
export function listPublishRequests(params: {
  status?: string
  agentId?: string
  page?: number
  size?: number
}) {
  return api.get<Result<PageResult<PublishRequest>>>('/api/publish-requests', { params })
}

/** 审批通过（仅 admin/owner，且不可审自己的申请） */
export function approvePublishRequest(id: string) {
  return api.post<Result<PublishRequest>>(`/api/publish-requests/${id}/approve`)
}

/** 驳回（理由必填，8004） */
export function rejectPublishRequest(id: string, comment: string) {
  return api.post<Result<PublishRequest>>(`/api/publish-requests/${id}/reject`, { comment })
}

/** 撤回申请（仅提交人本人） */
export function withdrawPublishRequest(id: string) {
  return api.post<Result<PublishRequest>>(`/api/publish-requests/${id}/withdraw`)
}

/** 审计日志查询（仅 owner/admin/auditor 可见，后端 2003 兜底） */
export function fetchAuditLogs(params: {
  action?: string
  resourceType?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return api.get<Result<PageResult<AuditLog>>>('/api/audit-logs', { params })
}
