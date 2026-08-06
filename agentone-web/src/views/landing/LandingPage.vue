<template>
  <div class="landing">
    <!-- ============================================================
         导航栏
         ============================================================ -->
    <header class="nav" :class="{ scrolled: navScrolled }">
      <div class="nav-inner">
        <div class="nav-brand" @click="scrollTop">
          <div class="nav-logo">灵一</div>
          <span class="nav-name">灵一 AgentOne</span>
          <span class="nav-version">开源</span>
        </div>
        <nav class="nav-links">
          <span class="nav-link" @click="goAnchor('features')">核心亮点</span>
          <span class="nav-link" @click="goAnchor('compare')">能力对比</span>
          <span class="nav-link" @click="goAnchor('deploy')">私有化部署</span>
        </nav>
        <div class="nav-actions">
          <template v-if="authStore.isLoggedIn">
            <span class="nav-user">{{ authStore.nickname || '已登录' }}</span>
            <button class="nav-btn ghost" @click="handleLogout">退出</button>
            <button class="nav-btn solid" @click="goDashboard">进入控制台</button>
          </template>
          <template v-else>
            <button class="nav-btn ghost" @click="goLogin">登录</button>
            <button class="nav-btn solid" @click="goRegister">免费注册</button>
          </template>
        </div>
      </div>
    </header>

    <!-- ============================================================
         Hero：左文案 + 右「看得见的 Agent」透明调用链动画
         ============================================================ -->
    <section class="hero">
      <div class="hero-bg" aria-hidden="true">
        <div class="grid-lines"></div>
        <div class="glow glow-tr"></div>
        <div class="glow glow-bl"></div>
      </div>

      <div class="hero-inner">
        <div class="hero-copy">
          <div class="hero-badge">
            <span class="dot"></span>
            信创友好 · 合规可审计 · 开源 AI 中台
          </div>
          <h1 class="hero-title">
            让 AI 中台<br />
            <span class="hl">透明、可审计、<span class="hl-tail">可掌控。</span></span>
          </h1>
          <p class="hero-sub">
            AgentOne 灵一，面向银行、国企与合规型企业的开源 AI 中台。调用链全程可视，操作留痕可查，权限与流程可治理——Java 团队可接管，数据不出内网。
          </p>
          <div class="hero-ctas">
            <button class="cta-primary" @click="goRegister">
              免费开始使用
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </button>
            <button class="cta-ghost" @click="goLogin">登录已有账号</button>
          </div>
          <div class="hero-trust">
            <span class="trust-item"><CheckIcon />Java 全栈 · 可二开</span>
            <span class="trust-item"><CheckIcon />数据不出域 · 可私有化</span>
            <span class="trust-item"><CheckIcon />国产模型即插即用</span>
          </div>
        </div>

        <!-- 透明调用链控制台 -->
        <div class="hero-visual">
          <div class="console">
            <div class="console-head">
              <span class="traffic r"></span><span class="traffic y"></span><span class="traffic g"></span>
              <span class="console-title">灵一 AgentOne · 透明调用链</span>
              <span class="live"><span class="live-dot"></span>LIVE</span>
            </div>
            <div class="console-body">
              <div
                v-for="(line, i) in consoleLines"
                :key="cycle + '-' + i"
                class="cline"
                :class="line.type"
              >
                <span class="cline-tag">{{ line.tag }}</span>
                <span class="cline-text">{{ line.text }}</span>
              </div>
              <div class="cline cursor-line">
                <span class="cursor"></span>
              </div>
            </div>
            <div class="console-foot">
              思考 → 调用 → 留痕 · 全程可视、可回放、可审计
            </div>
          </div>
          <div class="float-chip chip-a">
            <span class="chip-dot green"></span>审计留痕 · 已记录 1,284 条
          </div>
          <div class="float-chip chip-b">
            <span class="chip-dot indigo"></span>流程审批 · 已批准
          </div>
        </div>
      </div>

      <!-- 统计条 -->
      <div class="stats">
        <div class="stat" v-for="s in stats" :key="s.label">
          <div class="stat-num">{{ s.num }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         核心亮点：Bento 不对称布局
         ============================================================ -->
    <section class="section" id="features">
      <div class="section-head reveal">
        <div class="eyebrow"><i></i>01 · 核心亮点</div>
        <h2 class="section-title">不止能跑通，更要<em>敢上生产</em></h2>
        <p class="section-sub">
          对企业而言，AI 的价值不取决于它有多聪明，而取决于它是否<strong>可观测、可审计、可治理</strong>。
        </p>
      </div>

      <div class="bento">
        <!-- 大卡：看得见的 Agent -->
        <div class="b-card b-big reveal">
          <div class="b-icon indigo">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" />
            </svg>
          </div>
          <div class="b-kick">OBSERVABLE</div>
          <h3 class="b-title">透明调用链</h3>
          <p class="b-tag">不是黑盒，是直播。</p>
          <p class="b-desc">
            Agent 在想什么、调了哪个工具、传了什么参数、拿到什么结果，逐帧可视、可回放、可调试。
            对开发是调试利器，对合规是「可解释、可追溯」的底气。
          </p>
          <div class="chain">
            <span class="chain-node">思考</span>
            <span class="chain-arrow">→</span>
            <span class="chain-node">调用工具</span>
            <span class="chain-arrow">→</span>
            <span class="chain-node">拿到结果</span>
            <span class="chain-arrow">→</span>
            <span class="chain-node done">回答并留痕</span>
          </div>
        </div>

        <!-- 说得清的合规 -->
        <div class="b-card reveal">
          <div class="b-icon green">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M9 11l3 3L22 4" /><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
            </svg>
          </div>
          <div class="b-kick">AUDITABLE</div>
          <h3 class="b-title">全量审计留痕</h3>
          <p class="b-tag">谁在何时做了什么，一查便知。</p>
          <p class="b-desc">每次调用、每个操作自动留痕。监管来查，来龙去脉一条条摆得出来。</p>
          <div class="audit b-demo">
            <div class="audit-row"><span class="a-time">10:24:03</span><span class="a-act">调用 http_request</span><span class="a-ok">已记录</span></div>
            <div class="audit-row"><span class="a-time">10:24:05</span><span class="a-act">读取知识库《合规手册》</span><span class="a-ok">已记录</span></div>
            <div class="audit-row"><span class="a-time">10:24:08</span><span class="a-act">生成回复 · 张三复核</span><span class="a-ok">已记录</span></div>
          </div>
        </div>

        <!-- 管得住的中台 -->
        <div class="b-card reveal">
          <div class="b-icon purple">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0110 0v4" />
            </svg>
          </div>
          <div class="b-kick">GOVERNED</div>
          <h3 class="b-title">权限与流程治理</h3>
          <p class="b-tag">权限管控 + 流程审批。</p>
          <p class="b-desc">细粒度角色权限，高危操作人工确认后才执行。AI 在笼子里跑，批了才动。</p>
          <div class="flow b-demo">
            <span class="flow-node">AI 申请</span>
            <span class="flow-arrow">→</span>
            <span class="flow-node">人工审批</span>
            <span class="flow-arrow">→</span>
            <span class="flow-node ok">执行</span>
          </div>
        </div>

        <!-- Java 可接管 -->
        <div class="b-card reveal">
          <div class="b-icon blue">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M16 18l6-6-6-6M8 6l-6 6 6 6" />
            </svg>
          </div>
          <div class="b-kick">JAVA-NATIVE</div>
          <h3 class="b-title">Java 全栈可接管</h3>
          <p class="b-tag">Spring Boot 全栈，没有第二门语言。</p>
          <p class="b-desc">你的后端团队 clone 下来当天就能读、能改、能二开，无缝长进现有 Spring Cloud 体系。</p>
          <div class="code-chip b-demo">
            <span class="code-prompt">$</span> git clone agentone &amp;&amp; docker compose up
          </div>
        </div>

        <!-- 可度量 RAG -->
        <div class="b-card b-mid reveal">
          <div class="b-icon cyan">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M3 3v18h18" /><path d="M7 14l4-4 3 3 5-6" />
            </svg>
          </div>
          <div class="b-kick">MEASURABLE</div>
          <h3 class="b-title">可度量的 RAG</h3>
          <p class="b-tag">知识库准不准，用数字说话。</p>
          <p class="b-desc">制度、规章、合规文档入库后，Recall@K / MRR 自动出分，调参效果可回归对比。</p>
          <div class="metrics b-demo">
            <div class="metric"><span class="m-label">Recall@5</span><span class="m-bar"><span class="m-fill" style="width: 92%"></span></span><span class="m-val">92%</span></div>
            <div class="metric"><span class="m-label">MRR</span><span class="m-bar"><span class="m-fill" style="width: 87%"></span></span><span class="m-val">0.87</span></div>
          </div>
        </div>

        <!-- 信创友好 -->
        <div class="b-card b-wide reveal">
          <div class="b-wide-main">
            <div class="b-icon orange">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="2" y="3" width="20" height="14" rx="2" /><path d="M8 21h8M12 17v4" />
              </svg>
            </div>
            <div class="b-wide-body">
              <div class="b-kick">LOCALIZED</div>
              <h3 class="b-title">信创友好 · 国产化</h3>
              <p class="b-tag">国产大模型即插即用，数据全程不出内网。</p>
              <p class="b-desc">OpenAI 协议全兼容，换模型不换代码；私有化 vLLM / Ollama 直接对接，信创环境可适配。</p>
            </div>
          </div>
          <div class="b-wide-providers">
            <div class="prov-cap">已适配 · 模型 / 运行时</div>
            <div class="providers">
              <span class="provider">通义千问</span>
              <span class="provider">DeepSeek</span>
              <span class="provider">智谱 GLM</span>
              <span class="provider">月之暗面</span>
              <span class="provider">vLLM</span>
              <span class="provider">Ollama</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         能力对比
         ============================================================ -->
    <section class="section alt" id="compare">
      <div class="section-head reveal">
        <div class="eyebrow"><i></i>02 · 能力对比</div>
        <h2 class="section-title">功能广度让一步，<br /><em>可信深度赢全场</em></h2>
        <p class="section-sub">和 Dify、FastGPT 同台较量——比的不是谁的功能更多，而是谁能进生产、过审计、交给你的团队。</p>
      </div>
      <div class="table-wrap reveal">
        <table class="cmp">
          <thead>
            <tr>
              <th class="dim">维度</th>
              <th>Dify</th>
              <th>FastGPT</th>
              <th class="us">AgentOne 灵一</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in compare" :key="row.dim">
              <td class="dim">{{ row.dim }}</td>
              <td class="muted">{{ row.dify }}</td>
              <td class="muted">{{ row.fastgpt }}</td>
              <td class="us">{{ row.us }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- ============================================================
         CTA：私有化部署
         ============================================================ -->
    <section class="section" id="deploy">
      <div class="cta-banner reveal">
        <div class="cta-glow" aria-hidden="true"></div>
        <div class="cta-eyebrow">03 · 私有化部署</div>
        <h2 class="cta-title">5 分钟，把 AI 中台开进你的机房。</h2>
        <p class="cta-sub">一条命令，私有化就绪。数据不出域，合规可审计，Java 团队可接管。</p>
        <div class="cta-code">
          <span class="code-prompt">$</span> docker compose up -d
        </div>
        <div class="cta-actions">
          <button class="cta-primary light" @click="goRegister">
            免费注册，立即体验
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </button>
          <button class="cta-ghost light" @click="goLogin">登录</button>
        </div>
      </div>
    </section>

    <!-- ============================================================
         页脚
         ============================================================ -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <div class="nav-logo">灵一</div>
          <div>
            <div class="footer-name">灵一 AgentOne</div>
            <div class="footer-slogan">让每一次 AI 决策，都经得起审视。</div>
          </div>
        </div>
        <div class="footer-links">
          <span @click="goAnchor('features')">核心亮点</span>
          <span @click="goAnchor('compare')">能力对比</span>
          <span @click="goAnchor('deploy')">私有化部署</span>
          <span @click="goLogin">登录</span>
          <span @click="goRegister">注册</span>
        </div>
        <div class="footer-meta">
          开源项目 · 企业友好 / 可私有化（不含商业 SLA 与合规认证承诺） · © 2026 AgentOne
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, h } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function goLogin() {
  router.push('/login')
}
function goRegister() {
  router.push('/register')
}
function goDashboard() {
  router.push('/dashboard')
}
async function handleLogout() {
  await authStore.logout()
}
function goAnchor(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}
function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/* 导航栏滚动后收紧 */
const navScrolled = ref(false)
function onScroll() {
  navScrolled.value = window.scrollY > 24
}

/* 「看得见的 Agent」透明调用链：逐条流入，循环播放 */
interface CLine {
  type: 'user' | 'thought' | 'tool' | 'result' | 'answer'
  tag: string
  text: string
}
const SCRIPT: CLine[] = [
  { type: 'user', tag: '用户', text: '查一下昨天的订单量' },
  { type: 'thought', tag: '思考', text: '需要调用订单系统接口获取昨日数据…' },
  { type: 'tool', tag: '调用', text: 'http_request → GET /api/orders?date=yesterday' },
  { type: 'result', tag: '结果', text: '✓ 返回 1,284 条订单，总额 ¥3,562,000' },
  { type: 'answer', tag: '回答', text: '昨日订单量 1,284 单，总额 ¥3,562,000。已留痕。' },
]
const consoleLines = ref<CLine[]>([])
const cycle = ref(0)
let consoleTimer: number | null = null

function playConsole() {
  consoleLines.value = []
  cycle.value++
  let i = 0
  const step = () => {
    if (i < SCRIPT.length) {
      consoleLines.value = [...consoleLines.value, SCRIPT[i]]
      i++
      consoleTimer = window.setTimeout(step, 950)
    } else {
      consoleTimer = window.setTimeout(playConsole, 3600)
    }
  }
  step()
}

/* 滚动显现 */
let observer: IntersectionObserver | null = null
function setupReveal() {
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add('in')
          observer?.unobserve(e.target)
        }
      })
    },
    { threshold: 0.12 },
  )
  document.querySelectorAll('.reveal').forEach((el) => observer?.observe(el))
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  playConsole()
  setupReveal()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (consoleTimer) window.clearTimeout(consoleTimer)
  observer?.disconnect()
})

