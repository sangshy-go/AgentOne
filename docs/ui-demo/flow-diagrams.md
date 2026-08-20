# Skill 中心 v2 — 交互流程图

> 目标：重定位「面向中小企业所有工种」的技能中心。核心转变——从「技能资产管理」视角
> （列表/创建/调试并列）转为「能力发现 + 能力管理」双视角：
> - **技能广场**（发现）：按工种导航、搜索、精选、卡片、详情抽屉、试一试、启用到 Agent
> - **我的技能**（管理）：已安装技能的启停、使用统计、编辑、导出
> - **调试降级**：三步向导从顶级 Tab 降为详情抽屉内的行内动作
> - **创建多入口**：模板库 / 空白 / 内容导入（SKILL.md）/ 脚本包（Scripts）/ API 接入

---

## 1. 用户主流程（发现并使用一个技能）

业务用户（HR / 销售 / 客服等非技术角色）带着业务问题来，3 步内用上技能。

```mermaid
flowchart TD
    A([进入技能中心]) --> B[技能广场 · 默认视图]
    B --> C{如何找到技能?}
    C -->|按工种| D[点工种导航 chip<br/>市场/销售/客服/人事/财务...]
    C -->|按关键词| E[搜索框输入<br/>名称/描述模糊匹配]
    C -->|逛精选| F[官方精选区卡片]
    D --> G[技能卡片网格]
    E --> G
    F --> G
    G --> H{点卡片}
    H --> I[技能详情抽屉<br/>它能做什么 + 使用示例]
    I --> J{下一步?}
    J -->|先试| K[试一试面板<br/>填参数→看结果]
    J -->|直接用| L[启用到 Agent]
    K --> K1{满意?}
    K1 -->|是| L
    K1 -->|否| I
    L --> M[选目标 Agent<br/>客服助手/销售管家...]
    M --> N([Toast: 已启用<br/>去对话页即可使用])

    style B fill:#EEF2FF,stroke:#6366F1
    style I fill:#EEF2FF,stroke:#6366F1
    style N fill:#ECFDF5,stroke:#10B981
```

---

## 2. 创建技能流程（多入口）

```mermaid
flowchart TD
    A([点 创建技能]) --> B[Step 1 选择创建方式]
    B --> C{五种入口}

    C -->|业务用户推荐| D[模板库<br/>按工种分组的预填模板]
    C -->|沉淀 SOP| E[空白创建<br/>名称/描述/工种/正文]
    C -->|已有文档| F[内容导入<br/>上传/粘贴 Markdown 或 SKILL.md]
    C -->|高级·带脚本| G[脚本包 Scripts<br/>SKILL.md + scripts + resources]
    C -->|IT 集成| H[API 接入<br/>url/method/参数 结构化表单]

    D --> I[Step 2 编辑器<br/>预填模板内容]
    E --> I
    F --> F1[解析 frontmatter<br/>自动带出名称/描述/工种] --> I
    G --> G1[填写 SKILL.md + 上传脚本/资源<br/>注: 脚本本期仅存储不执行] --> I
    H --> H1[结构化表单 ↔ 高级 JSON] --> J

    I --> J[Step 3 预览并发布]
    J --> K[技能卡片预览 + 校验]
    K --> L{校验通过?}
    L -->|是| M([发布成功<br/>进入 我的技能])
    L -->|否| I

    style B fill:#EEF2FF,stroke:#6366F1
    style M fill:#ECFDF5,stroke:#10B981
    style G fill:#F5F3FF,stroke:#8B5CF6
    style H fill:#FFFBEB,stroke:#F59E0B
```

---

## 3. 页面跳转图

```mermaid
flowchart LR
    subgraph 技能中心
        PLAZA[技能广场<br/>#plaza]
        MINE[我的技能<br/>#mine]
    end

    CREATE[创建技能<br/>page-create.html]
    DRAWER[技能详情抽屉<br/>行内滑出]
    TRY[试一试面板<br/>抽屉内嵌]
    AGENT[启用到 Agent<br/>下拉选择]
    DEBUG[高级调试<br/>折叠面板]

    PLAZA -->|点卡片| DRAWER
    MINE -->|点行| DRAWER
    DRAWER --> TRY
    DRAWER --> AGENT
    DRAWER -->|技术用户展开| DEBUG
    PLAZA -->|创建技能| CREATE
    MINE -->|创建技能| CREATE
    MINE -->|导入 JSON| PLAZA
    CREATE -->|发布成功| MINE

    style PLAZA fill:#EEF2FF,stroke:#6366F1
    style MINE fill:#EEF2FF,stroke:#6366F1
    style CREATE fill:#F5F3FF,stroke:#8B5CF6
```

> 实线 = 主导航；抽屉/面板为行内滑出，非独立路由页。

---

## 4. 核心交互时序图

### 4.1 试一试（调试降级后的行内形态）

```mermaid
sequenceDiagram
    participant U as 业务用户
    participant D as 详情抽屉
    participant T as 试一试面板
    participant S as 后端 SkillService

    U->>D: 打开技能详情
    U->>T: 点击 试一试
    T->>T: 按 inputSchema 渲染参数表单
    U->>T: 填写参数 → 点 运行
    T->>S: POST /skills/{id}/test {params}
    Note over T,S: 内容型技能无参，直接返回正文
    S-->>T: SkillResult（内容/JSON）
    T-->>U: 展示结果 + 耗时
    alt 技术用户
        U->>T: 展开 高级调试
        T->>S: preview（参数预检+执行计划）
        S-->>T: 校验结果 + 计划
        U->>S: run（真实执行，写审计）
    end
```

### 4.2 内容导入（SKILL.md → 技能）

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 内容导入面板
    participant P as 解析器
    participant E as 编辑器

    U->>F: 上传 .md / 粘贴 SKILL.md 文本
    F->>P: 读取内容
    P->>P: 检测 frontmatter（--- 包裹的 YAML）
    alt 有 frontmatter
        P-->>E: 带出 name / description / 工种
    else 无 frontmatter
        P-->>E: 取首行为名称，其余为正文
    end
    E-->>U: 预填编辑器，可继续修改
    U->>E: 补充/修正 → 进入预览发布
```

---

## 5. 状态与边界

| 场景 | 处理 |
|------|------|
| 广场无搜索结果 | 空态插画 + 「换个关键词，或按工种浏览」+ 创建技能入口 |
| 我的技能为空 | 空态 + 「从广场安装」/「创建第一个技能」双按钮 |
| 技能已被 Agent 绑定 | 删除时拦截，提示先解绑（错误码 5005） |
| 内置/MCP 技能 | 虚拟挂载，不可编辑/删除，仅可启停与试用 |
| 脚本包技能 | 标注「脚本暂不执行」，避免误导 |
