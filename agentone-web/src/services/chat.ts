import api from './api'
import type { Result, PageResult, ChatSession, ChatMessage, ChatResponse } from '@/types'

/** 同步对话 */
export function chat(agentId: string, message: string, sessionId?: string) {
  return api.post<Result<ChatResponse>>('/api/chat', { agentId, message, sessionId, stream: false })
}

/**
 * 流式对话（SSE）
 *
 * 后端接口：POST /api/chat/stream，返回 text/event-stream
 * 每个 SSE event 的 data 字段是文本增量（delta）
 *
 * @param onDelta    收到文本增量时回调
 * @param onThinking 收到思考过程增量时回调（reasoning_content）
 * @param onDone     流结束时回调（携带完整 sessionId）
 * @param onError    流异常时回调
 */
export function chatStream(
  agentId: string,
  message: string,
  sessionId: string | undefined,
  onDelta: (chunk: string) => void,
  onThinking: (chunk: string) => void,
  onDone: (sessionId: string) => void,
  onError: (err: string) => void
): AbortController {
  const controller = new AbortController()

  // 用 fetch 实现 SSE（axios 不支持 stream 读取）
  // S7: 认证由 HttpOnly Cookie 承载，使用 credentials: 'include' 自动携带
  fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ agentId, message, sessionId, stream: true }),
    signal: controller.signal,
    credentials: 'include',
  })
    .then(async (response) => {
      if (!response.ok) {
        const text = await response.text()
        onError(`HTTP ${response.status}: ${text}`)
        return
      }
      const reader = response.body?.getReader()
      if (!reader) {
        onError('无响应流')
        return
      }
      const decoder = new TextDecoder()
      let buffer = ''
      let lastSessionId = sessionId || ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        // C1: 归一化 \r\n，避免跨平台换行导致事件解析错位
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

        // 按 SSE 协议解析：以 "\n\n" 分隔事件
        const parts = buffer.split('\n\n')
        buffer = parts.pop() || ''
        for (const part of parts) {
          const { data, event } = parseSseEvent(part)
          if (event === 'session') {
            // 后端推送 sessionId 的第一个事件。可能为 JSON（含 sessionId/id 字段），
            // 也可能为纯文本；统一抽取出真正的 sessionId，避免 loadMessages 因误用整段 JSON 而失败。
            lastSessionId = extractSessionId(data) || lastSessionId
            continue
          }
          if (event === 'thinking') {
            if (data) onThinking(data)
            continue
          }
          if (event === 'error') {
            // C1: 异常事件也要释放底层连接
            try { await reader.cancel() } catch { /* ignore */ }
            onError(data || '未知错误')
            return
          }
          if (event === 'done' || data === '[DONE]') {
            onDone(lastSessionId)
            return
          }
          if (data) onDelta(data)
        }
      }
      // C1: 流结束前 flush 残留 buffer（末尾事件未以 \n\n 结尾时仍要发出）
      if (buffer.trim()) {
        const { data, event } = parseSseEvent(buffer)
        if (event === 'done' || data === '[DONE]') {
          onDone(lastSessionId)
          return
        }
        if (event === 'error') {
          onError(data || '未知错误')
          return
        }
        if (data) onDelta(data)
      }
      // 流自然结束
      onDone(lastSessionId)
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err.message)
    })

  return controller
}

/** 解析单个 SSE 事件 */
function parseSseEvent(raw: string): { event: string; data: string } {
  let event = 'message'
  let data = ''
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += (data ? '\n' : '') + line.slice(5).trim()
  }
  return { event, data }
}

/**
 * 从 session 事件的 data 中解析出 sessionId。
 * - 若为 JSON（如 {"sessionId":"...", ...} 或 {"id":"..."}），取对应字段；
 * - 否则视为纯文本 sessionId 原样返回。
 */
function extractSessionId(data: string): string {
  if (!data) return ''
  const trimmed = data.trim()
  try {
    const obj = JSON.parse(trimmed)
    if (obj && typeof obj === 'object') {
      const id = (obj as Record<string, unknown>).sessionId ?? (obj as Record<string, unknown>).id
      return typeof id === 'string' && id ? id : ''
    }
  } catch {
    // 非 JSON：按纯文本处理
  }
  return trimmed
}

/** 会话列表（分页） */
export function listSessions(agentId: string, page = 1, size = 20) {
  return api.get<Result<PageResult<ChatSession>>>(`/api/chat/sessions`, { params: { agentId, page, size } })
}

/** 会话详情 */
export function getSession(sessionId: string) {
  return api.get<Result<ChatSession>>(`/api/chat/sessions/${sessionId}`)
}

/** 会话消息列表 */
export function getSessionMessages(sessionId: string) {
  return api.get<Result<ChatMessage[]>>(`/api/chat/sessions/${sessionId}/messages`)
}

/** 重命名会话 */
export function renameSession(sessionId: string, title: string) {
  return api.put<Result<void>>(`/api/chat/sessions/${sessionId}/rename`, { title })
}

/** 删除会话 */
export function deleteSession(sessionId: string) {
  return api.delete<Result<void>>(`/api/chat/sessions/${sessionId}`)
}
