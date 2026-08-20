# Skill 平台竞品调研报告（课题⑧立项依据）

> **日期**: 2026-08-10
> **触发**: 用户 2026-08-10 对 Skill 中心不满——"没有搜索、没有归类、创建调试和查询在一个页面、创建模式一般"，要求调研阿里云百炼、WorkBuddy 等主流平台，核心标准：**对技术人员和非技术人员都非常友好**；随后定调**面向企业**（不是 Dify 式技术平台）。
> **产出**: 本报告 → 课题⑧ 企业化升级（已交付，见 improvement-details.md §1.6）。

---

## 1. 调研方法与可信度分级

| 平台 | 信息来源 | 验证方式 | 可信度 |
|------|---------|---------|--------|
| 阿里云百炼 | help.aliyun.com 官方文档 | 子代理逐页查证 | ✅ 高（官方文档原文） |
| Coze（扣子） | docs.coze.cn 官方文档 | 子代理逐页查证 | ✅ 高（官方文档原文；商店 UI 分类标签需登录，以文档为准） |
| Dify | GitHub langgenius/dify 源码 + 官方文档 | 子代理读源码查证 | ✅ 高（源码级，含组件/枚举名） |
| Tencent WorkBuddy / SkillHub | workbuddy.ai / cloud.tencent.com / skillhub.cn | 子代理抓取官网 | 🟡 中（官网宣传口径，未见后台细节） |
| OpenAI（GPTs / GPT Store） | Claude 既有知识 | 本次未在线复核 | ⚠️ 低（知识基线，未当场验证） |
| Anthropic（Claude Skills） | Claude 既有知识 | 本次未在线复核 | ⚠️ 低（知识基线；课题⑦已按其 SKILL.md 形态实现过） |

> 诚实说明：百炼/Coze/Dify/WorkBuddy 四家为当场查证；OpenAI/Anthropic 两家仅作方向性参照，具体字段与流程未复核，引用其结论时按"行业共识"而非"精确事实"对待。

---

## 2. 六平台对比总览

| 平台 | 信息组织 | 创建模式 | 页面结构 | 发布/共享 |
|------|---------|---------|---------|----------|
| **阿里云百炼** | 官方/三方/自定义三层；卡片带状态标签 | 纯技术路径：逐 API 配 URL/方法/参数 + 鉴权；云市场导入兜底；**无非技术路径** | 单页承载列表/创建/编辑/调试/绑定；调试为行内弹窗（"测试工具"），**调试成功才能发布** | 业务空间级；无跨账号市场；仅草稿/已发布两态 |
| **Coze（扣子）** | 技能商店：技能类型+行业分类、关键词搜索、技能包 | **双轨**：对话式（"把以上流程制作为技能"，零代码）+ AI 编程（自然语言生成文件包） | 商店浏览/我的技能/创建/调试分区；调试对话化 | 公开商店（扣子审核）+ 企业市场（管理员控审核）；版本自动升级 |
| **Dify** | 4 类 Tab（内置/自定义/工作流/MCP）+ 17 个二级标签；marketplace 有搜索/分类/标签/排序/精选位 | 自定义工具 = OpenAPI schema 导入（粘贴/URL/示例模板）+ 鉴权三档；Provider 卡片制；Marketplace 一键安装 | 浏览、创建、编排分开；**每个 API 有独立 test run 调试** | Plugin 体系（manifest + .difybndl），GitHub PR 上架 marketplace |
| **OpenAI** | GPT Store 15+ 分类 + trending + 编辑精选 | Builder 对话式创建（非技术）+ Actions 粘 OpenAPI（技术）；左右分栏实时预览 | 创建/预览/发布分离 | GPT Store 公开分发 |
| **Anthropic** | Claude Code skills 走 plugin marketplace | SKILL.md 内容型（指令 + 脚本 + 资源） | 文件/git/marketplace 分发 | marketplace 安装 |
| **WorkBuddy/SkillHub** | Top 50 精选榜单，安全审核 + 多维度评估 | Skill 与连接器沉淀至项目空间，一次配置多人复用 | 技能市场（skillhub.cn）独立站点 | 100+ 领域专家、7万+ Skills 随取随用 |

---

## 3. 重点平台细节

### 3.1 阿里云百炼（企业云厂商参照）

**信息组织**：官方插件（6 个：代码解释器/计算器/图片生成/夸克搜索/二维码/GitHub 搜索，表格呈现）/ 三方插件（商业服务、图像视频、学习教育等领域）/ 自定义插件。卡片带状态标签（如"已开通"）。

