#!/usr/bin/env python3
"""
最小 stdio MCP Server（AgentOne 课题④离线验证用）。

实现 MCP 协议 stdio 传输的核心子集：换行分隔 JSON-RPC 2.0，
支持 initialize / notifications/initialized / ping / tools/list / tools/call。
提供两个 mock 工具：echo（回显文本）与 add（两数求和）。

用法（在 AgentOne MCP 管理页登记）：
  transport = stdio
  command   = python3
  args      = ["/绝对路径/scripts/mock-mcp-stdio-server.py"]

无任何第三方依赖；stdout 仅输出协议消息，诊断信息走 stderr。
"""
import json
import sys

DEFAULT_PROTOCOL_VERSION = "2024-11-05"

TOOLS = [
    {
        "name": "echo",
        "description": "原样回显输入的文本（AgentOne 离线验证用 mock MCP 工具）",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "要回显的文本"}
            },
            "required": ["text"],
        },
    },
    {
        "name": "add",
        "description": "返回两个整数之和（AgentOne 离线验证用 mock MCP 工具）",
        "inputSchema": {
            "type": "object",
            "properties": {
                "a": {"type": "integer", "description": "加数 a"},
                "b": {"type": "integer", "description": "加数 b"},
            },
            "required": ["a", "b"],
        },
    },
]


def respond(msg):
    sys.stdout.write(json.dumps(msg, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except json.JSONDecodeError:
            print("bad json line ignored", file=sys.stderr)
            continue

        method = req.get("method")
        rid = req.get("id")
        params = req.get("params") or {}

        if method == "initialize":
            # 回显客户端声明的协议版本，避免版本协商失败
            respond({
                "jsonrpc": "2.0",
                "id": rid,
                "result": {
                    "protocolVersion": params.get("protocolVersion", DEFAULT_PROTOCOL_VERSION),
                    "capabilities": {"tools": {"listChanged": False}},
                    "serverInfo": {"name": "agentone-mock-mcp", "version": "1.0.0"},
                },
            })
        elif method == "notifications/initialized":
            continue  # 通知无需响应
        elif method == "ping":
            respond({"jsonrpc": "2.0", "id": rid, "result": {}})
        elif method == "tools/list":
            respond({"jsonrpc": "2.0", "id": rid, "result": {"tools": TOOLS}})
        elif method == "tools/call":
            name = params.get("name")
            args = params.get("arguments") or {}
            if name == "echo":
                text = "echo: " + str(args.get("text", ""))
                respond({"jsonrpc": "2.0", "id": rid, "result": {
                    "content": [{"type": "text", "text": text}],
                    "isError": False,
                }})
            elif name == "add":
                try:
                    total = int(args.get("a", 0)) + int(args.get("b", 0))
                    respond({"jsonrpc": "2.0", "id": rid, "result": {
                        "content": [{"type": "text", "text": str(total)}],
                        "isError": False,
                    }})
                except (TypeError, ValueError):
                    respond({"jsonrpc": "2.0", "id": rid, "result": {
                        "content": [{"type": "text", "text": "a / b 必须是整数"}],
                        "isError": True,
                    }})
            else:
                respond({"jsonrpc": "2.0", "id": rid, "result": {
                    "content": [{"type": "text", "text": "unknown tool: " + str(name)}],
                    "isError": True,
                }})
        elif rid is not None:
            respond({"jsonrpc": "2.0", "id": rid, "error": {
                "code": -32601,
                "message": "Method not found: " + str(method),
            }})


if __name__ == "__main__":
    main()