/* 内联 Check 图标组件（保持无 emoji） */
const CheckIcon = {
  render() {
    return h(
      'svg',
      { width: '14', height: '14', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2.6' },
      [h('path', { d: 'M20 6L9 17l-5-5' })],
    )
  },
}

/* 数据 */
const stats = [
  { num: '5 分钟', label: '一键私有化部署' },
  { num: '4 容器', label: 'docker compose 全拉起' },
  { num: '100%', label: 'OpenAI 协议兼容' },
  { num: '多租户', label: '工作空间数据隔离' },
]

const compare = [
  { dim: '技术栈', dify: 'Python', fastgpt: 'TypeScript', us: 'Java · Spring Boot' },
  { dim: 'Java 团队二开', dify: '需另养 Python 团队', fastgpt: '需另养前端团队', us: '当天接管' },
  { dim: 'Agent 调用过程', dify: '黑盒', fastgpt: '黑盒', us: '全程直播 · 可回放' },
  { dim: '审计合规', dify: '弱', fastgpt: '弱', us: '全量留痕 · 可导出' },
  { dim: 'RAG 质量', dify: '不可度量', fastgpt: '较强 · 不可度量', us: '可度量 · 可回归' },
  { dim: '私有化', dify: '支持', fastgpt: '支持', us: '一键 · 信创友好' },
]
</script>

<style scoped>
/* ============================================================
   基础与背景
   ============================================================ */
.landing {
  min-height: 100vh;
  background: #F6F8FD;
  color: var(--text);
  overflow-x: hidden;
  /* 落地页局部调色：去掉 靛→紫→青 彩虹，统一为 靛蓝→蓝；强调色只作实色与细线 */
  --grad-primary: linear-gradient(135deg, #4F46E5 0%, #2563EB 100%);
  --grad-primary-2: linear-gradient(135deg, #4F46E5 0%, #3B82F6 100%);
  --grad-text: linear-gradient(120deg, #4338CA 0%, #2563EB 100%);
  --ink: #0A1024;
}

/* ============================================================
   导航栏
   ============================================================ */
.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  transition: var(--transition);
  background: rgba(255, 255, 255, 0);
}
.nav.scrolled {
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
}
.nav-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 14px 32px;
  display: flex;
  align-items: center;
  gap: 32px;
}
.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.nav-logo {
  width: 38px;
  height: 38px;
  border-radius: var(--radius);
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  box-shadow: var(--glow-primary-md);
  flex-shrink: 0;
}
.nav-name {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.4px;
  color: var(--ink);
  white-space: nowrap;
}
.nav-version {
  font-size: 10px;
  font-weight: 700;
  color: var(--primary);
  background: var(--primary-light);
  border: 1px solid var(--primary-border);
  border-radius: 20px;
  padding: 2px 8px;
  white-space: nowrap;
}
.nav-links {
  display: flex;
  gap: 26px;
  margin-left: 8px;
}
.nav-link {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
}
.nav-link:hover {
  color: var(--primary);
}
.nav-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.nav-user {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-btn {
  border-radius: var(--radius);
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: var(--transition);
  border: 1.5px solid transparent;
  white-space: nowrap;
}
.nav-btn.ghost {
  background: transparent;
  color: var(--text-secondary);
  border-color: var(--border);
}
.nav-btn.ghost:hover {
  color: var(--primary);
  border-color: var(--primary-border);
  background: var(--primary-light);
}
.nav-btn.solid {
  background: var(--grad-primary-2);
  color: #fff;
  box-shadow: var(--glow-primary);
}
.nav-btn.solid:hover {
  box-shadow: var(--glow-primary-md);
  transform: translateY(-1px);
}

/* ============================================================
   Hero
   ============================================================ */
.hero {
  position: relative;
  padding: 150px 32px 56px;
}
.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  background: radial-gradient(120% 80% at 50% -10%, #FFFFFF 0%, #F4F7FD 55%, #EDF1FB 100%);
}
.grid-lines {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(37, 99, 235, 0.06) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(37, 99, 235, 0.06) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 80% 62% at 72% 0%, #000 28%, transparent 80%);
  -webkit-mask-image: radial-gradient(ellipse 80% 62% at 72% 0%, #000 28%, transparent 80%);
}
/* 取代三团彩色 aurora：两束低饱和、定位明确的环境光 */
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}
.glow-tr {
  width: 620px;
  height: 620px;
  top: -220px;
  right: -120px;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.22), transparent 68%);
  animation: drift 16s ease-in-out infinite alternate;
}
.glow-bl {
  width: 460px;
  height: 460px;
  bottom: -180px;
  left: -120px;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.16), transparent 70%);
  animation: drift 20s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(30px, -22px) scale(1.06); }
}