**创建流程（两条技术路径）**：
- 手工创建：填插件名称/描述/URL + 可选鉴权（Header|Query × basic|bearer|appcode），再逐 API 配置（名称/描述/路径/方法/提交方式）+ 出入参（**传参方式二选一：大模型识别 or 业务透传**）+ 调用示例。保存草稿 → 测试 → 发布；
- 云市场导入：开通现成 API → 授权 → 一键导入 → 自动填充出入参。
- **文档未提及任何模板/AI 生成/自然语言建插件的非技术路径**。

**可借鉴**：
1. 三层分类（官方/三方/自定义）+ 状态标签，对"平台内置/生态/自建"区分直观；
2. "工具描述帮助大模型判断是否调用"的引导文案 + 调用示例降低漏召回；
3. **传参方式显式开关**（LLM 推理 vs 业务透传）是 Agent 平台必备语义；
4. **调试是发布前置条件**，坏插件不流入应用；
5. 插件 → MCP 服务一键转换，资产复用。

### 3.2 Coze（扣子，双受众标杆）

**信息组织**：技能商店含单个技能/技能包（官方严选行业组合）/数据集；按技能类型、行业领域分类 + 关键词搜索；卡片带图标/简介/分类/付费标识，详情页有 3 个真实使用案例。

**创建流程（双轨，明确区分受众）**：
- **对话式（非技术）**：对话中完成任务 → 输入"帮我将以上处理流程制作为技能" → AI 总结工作流、沉淀 SKILL.md → 添加到 Agent。"先执行再打包"，零代码；
- **AI 编程（技术）**：code.coze.cn 用自然语言描述需求 → 编程 Agent 生成技能文件包 + 脚本 → 预览测试 → 打包发布。**旧版插件 IDE/OpenAPI 导入已重构为技能体系**。

**发布/共享（双商店分层）**：公开商店（扣子团队审核图标/名称/描述/功能合规，需上架资质）+ 企业市场（管理员可配是否审核，默认免审）；版本更新后已安装用户自动升级；支持按次/订阅/免费 + 开源 toggle。

**可借鉴（按优先级）**：
1. **双路径创建**——非技术 5 分钟出技能，技术有完整编辑能力，对双受众至关重要；
2. **技能 = SKILL.md + 脚本 + 资源**，隐性知识显性化，比 API 包装更贴企业场景；审核四维度"高复用/可交付/有标准/可迭代"；
3. **双商店**：公开变现生态 + 企业内部工具分发，审核策略可配；
4. 案例驱动审核（上架必须 3 个真实案例）；
5. 技能包 + 数据集降低选择成本。

### 3.3 Dify（技术平台参照，源码级查证）

**信息组织**：4 个一级 Tab（Built-in/Custom-API/Workflow/MCP，源码 `CollectionType`）+ 内置工具 17 个二级标签（`ToolLabelEnum`：search/image/finance/productivity/rag/other 等）；Marketplace 支持 query/category/tags/sort 多维检索。

**创建流程**：自定义工具 = OpenAPI Schema 导入（JSON/YAML 粘贴 + URL 导入 + Weather/Petstore/空白示例模板），Schema 类型支持 OPENAPI/SWAGGER/OPENAI_PLUGIN/OPENAI_ACTIONS，鉴权三档（none/api_key_header/api_key_query）；导入后自动解析生成参数表单。Provider 概念：凭证在 Provider 级统一管理（workspace 级共享 + 单工具级两层）。

**页面结构**：工具总览页（左 Tab + 右卡片网格）/ Provider 详情页（左工具列表右详情）/ MCP 独立管理页 / **单工具 test run 调试**（每个 API 填参数测试，返回结果/错误）。

**可借鉴**：
1. **Provider 卡片式组织**（以"服务提供者"为粒度而非单个 API），符合企业"系统对接"心智；
2. **授权与工具分离**，凭证一次配置全局生效；
3. Marketplace 三件套：编辑推荐 + 分类发现 + 信任标记（verified）；
4. **示例模板兜底**（Weather/Petstore）显著降低 OpenAPI 上手门槛。

### 3.4 Tencent WorkBuddy / SkillHub

- **产品确认**：WorkBuddy 是腾讯出品的 AI Agent 办公平台（workbuddy.ai / cloud.tencent.com/product/workbuddy），全场景 AI 办公工作台，自然语言下达任务 → 自主拆解执行 → 交付报告/PPT/表格等可验收结果；
- **SkillHub**（skillhub.cn）：其技能市场，**精选 Top 50 高质量 AI Skills**，经安全审核与多维度评估；宣称 100+ 领域专家、7万+ Skills 随取随用；Skill 与连接器沉淀至项目空间，一次配置多人复用；
- **同名辨析**：workbuddy.com 是澳洲现场服务管理软件，非 AI 平台，勿混淆；
- **启发**：企业级技能分发走"**精选 + 审核 + 复用**"路线，而非开放堆积。

