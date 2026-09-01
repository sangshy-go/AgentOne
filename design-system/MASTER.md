# AgentOne 设计系统规范（项目级）

> **覆盖声明：** 本文件是 AgentOne 项目级设计系统规范，**覆盖**全局基准 `~/.claude/rules/design-system/MASTER.md`。
> 所有 AgentOne 页面（真机 Vue 与 HTML 原型）必须遵循本文件。
> Token 唯一事实来源：`agentone-web/src/assets/styles/global.css`，本文件与其保持同步。

---

## 设计语言

**靛蓝渐变体系**。主色靛蓝 `#6366F1`，经紫罗兰 `#8B5CF6` 过渡到青 `#06B6D4` 的 135° 三色渐变是品牌签名，
用于主按钮、激活态、强调文字与关键行动点。整体气质：科技感、轻盈、有呼吸的浅色界面。

- 页面背景不用纯白：`#EEF2FF`（淡靛蓝）铺底，白色卡片浮于其上
- 辉光（glow）替代传统阴影做强调：主色元素带 `rgba(99,102,241,0.25~0.35)` 光晕
- 状态色一律配「底色 + 边框」三件套（见下表），不用裸色块

---

## 颜色

### 主色与渐变

| Token | 值 | 用途 |
|-------|-----|------|
| `--primary` | `#6366F1` | 主按钮、链接、激活态 |
| `--primary-hover` | `#4F46E5` | 主按钮悬停 |
| `--primary-light` | `#EEF2FF` | 选中底色、chip 激活底 |
| `--primary-bg` | `#F5F3FF` | 弱主色底 |
| `--primary-border` | `#C7D2FE` | 选中边框 |
| `--grad-primary` | `135deg, #6366F1 0%, #8B5CF6 50%, #06B6D4 100%` | 品牌签名渐变：主 CTA、toggle 开态、Hero |
| `--grad-primary-2` | `135deg, #6366F1 0%, #8B5CF6 100%` | 双色渐变（图标底、标签） |
| `--grad-blue` | `135deg, #3B82F6 0%, #06B6D4 100%` | 信息类图标底 |
| `--grad-purple` | `135deg, #8B5CF6 0%, #EC4899 100%` | 紫色系图标底 |
| `--grad-green` | `135deg, #10B981 0%, #06B6D4 100%` | 成功/增长类 |
| `--grad-orange` | `135deg, #F59E0B 0%, #EF4444 100%` | 警示类 |
| `--grad-text` | 同 `--grad-primary` | 渐变文字（`background-clip: text`） |

### 表面与边框

| Token | 值 | 用途 |
|-------|-----|------|
| `--surface` | `#FFFFFF` | 卡片、弹窗、抽屉 |
| `--surface-alt` | `#F8FAFF` | 次级面板底 |
| `--bg` | `#EEF2FF` | 页面背景 |
| `--border` | `#E2E8F0` | 常规边框 |
| `--border-light` | `#EEF2FF` | 弱分割线 |
| `--border-strong` | `#CBD5E1` | 强边框（输入框 focus 前） |

### 文字

| Token | 值 | 用途 |
|-------|-----|------|
| `--text` | `#0F172A` | 主文本 |
| `--text-secondary` | `#475569` | 次要文本 |
| `--text-muted` | `#64748B` | 辅助说明 |
| `--text-placeholder` | `#94A3B8` | 占位符 |

### 状态色（三件套：色 / 底 / 边框）

| 语义 | 色 | 底 | 边框 |
|------|-----|-----|------|
| 成功 green | `#10B981` | `#ECFDF5` | `#A7F3D0` |
| 警告 orange | `#F59E0B` | `#FFFBEB` | `#FDE68A` |
| 危险 red | `#EF4444` | `#FEF2F2` | `#FECACA` |
| 紫 purple | `#8B5CF6` | `#F5F3FF` | `#DDD6FE` |
| 靛蓝 indigo | `#6366F1` | `#EEF2FF` | `#C7D2FE` |
| 蓝 blue | `#3B82F6` | `#EFF6FF` | `#BFDBFE` |
| 青 cyan | `#06B6D4` | `#ECFEFF` | `#A5F3FC` |

---

## 字体

```css
font-family: 'Inter', system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue",
  Arial, "PingFang SC", "Microsoft YaHei", sans-serif;
```

- 基准字号 **14px**（企业后台密度），行高 1.6
- 字号阶梯：12（徽章/辅助）/ 13（表格、次要）/ 14（正文）/ 15~16（卡片标题）/ 18~20（页标题）/ 24+（Hero）
- 字重：400 / 500 / 600 / 700；标题最多用到 700，不用 800+

---

## 间距与布局

基础单位 4px，间距取 4 的倍数（4/8/12/16/20/24/32）。

| Token | 值 | 用途 |
|-------|-----|------|
| `--sidebar-w` | `260px` | 左侧导航宽 |
| `--header-h` | `64px` | 顶栏高 |

布局骨架：260px 固定侧边栏 + 内容区（`--bg` 底，内容卡片白底圆角浮起）。
移动端（≤768px）侧边栏收起为汉堡菜单。

## 圆角

