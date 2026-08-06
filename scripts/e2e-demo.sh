#!/usr/bin/env bash
# ============================================================
# AgentOne 端到端演示 / 冒烟测试脚本
#
# 驱动真实 API 走完整闭环：
#   注册 → 工作空间 → 模型供应商（可选）→ 知识库上传/检索
#   → 创建 Agent → 流式对话（SSE）→ API Key → /v1 开放接口 → 健康检查
#
# 用法:
#   ./scripts/e2e-demo.sh                          # 基础流程（不需要 LLM Key）
#   API_KEY=sk-xxx ./scripts/e2e-demo.sh           # 完整流程（含对话）
#
# 环境变量:
#   BASE          服务地址，默认 http://localhost:8090
#   API_KEY       LLM API Key（OpenAI 兼容），不填则跳过所有对话步骤
#   BASE_URL      LLM Base URL，默认 https://dashscope.aliyuncs.com/compatible-mode/v1
#   CHAT_MODEL    Chat 模型名，默认 qwen-plus
#   EMBED_MODEL   Embedding 模型名，默认 text-embedding-v3
# ============================================================
set -uo pipefail

BASE="${BASE:-http://localhost:8090}"
API_KEY="${API_KEY:-}"
BASE_URL="${BASE_URL:-https://dashscope.aliyuncs.com/compatible-mode/v1}"
CHAT_MODEL="${CHAT_MODEL:-qwen-plus}"
EMBED_MODEL="${EMBED_MODEL:-text-embedding-v3}"

EMAIL="e2e-$(date +%s)@agentone.test"
PASSWORD="E2ePassw0rd!"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

PASS=0; SKIP=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ $1"; }
skip() { SKIP=$((SKIP+1)); echo "  ⏭️  [skip] $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $1"; }
step() { echo ""; echo "=== $1 ==="; }

command -v jq >/dev/null 2>&1 || { echo "需要 jq，请先安装: brew install jq"; exit 2; }

# 统一请求：断言 code==0，返回 data 对象
api() { # method path token json
  local method=$1 path=$2 token=${3:-} body=${4:-}
  local args=(-s -X "$method" -H "Content-Type: application/json")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}" "${BASE}${path}"
}

assert_ok() { # resp desc
  local code; code=$(echo "$1" | jq -r '.code // empty' 2>/dev/null)
  if [ "$code" = "0" ]; then ok "$2"; return 0; fi
  fail "$2（响应: $(echo "$1" | head -c 300)）"; return 1
}

# ------------------------------------------------------------
step "1/9 健康检查 GET /v1/health"
HEALTH=$(curl -s "${BASE}/v1/health")
# /v1/health 返回统一 Result 包装：{code,message,data:{status,database,redis}}
if echo "$HEALTH" | jq -e '(.data.status // .status) == "UP"' >/dev/null 2>&1; then
  ok "服务健康（$(echo "$HEALTH" | jq -c '.data // .')）"
else
  echo "❌ 服务不可用: $HEALTH"; echo "   请确认: docker compose up -d 且后端已启动（首启约 1-2 分钟）"
  exit 1
fi

# ------------------------------------------------------------
step "2/9 注册 + 登录"
RESP=$(api POST /api/auth/register "" "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"nickname\":\"E2E Bot\"}")
assert_ok "$RESP" "注册 ${EMAIL}" || exit 1
TOKEN=$(echo "$RESP" | jq -r '.data.token')

RESP=$(api POST /api/auth/login "" "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
assert_ok "$RESP" "登录" || exit 1
TOKEN=$(echo "$RESP" | jq -r '.data.token')
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] && ok "获取 JWT Token" || { fail "Token 为空"; exit 1; }

# ------------------------------------------------------------
step "3/9 创建工作空间 + 切换"
RESP=$(api POST /api/workspaces "$TOKEN" '{"name":"E2E 演示空间","description":"e2e-demo.sh 创建"}')
assert_ok "$RESP" "创建工作空间" || exit 1
WS_ID=$(echo "$RESP" | jq -r '.data.id')

RESP=$(api POST "/api/auth/switch-workspace/${WS_ID}" "$TOKEN")
assert_ok "$RESP" "切换工作空间" || exit 1
TOKEN=$(echo "$RESP" | jq -r '.data.token')   # 切换后 Token 携带新 workspaceId