### 3.5 OpenAI / Anthropic（知识基线参照，未当场复核）

- **OpenAI**：GPT Store 15+ 分类 + trending + 编辑精选；创建为 Builder 对话式（非技术）+ Actions 粘 OpenAPI（技术），左右分栏实时预览；发布前要求至少一次成功测试调用；
- **Anthropic**：Claude Code skills 走 plugin marketplace，SKILL.md 内容型（指令 + 脚本 + 资源），文件/git/marketplace 分发。**课题⑦的 type=prompt 内容型 Skill 即照此形态实现**。

---

## 4. 行业共识：四个痛点的成熟答案

用户提出的四个不满意点，行业答案高度一致：

| 痛点 | 行业共识 | 参照 |
|------|---------|------|
| **没有搜索** | 关键词搜索是标配，无一不做 | Coze 商店搜索 / Dify marketplace query / GPT Store |
| **没有归类** | 分类/标签体系是标配；企业向按**业务/行业**而非技术形态分 | Coze 行业领域 / Dify 17 标签 / GPT Store 15+ 分类 |
| **创建调试查询挤一页** | 浏览/创建/调试普遍分区 | Dify（总览页/详情/test run 分离）/ Coze（商店/我的/编程页） |
| **创建模式一般** | **创建全是双轨制**：非技术走模板/对话式，技术走 OpenAPI/结构化表单 | Coze 双轨 / GPTs Builder+Actions / Dify 模板+OpenAPI |

**趋势判断**：Coze 已把"插件"重构为"技能（SKILL.md + 脚本 + 资源）"体系——**内容型是行业共识**（课题⑦方向正确）；技术平台（Dify/百炼）与企业平台的分水岭在于**组织维度**（技术形态 vs 业务场景）与**创建门槛**（手写 JSON vs 模板/对话式）。

---

## 5. AgentOne 课题⑧ 设计决策（调研 → 落地映射）

**核心判断**：AgentOne 面向银行/保险/国企，用户 = 业务专家（沉淀 SOP/规范）+ IT 集成（接存量系统）+ 管理员（管控）。与 Dify 式技术平台的根本差异——**按业务场景组织，而非按技术形态组织**。业务专家不关心"API 封装"，只认"信贷审批规范""客诉处理流程"。

| 决策 | 内容 | 调研依据 |
|------|------|---------|
| ① 业务分类 | 受控词表（风控合规/财务/人力资源/客服运营/办公效率/IT集成/其他），非自由标签（企业要可管控）；builtin/MCP 同步归类 | Coze 行业分类 + GPT Store 分类；受控词表 = 企业治理要求 |
| ② 关键词搜索 | name/description 大小写不敏感，DB 与虚拟挂载同口径 | 四家平台标配 |
| ③ 双 Tab 分区 | 技能浏览（搜索+分类+卡片）/ 技能调试（三步向导内联化） | Dify 浏览/调试分离；百炼调试行内化 |
| ④ 内容型模板库 | 6 模板（空白/报告规范/SOP/合规清单/客服话术/会议纪要）预填分类与正文骨架 | Coze 对话式创建的"低门槛"目标，用模板库静态实现（无 LLM Key 依赖） |
| ⑤ API 结构化表单 | 裸 JSON → url/method/timeout/headers KV/参数可视表格 ↔ 高级 JSON 双向切换（A3-1） | Dify OpenAPI 自动解析表单 + 示例模板；百炼结构化配置 |

**明确不做（调研支持缓置）**：
- 技能市场/商店（AE-E2）——Coze/WorkBuddy 证明市场依赖内容供给与审核体系，三周内无供给来源；课题⑦的导出/导入已提供"种子技能包"线下流转；
- AI 辅助创建（AE-E4）——依赖真实 LLM Key，Phase 3 评估；
- 审批流——私有化场景工作空间即隔离边界，加审批是过度设计；
- OpenAPI 导入（AE-E1）——保持原排期（课题⑥后），不塞本期。

---

## 6. 来源清单

**当场查证（子代理执行）**：
- 百炼：help.aliyun.com/zh/model-studio/plug-in-overview、/plugins、/custom-plug-ins
- Coze：docs.coze.cn/cozespace_skills_store、/cozespace_create_skill、/cozespace_using_skills、/cozespace_publish_skill
- Dify：github.com/langgenius/dify —— web/app/components/tools/types.ts、edit-custom-collection-modal/（test-api.tsx/get-schema.tsx）、api/core/tools/entities/tool_entities.py、values.py、web/app/components/plugins/types.ts
- WorkBuddy：cloud.tencent.com/product/workbuddy、skillhub.cn

**知识基线（未当场复核）**：OpenAI GPTs/GPT Store、Anthropic Claude Agent Skills。
