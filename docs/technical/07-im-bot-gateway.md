# IM Bot 网关技术方案

> Phase 2 · 课题⑤（IM Bot 网关）
> 状态：已交付（后端全链路 + 41 单测 + 模拟平台回调 E2E 验证 + 前端管理页）
> 前置阅读：[02-agent-engine.md](02-agent-engine.md)（ChatService 对话主链路）、[05-auth-rbac.md](05-auth-rbac.md)（租户拦截器 / RuntimeContext）

## 1. 概述

IM Bot 网关把 AgentOne 的 Agent 接入企业 IM 平台（钉钉 / 企业微信），支持两种形态：

| 形态 | 方向 | 平台载体 | 说明 |
|------|------|---------|------|
| 通知（webhook） | 仅发送 | 钉钉自定义机器人 | 控制台主动推送 text/markdown 到群 |
| 对话（callback） | 收发 | 企微自建应用 / 钉钉企业机器人 | 平台回调 → 转发绑定 Agent → 回复 |

当前产品口径：**钉钉**（通知 + @对话）开放；**企业微信**后端能力完整但未真实联调，前端入口标「待建设」；**飞书**未排期。详见 §9。

核心能力：

| 能力 | 说明 |
|------|------|
| 机器人管理 | 每工作空间 CRUD：platform（dingtalk/wecom）× mode（webhook/callback）组合校验 |
| 凭证加密存储 | config 整体 AES-256-GCM 加密落库，接口只回掩码，绝不回传原始凭证 |
| 平台协议实现 | 企微 WXBizMsgCrypt（AES-256-CBC + SHA1 签名）；钉钉加签（HmacSHA256） |
| 对话转发 | 回调注入虚拟用户上下文 `im:{platform}:{senderId}`，复用 ChatService 同步对话 |
| 多轮记忆 | `im_sender_session` 映射发送者 → 会话，同发送者连续对话保持上下文 |
| 租户隔离 | 管理端走租户拦截器；回调端点无用户上下文，按"平台签名即鉴权"模型单独处理 |

与其他模块的关系：本模块是 Agent 的**第五个接入渠道**（继前端对话、SSE 流式、API Key、之后），对话能力完全复用 `ChatService.chat()`，不新增任何对话逻辑。

## 2. 架构设计

### 2.1 组件与职责

```
管理端（走 JWT + 租户拦截器）
ImBotController（REST，/api/im/bots）
   ▼
ImBotServiceImpl（CRUD / send / handleIncoming）
   ├─→ ImConfigCrypto          config 整体 AES-256-GCM 加解密（密钥 = 环境变量）
   ├─→ ImBotMapper             im_bot 表（管理端查询自动带 workspace_id）
   ├─→ ImSenderSessionMapper   im_sender_session（无 workspace 列，经父表间接隔离）
   └─→ ChatService             同步对话（复用 Agent 引擎）

平台回调（公开端点，不走 JWT，平台签名鉴权）
ImCallbackController（/api/im/callback/{platform}/{botId}）
   ├─ 企微：GET URL 验证（解密 echostr 回显）+ POST 消息（解密→转发→加密被动回复）
   │    └─ WecomCrypto（官方协议独立实现：AES-256-CBC / SHA1 / PKCS#7-32）
   └─ 钉钉：POST JSON + timestamp/sign 头验签
        └─ DingTalkSender.sign（与自定义机器人加签同算法）

出站发送
DingTalkSender（webhook + 加签，HTTP/1.1，errcode 映射 5203）
```

**管理与回调双入口**是关键结构决策：同一个 `ImBotService.handleIncoming` 被两个平台控制器复用，平台差异全部收敛在协议层（加解密/验签/报文解析），业务层只见统一的 `ImIncoming(senderId, senderNick, text, conversationType)`。

### 2.2 回调对话流程（以企微为例）

```
企微平台推送加密 XML
  ▼
JwtAuthFilter 白名单放行 /api/im/callback/**
  ▼
ImCallbackController.wecomMessage
  ├─ wecomCryptoOf(botId)：bot 不存在/停用/非 callback/配置不可解 → 静默返回
  ├─ 提取 <Encrypt> → verifySignature（SHA1 排序拼接）→ decrypt（AES-CBC）
  ├─ MsgType != text → 静默返回
  ▼
ImBotServiceImpl.handleIncoming(botId, incoming)
  ├─ selectById（@InterceptorIgnore，回调无用户上下文）
  ├─ RuntimeContext.set(im:{platform}:{senderId}, bot.workspaceId)  ← 必须先于后续查询
  ├─ agentId 为空 → 提示绑定；Agent 跨空间/未发布 → 对应文案
  ├─ 查 im_sender_session 映射 → ChatService.chat(sessionId 可空)
  ├─ 首次对话落映射（后续同发送者复用会话）
  └─ finally RuntimeContext.clear()
  ▼
回复文本 → 加密信封（encrypt + SHA1 签名）→ 被动回复
```