| Token | 值 | 用途 |
|-------|-----|------|
| `--radius-xs` | `6px` | 徽章、小标签 |
| `--radius-sm` | `8px` | 输入框、小按钮 |
| `--radius` | `12px` | 按钮、常规卡片 |
| `--radius-lg` | `16px` | 大卡片、抽屉 |
| `--radius-xl` | `20px` | Hero、特色区块 |
| `--radius-2xl` | `28px` | 特大容器 |
| `--radius-full` | `9999px` | chip、toggle、头像 |

## 阴影与辉光

| Token | 值 | 用途 |
|-------|-----|------|
| `--shadow-sm` | `0 1px 2px rgba(15,23,42,0.04)` | 静态卡片 |
| `--shadow` | `0 2px 8px rgba(15,23,42,0.06)` | 常规浮层 |
| `--shadow-md` | `0 8px 24px rgba(15,23,42,0.08)` | 悬停卡片、下拉 |
| `--shadow-lg` | `0 16px 40px rgba(15,23,42,0.10)` | 弹窗、抽屉 |
| `--shadow-xl` | `0 24px 56px rgba(15,23,42,0.14)` | Hero |
| `--glow-primary` | `0 0 24px rgba(99,102,241,0.25)` | 主色元素辉光 |
| `--glow-primary-md` | `0 8px 32px rgba(99,102,241,0.30)` | 主 CTA 悬停 |
| `--glow-blue` | `0 0 24px rgba(59,130,246,0.25)` | 蓝色元素 |
| `--glow-purple` | `0 0 24px rgba(139,92,246,0.25)` | 紫色元素 |

## 动画

- 统一缓动：`--transition: 0.25s cubic-bezier(0.4, 0, 0.2, 1)`
- 悬停：`translateY(-1~2px)` + 阴影加深；不用 `scale`（避免布局偏移感）
- 抽屉/面板：`transform: translateX(100%) → 0`，0.3s
- 尊重 `prefers-reduced-motion`

---

## 组件规范

### 主按钮
```css
.btn-primary {
  background: var(--grad-primary);   /* 渐变，不用纯色 */
  color: #fff;
  padding: 9px 20px;
  border-radius: var(--radius-sm);
  font-weight: 600; font-size: 14px;
  border: none; cursor: pointer;
  box-shadow: var(--glow-primary);
  transition: var(--transition);
}
.btn-primary:hover { box-shadow: var(--glow-primary-md); transform: translateY(-1px); }
```

### 次要按钮
```css
.btn-secondary {
  background: var(--surface);
  color: var(--text-secondary);
  border: 1px solid var(--border);
  padding: 9px 20px; border-radius: var(--radius-sm);
  font-weight: 500; font-size: 14px; cursor: pointer;
  transition: var(--transition);
}
.btn-secondary:hover { border-color: var(--primary-border); color: var(--primary); background: var(--primary-light); }
```

### Chip（筛选标签）
```css
.chip {
  padding: 6px 14px; border-radius: var(--radius-full);
  font-size: 13px; color: var(--text-secondary);
  background: var(--surface); border: 1px solid var(--border);
  cursor: pointer; transition: var(--transition);
}
.chip.active {
  background: var(--grad-primary); color: #fff; border-color: transparent;
  box-shadow: var(--glow-primary);
}
```

### 卡片
```css
.card {
  background: var(--surface);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  transition: var(--transition); cursor: pointer;
}
.card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); border-color: var(--primary-border); }
```

### Toggle 开关
- 开态：`--grad-primary` 渐变轨道；关态：`#CBD5E1`
- 尺寸 40×22，圆点 18px；`label` 必须 `display:inline-block`（否则宽度不生效）

### 状态徽章
```css
.badge-green { color: var(--green); background: var(--green-bg); border: 1px solid var(--green-border);
  padding: 2px 10px; border-radius: var(--radius-full); font-size: 12px; }
```

---

## 图标

- 内联 SVG（Lucide / Heroicons 风格线性图标），禁止 Emoji 作图标、禁止外部图标库请求
- 图标底：渐变圆角方块（`--grad-*` + `--radius-sm`），图标白色 18~20px
- 原型中所有图标必须内联，保证 `file://` 直接打开零外部请求

## 响应式

| 断点 | 行为 |
|------|------|
| ≥1024px | 侧边栏 260px 常驻；卡片网格 3 列 |
| 768~1024px | 网格 2 列 |
| ≤768px | 侧边栏收起为汉堡；列表纵向堆叠；抽屉全宽 |

基准测试尺寸：375px（手机）、1280px（桌面）。

## 禁止事项

- ❌ 用全局基准的蓝色系 `#3B82F6` 作主色（本项目主色是靛蓝 `#6366F1`）
- ❌ Emoji 图标、外部字体/图标 CDN 请求
- ❌ 裸状态色块（必须三件套：色 + 底 + 边框）
- ❌ `scale` 悬停、无 transition 的状态跳变
- ❌ 对比度低于 4.5:1 的正文文字
- ❌ 未处理的空状态（列表/搜索/表单页必须有）

---

**最后更新**：2026-08-12（Skill 中心 v2 原型阶段建立，与 global.css 同步）
