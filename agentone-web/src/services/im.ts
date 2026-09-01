import api from './api'
import type { Result, ImBot } from '@/types'

export interface ImBotCreatePayload {
  name: string
  platform: 'dingtalk' | 'wecom'
  mode: 'webhook' | 'callback'
  agentId?: string
  config: Record<string, string>
}

export interface ImBotUpdatePayload {
  name?: string
  agentId?: string | null
  status?: 'active' | 'disabled'
  config?: Record<string, string>
}

/** IM 机器人列表 */
export function listImBots() {
  return api.get<Result<ImBot[]>>('/api/im/bots')
}

/** 创建 IM 机器人 */
export function createImBot(data: ImBotCreatePayload) {
  return api.post<Result<ImBot>>('/api/im/bots', data)
}

/** 更新 IM 机器人 */
export function updateImBot(id: string, data: ImBotUpdatePayload) {
  return api.put<Result<ImBot>>(`/api/im/bots/${id}`, data)
}

/** 删除 IM 机器人 */
export function deleteImBot(id: string) {
  return api.delete<Result<void>>(`/api/im/bots/${id}`)
}

/** 发送测试消息（仅钉钉 webhook 模式） */
export function sendImBotTest(id: string, data: { text: string; msgType?: string; title?: string }) {
  return api.post<Result<void>>(`/api/im/bots/${id}/send`, data)
}