### 2.3 一次出站发送（钉钉 webhook）

```
POST /api/im/bots/{id}/send
  ├─ loadOwnedBot（带 workspace 条件，防越权）
  ├─ status != active → 5202
  ├─ decryptConfig → webhookUrl + secret
  └─ DingTalkSender.send
       ├─ buildUrl：追加 timestamp + sign（HmacSHA256(ts+"\n"+secret, secret) → base64 → urlencode）
       ├─ POST JSON（text / markdown）
       └─ errcode != 0 → 5203（透传平台 errmsg，便于排障）
```

## 3. 核心实现

### 3.1 ImConfigCrypto（凭证加密存储）

- 算法：**AES-256-GCM**（12 字节随机 IV 前置 + 128 位认证标签），密文 = `base64(IV || ciphertext || tag)`。GCM 自带完整性校验，篡改密文解密直接失败（5201），无需额外 MAC。
- 密钥：环境变量 `AGENTONE_IM_SECRET_KEY`（64 位 hex = 32 字节，`openssl rand -hex 32` 生成）。**未配置时创建/更新机器人直接失败（5200），不做明文降级**——凭证是第三方密钥，明文落库等于泄露发送能力。
- 每次加密随机 IV：同一 config 两次加密密文不同，防模式分析。

### 3.2 WecomCrypto（企微官方协议独立实现）

按企微《自建应用回调加解密方案》实现，未引第三方 SDK（协议简单且引 SDK 反而带来依赖负担）：

- 密钥派生：`AESKey = base64decode(encodingAesKey + "=")`（43 位 Base64 → 32 字节），非法/长度不对 → 5204，**创建机器人时即校验**（构造即校验），避免回调时才发现配置错误。
- 加密布局：`random(16) + msgLen(4, 大端) + msg + receiveId`，PKCS#7 填充到 32 字节块，AES-256-CBC（IV = key 前 16 字节）。
- 签名：`SHA1(sort(token, timestamp, nonce, encrypt) 拼接)`。
- 解密校验：解出的 receiveId 必须等于配置 corpId，不匹配 → 5205（防伪造/错配）。
- 被动回复：`buildReplyEnvelope` 生成带签名的加密 XML 信封。

### 3.3 钉钉加签与验签（同一算法双向复用）

`DingTalkSender.sign(ts, secret)` 为静态方法，同时服务：
1. 出站：自定义机器人 webhook 追加 `timestamp + sign` 参数；
2. 入站：企业机器人回调头验签（允许 1 小时时钟偏移，与钉钉官方防重放窗口一致）。

验签兼容 URL 编码差异：平台可能传编码后的 sign，`expected.equals(sign) || expected.equals(URLDecoder.decode(sign))` 双路比对。注意 Java `URLEncoder` 会把 `/` 编码为 `%2F`，与钉钉平台行为一致。

### 3.4 回调容错策略（防平台重试风暴）

平台对非 200 / 异常响应会重试，故所有异常路径**静默返回成功语义**：

| 场景 | 行为 |
|------|------|
| bot 不存在 / 停用 / 非 callback 模式 / 平台不匹配 | 静默（企微回 "success"，钉钉回 `{}`） |
| body 缺失 / 空（`@RequestBody(required = false)` + guard） | 静默 |
| 企微无 `<Encrypt>` 节点 / 非文本消息 | 静默 |
| 钉钉验签失败 | warn 日志 + 静默 `{}` |
| 配置解密失败 | warn 日志 + 静默 |
| 对话内部异常 | 返回用户可读文案（"处理失败：…"），不抛 500 |

验签/解密失败与正常流量无法在 HTTP 层区分时，宁可静默也不报错——报错会触发平台无限重试。

### 3.5 租户隔离的回调特例

回调路径没有 JWT、没有用户上下文，与租户拦截器（自动追加 `workspace_id = ?`）冲突，按最小豁免处理：

1. `ImBotMapper.selectById` 标注 `@InterceptorIgnore(tenantLine = "true")`：**botId 即访问凭证**，能构造合法平台签名才知道 botId，鉴权由平台签名机制保证。管理端 CRUD 一律走带 `workspace_id` 等值条件的 wrapper 查询（`loadOwnedBot`），不经过该方法。
2. `im_sender_session` 加入 `WorkspaceInterceptor.IGNORE_TABLES`：该表无 `workspace_id` 列，经父实体 `im_bot` 间接隔离（先查 bot 拿 workspace 再操作映射，删除级联清理）。
3. `handleIncoming` 解密 bot 后**第一件事**是 `RuntimeContext.set(Context.of("im:{platform}:{senderId}", bot.workspaceId))`——必须先于 `agentMapper.selectById` 等后续查询（拦截器依赖上下文），且 `agent.workspaceId == bot.workspaceId` 显式等值校验作为纵深防御。
4. `finally` 中 `RuntimeContext.clear()`，防止线程复用串上下文。