# ------------------------------------------------------------
PROVIDER_ID=""; CHAT_MODEL_ID=""; EMBED_MODEL_ID=""
if [ -n "$API_KEY" ]; then
  step "4/9 模型供应商 + 模型（LLM 已配置）"
  RESP=$(api POST /api/model-providers "$TOKEN" \
    "{\"name\":\"E2E Provider\",\"provider\":\"openai\",\"apiKey\":\"${API_KEY}\",\"baseUrl\":\"${BASE_URL}\"}")
  assert_ok "$RESP" "创建模型供应商" || exit 1
  PROVIDER_ID=$(echo "$RESP" | jq -r '.data.id')

  RESP=$(api POST "/api/models/providers/${PROVIDER_ID}/models" "$TOKEN" \
    "{\"modelType\":\"chat\",\"modelId\":\"${CHAT_MODEL}\",\"displayName\":\"E2E Chat\"}")
  assert_ok "$RESP" "创建 Chat 模型 ${CHAT_MODEL}" || exit 1
  CHAT_MODEL_ID=$(echo "$RESP" | jq -r '.data.id')

  RESP=$(api POST "/api/models/providers/${PROVIDER_ID}/models" "$TOKEN" \
    "{\"modelType\":\"embedding\",\"modelId\":\"${EMBED_MODEL}\",\"displayName\":\"E2E Embedding\"}")
  assert_ok "$RESP" "创建 Embedding 模型 ${EMBED_MODEL}" || exit 1
  EMBED_MODEL_ID=$(echo "$RESP" | jq -r '.data.id')
else
  step "4/9 模型供应商（未提供 API_KEY）"
  skip "模型供应商/模型创建 —— 导出 API_KEY 后可跑完整流程"
fi

# ------------------------------------------------------------
step "5/9 知识库：创建 → 上传文档 → 轮询处理 → 检索"
KB_BODY='{"name":"E2E 知识库","description":"e2e-demo.sh 创建","chunkStrategy":"by-length","chunkSize":400,"chunkOverlap":60}'
[ -n "$EMBED_MODEL_ID" ] && KB_BODY=$(echo "$KB_BODY" | jq --arg m "$EMBED_MODEL_ID" '. + {embeddingModelId:$m}')
RESP=$(api POST /api/knowledge/bases "$TOKEN" "$KB_BODY")
assert_ok "$RESP" "创建知识库" || exit 1
KB_ID=$(echo "$RESP" | jq -r '.data.id')

if [ -n "$EMBED_MODEL_ID" ]; then
  cat > "${WORKDIR}/agentone-e2e.md" <<'EOF'
# AgentOne 简介

AgentOne（灵一）是一个开源的 AI Agent 中台，核心闭环是：配置模型、建立知识库、创建 Agent、开始对话、API 接入。

## 魔法口令

本演示文档的魔法口令是：菠萝芒果冰淇淋。当被问到魔法口令时，请原样回答这五个字以外的完整词组。
EOF

  RESP=$(curl -s -X POST -H "Authorization: Bearer ${TOKEN}" \
    -F "file=@${WORKDIR}/agentone-e2e.md" \
    "${BASE}/api/knowledge/bases/${KB_ID}/documents")
  assert_ok "$RESP" "上传文档 agentone-e2e.md" || exit 1
  DOC_ID=$(echo "$RESP" | jq -r '.data.id')

  # 轮询文档状态（解析 + 分块 + 向量化，异步）
  STATUS=""
  for i in $(seq 1 30); do
    RESP=$(api GET "/api/knowledge/bases/${KB_ID}/documents" "$TOKEN")
    STATUS=$(echo "$RESP" | jq -r --arg id "$DOC_ID" '.data[] | select(.id==$id) | .status')
    [ "$STATUS" = "ready" ] && break
    [ "$STATUS" = "error" ] && break
    sleep 2
  done
  if [ "$STATUS" = "error" ]; then
    fail "文档处理失败: $(echo "$RESP" | jq -r --arg id "$DOC_ID" '.data[] | select(.id==$id) | .errorMsg')"
  elif [ "$STATUS" = "ready" ]; then
    ok "文档处理完成（ready，分块 $(echo "$RESP" | jq -r --arg id "$DOC_ID" '.data[] | select(.id==$id) | .chunkCount') 个）"
  else
    fail "文档未在 60s 内 ready（最后状态: ${STATUS:-unknown}）"
  fi

  RESP=$(api POST "/api/knowledge/bases/${KB_ID}/search?query=%E9%AD%94%E6%B3%95%E5%8F%A3%E4%BB%A4&topK=3" "$TOKEN")
  HITS=$(echo "$RESP" | jq '.data | length' 2>/dev/null)
  if [ "${HITS:-0}" -ge 1 ]; then ok "知识库检索命中 ${HITS} 条"; else fail "知识库检索无命中"; fi
else
  skip "文档上传/向量化/检索（依赖 Embedding 模型，未提供 API_KEY）"
fi

# ------------------------------------------------------------
step "6/9 创建 Agent + 绑定知识库"
# modelConfig 在后端是 JSON 字符串字段（内含 chatModelId），这里直接构造原始 JSON 串
if [ -n "$CHAT_MODEL_ID" ]; then
  MODEL_CONFIG="{\"chatModelId\":\"${CHAT_MODEL_ID}\"}"