.hero-inner {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: 56px;
  align-items: center;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--primary-border);
  color: var(--primary);
  font-size: 12.5px;
  font-weight: 700;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
  animation: pageIn 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.hero-badge .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--green);
  animation: pulseGreen 2s ease-in-out infinite;
}
.hero-title {
  font-size: clamp(34px, 4.2vw, 54px);
  font-weight: 800;
  line-height: 1.16;
  letter-spacing: -1px;
  text-wrap: balance;
  color: var(--ink);
  margin-bottom: 22px;
  animation: pageIn 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.08s both;
}
/* 关键词：实色墨字 + 高亮笔触（取代彩虹渐变字，多行安全） */
.hl {
  color: var(--ink);
  background-image: linear-gradient(
    180deg,
    transparent 58%,
    rgba(99, 102, 241, 0.22) 58%,
    rgba(99, 102, 241, 0.22) 94%,
    transparent 94%
  );
  -webkit-box-decoration-break: clone;
  box-decoration-break: clone;
  padding: 0 0.06em;
  border-radius: 2px;
}
.hl-tail {
  white-space: nowrap;
}
.hero-sub {
  font-size: 16px;
  line-height: 1.75;
  color: var(--text-secondary);
  max-width: 520px;
  margin-bottom: 32px;
  animation: pageIn 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.16s both;
}
.hero-ctas {
  display: flex;
  gap: 14px;
  margin-bottom: 28px;
  animation: pageIn 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.24s both;
}
.cta-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--grad-primary-2);
  color: #fff;
  border: none;
  border-radius: var(--radius);
  padding: 14px 28px;
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
  box-shadow: var(--glow-primary-md);
  transition: var(--transition);
  position: relative;
  overflow: hidden;
}
.cta-primary::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.35), transparent);
  transition: left 0.5s;
}
.cta-primary:hover {
  transform: translateY(-2px);
  box-shadow: var(--glow-primary-md), 0 12px 28px rgba(99, 102, 241, 0.35);
}
.cta-primary:hover::after {
  left: 100%;
}
.cta-ghost {
  display: inline-flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.8);
  color: var(--text);
  border: 1.5px solid var(--border-strong);
  border-radius: var(--radius);
  padding: 14px 26px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: var(--transition);
}
.cta-ghost:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-light);
  transform: translateY(-2px);
}
.hero-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  animation: pageIn 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.32s both;
}
.trust-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.trust-item :deep(svg) {
  color: var(--green);
}