### 3.6 配置校验与 SSRF 防护（validateAndNormalize）

| 组合 | 校验 |
|------|------|
| dingtalk + webhook | `webhookUrl` 必填且**必须**以 `https://oapi.dingtalk.com/` 开头（服务端将向该 URL 发起请求，强制官方域名防 SSRF）；`secret` 可选 |
| dingtalk + callback | `appSecret` 必填（回调验签） |
| wecom + callback | 五件套必填（corpId/agentId/secret/token/encodingAesKey）+ 构造 WecomCrypto 即时校验 key 合法性 |
| wecom + webhook | 直接拒绝 5202（企微没有 webhook 机器人形态） |

绑定 Agent 时校验其存在且属于当前工作空间（5202）。

## 4. 数据库设计

V23 迁移（`V23__create_im_bot.sql`）：

```sql
CREATE TABLE im_bot (
    id               VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id     VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name             VARCHAR(100) NOT NULL,
    platform         VARCHAR(20)  NOT NULL,  -- dingtalk / wecom
    mode             VARCHAR(20)  NOT NULL,  -- webhook（仅发送）/ callback（收发）
    agent_id         VARCHAR(36),            -- 可空 = 纯通知机器人
    config_encrypted TEXT         NOT NULL,  -- AES-256-GCM(base64)
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_by       VARCHAR(100),
    created_at / updated_at TIMESTAMP,
    CHECK platform IN ('dingtalk','wecom'),
    CHECK mode IN ('webhook','callback'),
    CHECK status IN ('active','disabled')
);
CREATE INDEX idx_im_bot_workspace ON im_bot(workspace_id);

CREATE TABLE im_sender_session (
    bot_id     VARCHAR(36)  NOT NULL REFERENCES im_bot(id) ON DELETE CASCADE,
    sender_id  VARCHAR(200) NOT NULL,
    session_id VARCHAR(36)  NOT NULL,
    PRIMARY KEY (bot_id, sender_id)
);
```

要点：`config_encrypted` 存 JSON 序列化后的整体密文；`im_sender_session` 随 bot 删除级联清理，保证"删除机器人即清理会话映射"语义。

## 5. API 设计

管理端（`/api/im`，详见 rest-api.md §16）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/im/bots` | 列表（config 只回掩码 `configMasked`） |
| POST | `/api/im/bots` | 创建（组合校验 + 加密落库） |
| PUT | `/api/im/bots/{id}` | 更新名称/状态/绑定/凭证 |
| DELETE | `/api/im/bots/{id}` | 删除（级联清理映射） |
| POST | `/api/im/bots/{id}/send` | 出站发送（仅钉钉 webhook 支持，其余 5206） |

公开回调端点（JwtAuthFilter 白名单 `/api/im/callback/`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/im/callback/wecom/{botId}` | URL 验证（解密 echostr 明文回显） |
| POST | `/api/im/callback/wecom/{botId}` | 消息回调（被动回复加密信封） |
| POST | `/api/im/callback/dingtalk/{botId}` | 消息回调（验签 + 响应体回复） |

错误码：

| 码 | 含义 |
|----|------|
| 5200 | 未配置 AGENTONE_IM_SECRET_KEY |
| 5201 | 配置密文损坏 / 密钥不一致 |
| 5202 | 参数/组合/归属校验失败 |
| 5203 | 钉钉发送失败（透传平台 errcode/errmsg） |
| 5204 | 企微 encodingAesKey 非法 |
| 5205 | 企微签名/解密失败 |
| 5206 | 该平台/模式暂不支持主动发送 |

## 6. 关键技术决策