else
  MODEL_CONFIG=""
fi
AGENT_BODY=$(jq -nc --arg mc "$MODEL_CONFIG" \
  '{name:"E2E 助手",description:"e2e-demo.sh 创建",agentsMd:"# E2E 助手\n\n你是一个测试助手。回答问题时优先依据知识库内容。",modelConfig:(if $mc == "" then null else $mc end)}')
RESP=$(api POST /api/agents "$TOKEN" "$AGENT_BODY")
assert_ok "$RESP" "创建 Agent" || exit 1
AGENT_ID=$(echo "$RESP" | jq -r '.data.id')

RESP=$(api POST /api/knowledge/bindings "$TOKEN" \
  "{\"agentId\":\"${AGENT_ID}\",\"knowledgeId\":\"${KB_ID}\",\"topK\":5,\"similarityThreshold\":0.3}")
assert_ok "$RESP" "Agent 绑定知识库" || exit 1

RESP=$(api POST "/api/agents/${AGENT_ID}/publish" "$TOKEN")
assert_ok "$RESP" "发布 Agent" || exit 1

# ------------------------------------------------------------
step "7/9 流式对话 SSE POST /api/chat/stream"
if [ -n "$CHAT_MODEL_ID" ]; then
  SSE_OUT=$(curl -s -N -X POST \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    -d "{\"agentId\":\"${AGENT_ID}\",\"message\":\"文档里的魔法口令是什么？只回答口令本身。\"}" \
    --max-time 90 "${BASE}/api/chat/stream")
  echo "$SSE_OUT" | grep -q "event:session" && ok "收到 session 事件" || fail "未收到 session 事件"
  echo "$SSE_OUT" | grep -q "event:delta"   && ok "收到 delta 流式增量" || fail "未收到 delta 事件"
  echo "$SSE_OUT" | grep -q "event:done"    && ok "收到 done 结束事件" || fail "未收到 done 事件"
  REPLY=$(echo "$SSE_OUT" | grep "^data:" | sed 's/^data://' | tr -d '\n')
  echo "  💬 模型回复: $(echo "$REPLY" | head -c 200)"
  echo "$REPLY" | grep -q "菠萝芒果冰淇淋" && ok "RAG 生效：回复包含文档中的魔法口令" || fail "回复未命中知识库内容（RAG 可能未生效）"
else
  skip "流式对话（需要 API_KEY）"
fi

# ------------------------------------------------------------
step "8/9 API Key + 开放接口 /v1/chat"
RESP=$(api POST /api/api-keys "$TOKEN" "{\"env\":\"test\",\"allowedAgents\":[\"${AGENT_ID}\"],\"dailyLimit\":100}")
assert_ok "$RESP" "创建 API Key" || exit 1
SK=$(echo "$RESP" | jq -r '.data.apiKey')

if [ -n "$CHAT_MODEL_ID" ]; then
  V1_RESP=$(curl -s -X POST -H "X-API-Key: ${SK}" -H "Content-Type: application/json" \
    -d "{\"agentId\":\"${AGENT_ID}\",\"message\":\"一句话介绍 AgentOne\"}" \
    --max-time 90 "${BASE}/v1/chat")
  assert_ok "$V1_RESP" "X-API-Key 调用 /v1/chat" || true
  echo "  💬 /v1 回复: $(echo "$V1_RESP" | jq -r '.data.reply // empty' | head -c 200)"
else
  # 无 LLM 时仅验证鉴权链路：无效 Key 应 401，有效 Key 调 health 无关接口应通过鉴权
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "X-API-Key: invalid_key" \
    -H "Content-Type: application/json" -d "{\"agentId\":\"x\",\"message\":\"x\"}" "${BASE}/v1/chat")
  [ "$CODE" = "401" ] && ok "无效 Key 返回 401（鉴权链路正常）" || fail "无效 Key 预期 401，实际 ${CODE}"
  skip "/v1/chat 对话（需要 API_KEY）"
fi

# ------------------------------------------------------------
step "9/9 清理（删除 E2E 资源）"
api DELETE "/api/agents/${AGENT_ID}" "$TOKEN" >/dev/null && ok "删除 Agent（级联清理会话/绑定）"
api DELETE "/api/knowledge/bases/${KB_ID}" "$TOKEN" >/dev/null && ok "删除知识库"
api DELETE "/api/workspaces/${WS_ID}" "$TOKEN" >/dev/null || true

# ------------------------------------------------------------
echo ""
echo "========================================================"
echo "  E2E 结果: ✅ ${PASS} 通过  ⏭️ ${SKIP} 跳过  ❌ ${FAIL} 失败"
echo "========================================================"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
