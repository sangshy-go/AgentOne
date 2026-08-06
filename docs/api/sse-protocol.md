# SSE 流式对话协议

> 适用于 `POST /api/chat/stream`（JWT 认证）与 `POST /v1/chat/stream`（X-API-Key 认证）。
> 代码来源：`agentone-agent/.../service/impl/ChatServiceImpl.java#chatStream`

## 传输方式

| 项 | 说明 |
|----|------|
| 方法 | `POST`（需要请求体，因此**不能用浏览器原生 `EventSource`**——它只支持 GET） |
| Content-Type（请求） | `application/json` |
| Content-Type（响应） | `text/event-stream` |
| 认证 | `/api/*`：`Authorization: Bearer <JWT>`；`/v1/*`：`X-API-Key: <key>` |
| 超时 | Nginx 已配置 `proxy_read_timeout 3600s`；客户端建议 ≥ 90s |

### 请求体

```json
{
  "agentId": "必填，Agent ID",
  "message": "必填，用户输入",
  "sessionId": "可选，为空自动创建新会话",
  "stream": true
}
```

## 事件目录

所有事件的 `data` 均为**裸字符串**（非 JSON），按 SSE 标准以 `\n\n` 分隔。

| 事件 | 数量 | data 内容 | 说明 |
|------|------|-----------|------|
| `session` | 1（首个） | sessionId | 新建会话时前端据此记录会话 ID |
| `thinking` | 0..N | 推理片段 | 模型思考过程增量（需模型支持 reasoning，如 Qwen3 + `enable_thinking`） |
| `delta` | 0..N | 文本片段 | 回复正文增量，客户端追加拼接 |
| `done` | 1（正常结束） | `[DONE]` | 流正常结束标志 |
| `error` | 0..1 | 错误消息 | 流中途失败时发出（替代 `done`） |

**顺序保证**：`session` → （`thinking` / `delta` 交错）→ `done` 或 `error`。
`thinking` 与 `delta` 可能交错出现（模型先思考后输出，也可能边思考边输出）。

## 完整示例 transcript

```
event:session
data:0195a3f2c8d17b2e9f4a6c8e0d2b4f6a

event:thinking
data:用户问的是文档里的魔法口令，我需要

event:thinking
data:检索知识库……找到了相关片段。

event:delta
data:魔法口令是

event:delta
data:：菠萝芒果冰淇淋。

event:done
data:[DONE]
```

失败时：

```
event:session
data:0195a3f2c8d17b2e9f4a6c8e0d2b4f6a

event:error
data:Chat 模型关联的服务商配置不存在，请检查「模型管理」中的服务商设置
```

## curl 示例

```bash
# 控制台接口（JWT）
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agentId":"<agent-id>","message":"你好"}'

# 开放接口（API Key）
curl -N -X POST http://localhost:8080/v1/chat/stream \
  -H "X-API-Key: $AGENTONE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"agentId":"<agent-id>","message":"你好"}'
```

## 客户端解析参考

前端解析器位于 `agentone-web/src/services/chat.ts`（`chatStream` + `parseSseEvent`）：

- 使用 `fetch()` + `ReadableStream` 手动解析（`EventSource` 不支持 POST body）
- 按 `\n\n` 切分事件块，读取 `event:` 与 `data:` 字段
- 事件路由：`session` → 记录会话 ID；`thinking` → 展示思考过程；`error` → 错误提示；`done` → 结束；其余（含未命名事件）→ 作为正文增量追加

## 实现要点（服务端）

1. **上下文捕获**：SSE 的 `doOnComplete` 在请求线程外触发（ThreadLocal 已清空），`chatStream` 入口即捕获 `userId/workspaceId`，保存消息时临时恢复，保证 MyBatis 租户字段自动填充。
2. **部分保存**：流中途失败时，已接收的部分回复会连同错误信息一起保存到消息表，避免内容丢失。
3. **RAG 先行**：用户消息在构建上下文前即入库，RAG 检索以当前消息为 query。
4. **静默事件**：AgentScope 的 `ModelCallStartEvent`、`BlockStartEvent` 等内部事件不透出，仅转换 `TextBlockDeltaEvent`（→ delta）与 `ThinkingBlockDeltaEvent`（→ thinking）。