| 决策 | 选择 | 理由 | 备选方案 |
|------|------|------|---------|
| 凭证存储 | AES-256-GCM 整体加密 + 掩码回传 | 凭证=发送能力，明文落库即泄露；掩码满足前端展示 | 字段级加密（复杂度高）/ 明文（不可接受） |
| 加密密钥 | 环境变量，未配置 fail-fast 5200 | 密钥不落库不落代码；不做明文降级 | 数据库存密钥（等于没加密） |
| 企微协议 | 手写 WXBizMsgCrypt | 协议公开且固定，~150 行可控；避免引入厂商 SDK 依赖 | 企微官方 SDK（依赖旧、与 Spring Boot 3 兼容风险） |
| 回调鉴权 | 平台签名 + botId 作凭证，selectById 豁免租户拦截 | 回调无用户上下文；签名合法性 ⇒ botId 可信 | 回调也发 JWT（平台不支持自定义头） |
| 对话集成 | 复用 ChatService 同步对话 | IM 被动回复是请求-响应模型，天然适配同步接口 | 独立对话通路（重复建设） |
| 发送渠道 | 仅钉钉 webhook，企微/企业应用发送报 5206 | 企微主动发送需 access_token 协议（获取/刷新/发送三接口），明确报错不静默 | 实现企微 access_token（超出本期范围） |
| 钉钉出站 | JDK HttpClient + HTTP/1.1 | 与 McpConnectionManager 一致，避免 HTTP/2 ALPN 兼容问题 | OkHttp（新增依赖） |

## 7. 已知限制

1. **企微被动回复 5 秒窗口**：IM 回复走同步对话，长耗时模型（含 RAG/工具调用）可能超时，企微会提示"应用暂时无法响应"。解法（主动推送 + 异步）依赖 access_token 协议，留待后续版本。
2. **企微 / 钉钉企业应用主动发送未实现**（5206）：需要应用凭证 access_token 协议。
3. **仅文本消息**：图片/富文本/事件类消息静默忽略。
4. **钉钉 webhook 无法接收消息**：平台形态决定，仅通知用途。
5. **企微未真实环境联调**：协议级模拟验证已通过，但按产品口径（真实联调通过才算交付）前端入口与首页宣传降级为「待建设」，后端代码完整保留（见 §9）。

## 8. 测试要点

41 个单测全绿（`agentone-im` 模块）：

- **ImConfigCryptoTest（8）**：加解密往返、随机 IV 密文不同、篡改/错钥/垃圾输入 → 5201、无密钥 → 5200、密钥长度校验。
- **DingTalkSenderTest（5）**：签名已知向量锁定（`sign(1700000000000, "SEC123456")` = `FDly9FmQpdYyYkNLryV5%2F4kGkNb4cCTG2VhnJLEn0mA%3D`）、buildUrl 各分支。
- **WecomCryptoTest（8）**：固定 43 位 key 夹具、签名向量、加解密往返、receiveId 不匹配 → 5205、篡改 → 5205、非法 key → 5204。
- **ImBotServiceImplTest（20）**：SSRF 拦截、非法组合、缺字段、跨空间绑定、加密落库 + 掩码格式（`access_token=***`、`SEC1****`）、发送门禁（5202/5206）、handleIncoming 各门禁、映射落库/复用、上下文注入断言（`im:wecom:s1`）、业务异常文案透出、2000 字符截断、RuntimeContext 清理。

E2E 验证（独立 Python 脚本模拟平台回调，不依赖真实平台）：企微 URL 验证回显 → 企微消息回调拿到**真实 LLM 回复**的加密信封并成功解密 → 钉钉验签通过/拒绝 → 模式门禁 → 多轮会话复用（DB 映射行落地）→ 出站发送到达真实钉钉 API（假 token 被 300005 拒绝并映射 5203）→ 停用机器人静默。

## 9. 前端管理页与产品曝光

### 9.1 管理页（/im-bots，`views/im/ImBotPage.vue`）

- **卡片列表**：平台色图标 + 平台/模式/状态徽章 + 绑定 Agent + 掩码凭证行；callback 模式额外展示**回调地址行 + 一键复制**（基址取 `window.location.origin`，开发环境经 Vite 同源代理直达后端，生产同源部署同样成立）。
- **创建弹窗平台区**：钉钉可选；**企业微信 / 飞书为「待建设」占位卡**（虚线灰底、橙色徽标、不可选）。模式区按平台联动（企微仅 callback，入口降级后实际只走钉钉）。
- **callback 创建引导**：选「对话（callback）」后表单顶部展示四步引导——① open.dingtalk.com 建企业内部应用并开启机器人能力；② 复制 AppSecret 填入创建并绑定已发布 Agent；③ 创建后复制卡片回调地址回填「消息接收地址」；④ 发版加群 @机器人（回调需公网可达）。
- **凭证编辑交互**：编辑时凭证输入框默认为空 = 保持不变；轮换需填全整套，保存后整体加密覆盖。

### 9.2 产品曝光与降级口径

- **Landing 页**：bento 网格「钉钉原生接入」宽卡（群聊演示气泡 + providers：钉钉·通知推送 / 钉钉·@对话）+ 竞品对比表「企业 IM 接入」行。企微不出现——曝光以真实联调通过为准。
- **降级不等于删除**：企微/飞书降级仅发生在前端入口与宣传文案，后端协议实现、单测、API 完整保留；恢复启用 = 恢复平台选项 + 首页文案，无后端工作量。