/* ============================================================
   透明调用链控制台
   ============================================================ */
.hero-visual {
  position: relative;
  animation: pageIn 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.2s both;
}
.console {
  background: #0F172A;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl), 0 0 0 1px rgba(99, 102, 241, 0.25), 0 24px 64px rgba(99, 102, 241, 0.25);
  overflow: hidden;
  position: relative;
}
.console::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--grad-primary);
}
.console-head {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.traffic {
  width: 11px;
  height: 11px;
  border-radius: 50%;
}
.traffic.r { background: #EF4444; }
.traffic.y { background: #F59E0B; }
.traffic.g { background: #10B981; }
.console-title {
  margin-left: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: #94A3B8;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.live {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #10B981;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10B981;
  animation: pulseGreen 1.6s ease-in-out infinite;
}
.console-body {
  padding: 20px 18px;
  min-height: 250px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.cline {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 13px;
  font-size: 12.5px;
  line-height: 1.55;
  animation: lineIn 0.45s cubic-bezier(0.16, 1, 0.3, 1) both;
}
@keyframes lineIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.cline-tag {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 800;
  padding: 2px 8px;
  border-radius: 6px;
  margin-top: 1px;
  letter-spacing: 0.5px;
}
.cline.user .cline-tag { background: rgba(59, 130, 246, 0.18); color: #60A5FA; }
.cline.thought .cline-tag { background: rgba(148, 163, 184, 0.15); color: #94A3B8; }
.cline.tool .cline-tag { background: rgba(139, 92, 246, 0.2); color: #A78BFA; }
.cline.result .cline-tag { background: rgba(16, 185, 129, 0.18); color: #34D399; }
.cline.answer .cline-tag { background: rgba(99, 102, 241, 0.22); color: #818CF8; }
.cline.user .cline-text { color: #CBD5E1; }
.cline.thought .cline-text { color: #94A3B8; font-style: italic; }
.cline.tool .cline-text { color: #C4B5FD; }
.cline.result .cline-text { color: #6EE7B7; }
.cline.answer .cline-text { color: #E0E7FF; font-weight: 700; }
.cursor-line { margin-bottom: 0; }
.cursor {
  display: inline-block;
  width: 8px;
  height: 15px;
  background: #818CF8;
  animation: blink 1s step-end infinite;
}
@keyframes blink { 50% { opacity: 0; } }
.console-foot {
  padding: 12px 18px;
  font-size: 11px;
  color: #64748B;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  background: rgba(255, 255, 255, 0.03);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.float-chip {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 9px 14px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  box-shadow: var(--shadow-md);
}
.chip-dot { width: 8px; height: 8px; border-radius: 50%; }
.chip-dot.green { background: var(--green); animation: pulseGreen 2s infinite; }
.chip-dot.indigo { background: var(--indigo); animation: pulseBlue 2s infinite; }
.chip-a { top: -18px; right: -14px; animation: float 4.5s ease-in-out infinite; }
.chip-b { bottom: -16px; left: -18px; animation: float 5.5s ease-in-out infinite 1s; }

/* ============================================================
   统计条
   ============================================================ */
.stats {
  position: relative;
  max-width: 1200px;
  margin: 64px auto 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
.stat {
  padding: 26px 24px;
  text-align: center;
  position: relative;
  transition: var(--transition);
}
.stat + .stat::before {
  content: '';
  position: absolute;
  left: 0;
  top: 24%;
  bottom: 24%;
  width: 1px;
  background: var(--border);
}
.stat:hover {
  background: var(--primary-light);
}
.stat-num {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -1px;
  color: var(--primary);
}
.stat-label {
  font-size: 12.5px;
  color: var(--text-muted);
  font-weight: 600;
  margin-top: 4px;
}

/* ============================================================
   通用 Section
   ============================================================ */
.section {
  padding: 96px 32px;
}
.section.alt {
  background: linear-gradient(180deg, #F4F6FC 0%, #EEF2FF 100%);
}
.section-head {
  max-width: 880px;
  margin: 0 auto 56px;
  text-align: center;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 2.5px;
  color: var(--primary);
  background: var(--primary-light);
  border: 1px solid var(--primary-border);
  border-radius: 20px;
  padding: 7px 18px;
  margin-bottom: 22px;
  text-transform: uppercase;
}
.eyebrow i {
  width: 18px;
  height: 3px;
  border-radius: 2px;
  background: var(--grad-primary);
  flex-shrink: 0;
}
.section-title {
  font-size: clamp(28px, 4vw, 46px);
  font-weight: 800;
  letter-spacing: -1.2px;
  line-height: 1.22;
  margin-bottom: 18px;
}
.section-title em {
  font-style: normal;
  color: var(--primary);
}
.section-sub {
  font-size: 16.5px;
  color: var(--text-secondary);
  line-height: 1.75;
  max-width: 680px;
  margin: 0 auto;
}
.section-sub strong {
  color: var(--primary);
  font-weight: 800;
}

/* ============================================================
   Bento 亮点区（不对称）
   ============================================================ */
.bento {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.b-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: 28px;
  box-shadow: var(--shadow);
  transition: var(--transition);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.b-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--grad-primary);
  opacity: 0;
  transition: var(--transition);
}
.b-card:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-lg), var(--glow-primary);
  border-color: var(--primary-border);
}
.b-card:hover::before {
  opacity: 1;
}
.b-big {
  grid-column: span 2;
  grid-row: span 2;
  background: linear-gradient(160deg, #FFFFFF 0%, #EEF3FF 100%);
  justify-content: center;
}
.b-mid {
  grid-column: span 2;
}
.b-wide {
  grid-column: span 3;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 32px;
  align-items: stretch;
}
.b-wide-main {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  min-width: 0;
}
.b-wide-body {
  min-width: 0;
}
.b-wide-providers {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
  padding: 22px 24px;
  border-radius: var(--radius-lg);
  background: linear-gradient(160deg, #F7F8FE 0%, #EDF1FF 100%);
  border: 1px solid var(--border);
}
.prov-cap {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--text-muted);
  text-transform: uppercase;
}
.b-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  flex-shrink: 0;
}
.b-icon.indigo { background: var(--indigo-bg); color: var(--indigo); border: 1px solid var(--indigo-border); }
.b-icon.green { background: var(--green-bg); color: var(--green); border: 1px solid var(--green-border); }
.b-icon.purple { background: var(--purple-bg); color: var(--purple); border: 1px solid var(--purple-border); }
.b-icon.blue { background: var(--blue-bg); color: var(--blue); border: 1px solid var(--blue-border); }
.b-icon.cyan { background: var(--cyan-bg); color: var(--cyan); border: 1px solid var(--cyan-border); }
.b-icon.orange { background: var(--orange-bg); color: var(--orange); border: 1px solid var(--orange-border); }
.b-wide .b-icon { margin: 2px 0 0; width: 52px; height: 52px; flex-shrink: 0; }
.b-kick {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: var(--primary);
  margin-bottom: 8px;
}
.b-big .b-kick { font-size: 12px; margin-bottom: 10px; }
.b-title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.5px;
  margin-bottom: 6px;
}
.b-big .b-title { font-size: 28px; }
.b-tag {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--primary);
  margin-bottom: 10px;
}
.b-desc {
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-secondary);
}
.b-big .b-desc { font-size: 15px; max-width: 500px; }
.b-mid .b-desc { max-width: 560px; }

/* 演示区块：普通卡片统一沉底对齐，大卡保持自身节奏 */
.b-demo { margin-top: 18px; }
.b-card:not(.b-big) > .b-demo { margin-top: auto; padding-top: 20px; }

/* 大卡内的调用链演示 */
.chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}
.chain-node {
  padding: 8px 16px;
  border-radius: var(--radius);
  background: #fff;
  border: 1.5px solid var(--border);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  transition: var(--transition);
}
.b-big:hover .chain-node { border-color: var(--primary-border); }
.chain-node.done {
  background: var(--grad-primary-2);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--glow-primary);
}
.chain-arrow { color: var(--primary); font-weight: 800; }

/* 审计演示 */
.audit {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}
.audit-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  font-size: 11.5px;
  border-bottom: 1px solid var(--border-light);
  background: #fff;
}
.audit-row:last-child { border-bottom: none; }
.a-time { color: var(--text-placeholder); font-family: 'JetBrains Mono', monospace; flex-shrink: 0; }
.a-act { color: var(--text-secondary); flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.a-ok {
  color: var(--green);
  font-weight: 700;
  font-size: 10.5px;
  flex-shrink: 0;
}

/* 审批流演示 */
.flow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.flow-node {
  padding: 7px 14px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  background: #fff;
}
.flow-node.ok {
  background: var(--green-bg);
  color: var(--green);
  border-color: var(--green-border);
}
.flow-arrow { color: var(--text-placeholder); font-weight: 800; }

/* 代码演示 */
.code-chip {
  background: #0F172A;
  color: #C4B5FD;
  border-radius: var(--radius);
  padding: 12px 14px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.code-prompt { color: #34D399; font-weight: 700; margin-right: 8px; }

/* RAG 指标演示 */
.metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px 28px; }
.metric { display: flex; align-items: center; gap: 10px; font-size: 12px; min-width: 0; }
.m-label { color: var(--text-muted); font-weight: 600; width: 64px; flex-shrink: 0; font-family: 'JetBrains Mono', monospace; }
.m-bar { flex: 1; height: 8px; border-radius: var(--radius-full); background: var(--border-light); overflow: hidden; }
.m-fill { display: block; height: 100%; border-radius: var(--radius-full); background: var(--grad-primary-2); }
.m-val { font-weight: 800; color: var(--primary); width: 40px; text-align: right; font-family: 'JetBrains Mono', monospace; }

/* 供应商 chips */
.providers { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.provider {
  padding: 9px 12px;
  border-radius: var(--radius);
  background: #fff;
  border: 1px solid var(--border);
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-secondary);
  text-align: center;
  transition: var(--transition);
}
.provider:hover {
  border-color: var(--primary-border);
  color: var(--primary);
  transform: translateY(-1px);
}
.b-wide:hover .provider { border-color: var(--primary-border); }

/* ============================================================
   对比表
   ============================================================ */
.table-wrap {
  max-width: 960px;
  margin: 0 auto;
  border-radius: var(--radius-xl);
  overflow: hidden;
  border: 1px solid var(--border);
  background: #fff;
  box-shadow: var(--shadow-md);
}
.cmp {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}
.cmp th, .cmp td {
  padding: 15px 20px;
  text-align: left;
  border-bottom: 1px solid var(--border-light);
}
.cmp thead th {
  background: var(--surface-alt);
  font-weight: 800;
  font-size: 13px;
  color: var(--text-secondary);
}
.cmp .dim { font-weight: 700; color: var(--text); }
.cmp .muted { color: var(--text-muted); }
.cmp tbody tr { transition: var(--transition); }
.cmp tbody tr:hover { background: var(--surface-alt); }
.cmp tbody tr:last-child td { border-bottom: none; }
.cmp th.us, .cmp td.us {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 800;
  border-left: 2px solid var(--primary-border);
  border-right: 2px solid var(--primary-border);
}
.cmp thead th.us { background: var(--grad-primary-2); color: #fff; }
.cmp tbody tr:last-child td.us { border-bottom: 2px solid var(--primary-border); }

/* ============================================================
   CTA 横幅
   ============================================================ */
.cta-banner {
  max-width: 1000px;
  margin: 0 auto;
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    radial-gradient(120% 140% at 82% -20%, rgba(79, 70, 229, 0.55) 0%, transparent 55%),
    radial-gradient(100% 120% at 0% 120%, rgba(37, 99, 235, 0.4) 0%, transparent 50%),
    #0B1124;
  background-size: 44px 44px, 44px 44px, auto, auto, auto;
  border-radius: var(--radius-2xl);
  padding: 72px 48px;
  text-align: center;
  color: #fff;
  box-shadow: var(--shadow-xl), 0 0 0 1px rgba(99, 102, 241, 0.28);
}
.cta-glow {
  position: absolute;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.25), transparent 70%);
  top: -200px;
  right: -100px;
  filter: blur(40px);
  pointer-events: none;
}
.cta-eyebrow {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.85);
  text-transform: uppercase;
  margin-bottom: 16px;
  position: relative;
}
.cta-title {
  font-size: 38px;
  font-weight: 800;
  letter-spacing: -1.4px;
  margin-bottom: 14px;
  position: relative;
}
.cta-sub {
  font-size: 16px;
  opacity: 0.92;
  max-width: 560px;
  margin: 0 auto 28px;
  line-height: 1.7;
  position: relative;
}
.cta-code {
  display: inline-block;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: var(--radius);
  padding: 12px 22px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 14px;
  color: #C7D2FE;
  margin-bottom: 32px;
  position: relative;
}
.cta-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  position: relative;
}
.cta-primary.light {
  background: #fff;
  color: var(--primary-hover);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.25);
}
.cta-primary.light:hover {
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.35);
}
.cta-ghost.light {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.5);
  color: #fff;
}
.cta-ghost.light:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: #fff;
  color: #fff;
}

/* ============================================================
   页脚
   ============================================================ */
.footer {
  background: #0F172A;
  color: #94A3B8;
  padding: 48px 32px 40px;
}
.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.footer-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}
.footer-name {
  font-size: 15px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.3px;
}
.footer-slogan {
  font-size: 12.5px;
  color: #64748B;
  margin-top: 2px;
}
.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
.footer-links span {
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
}
.footer-links span:hover { color: #fff; }
.footer-meta {
  font-size: 11.5px;
  color: #475569;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding-top: 20px;
  line-height: 1.7;
}

/* ============================================================
   滚动显现
   ============================================================ */
.reveal {
  opacity: 0;
  transform: translateY(26px);
  transition: opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1), transform 0.7s cubic-bezier(0.16, 1, 0.3, 1);
}
.reveal.in {
  opacity: 1;
  transform: translateY(0);
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1024px) {
  .hero-inner { grid-template-columns: 1fr; gap: 48px; }
  .hero-title { font-size: 44px; }
  .bento { grid-template-columns: 1fr 1fr; }
  .b-big, .b-wide { grid-column: span 2; grid-row: auto; }
  .b-mid { grid-column: span 1; }
  .metrics { grid-template-columns: 1fr; }
  .b-wide { grid-template-columns: 1fr; }
  .b-wide-main { gap: 16px; }
  .b-wide-providers { padding: 18px 20px; }
  .providers { grid-template-columns: repeat(3, 1fr); }
  .stats { grid-template-columns: 1fr 1fr; }
  .stat:nth-child(3)::before { display: none; }
}
@media (max-width: 640px) {
  .nav-links { display: none; }
  .nav-inner { padding: 12px 20px; gap: 16px; }
  .hero { padding: 120px 20px 40px; }
  .hero-title { font-size: 32px; }
  .hero-ctas { flex-direction: column; }
  .cta-primary, .cta-ghost { width: 100%; justify-content: center; }
  .bento { grid-template-columns: 1fr; }
  .b-big, .b-mid, .b-wide { grid-column: span 1; }
  .providers { grid-template-columns: repeat(2, 1fr); }
  .stats { grid-template-columns: 1fr 1fr; }
  .section { padding: 64px 20px; }
  .section-title { font-size: 28px; }
  .cta-banner { padding: 48px 24px; }
  .cta-title { font-size: 28px; }
  .cta-actions { flex-direction: column; }
  .cmp th, .cmp td { padding: 11px 12px; font-size: 12px; }
  .float-chip { display: none; }
}

/* 尊重减少动态偏好 */
@media (prefers-reduced-motion: reduce) {
  .glow, .float-chip, .hero-badge .dot, .live-dot, .chip-dot,
  .status-published .status-dot {
    animation: none !important;
  }
  .reveal { opacity: 1; transform: none; transition: none; }
}
</style>
