import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Landing',
    component: () => import('@/views/landing/LandingPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    // 控制台挂在 /dashboard 下；子路由用绝对路径，保持 /agents、/knowledge 等既有 URL 不变
    path: '/dashboard',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardPage.vue'),
      },
      {
        path: '/agents',
        name: 'Agents',
        component: () => import('@/views/agent/AgentListPage.vue'),
      },
      {
        path: '/agents/:id',
        name: 'AgentDetail',
        component: () => import('@/views/agent/AgentDetailPage.vue'),
      },
      {
        path: '/knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/KnowledgeListPage.vue'),
      },
      {
        path: '/skills',
        name: 'Skills',
        component: () => import('@/views/skill/SkillCenterPage.vue'),
      },
      {
        path: '/skills/create',
        name: 'SkillCreate',
        component: () => import('@/views/skill/SkillCreatePage.vue'),
      },
      {
        path: '/mcp',
        name: 'Mcp',
        component: () => import('@/views/mcp/McpServerPage.vue'),
      },
      {
        path: '/models',
        name: 'Models',
        component: () => import('@/views/model/ModelListPage.vue'),
      },
      {
        path: '/api-keys',
        name: 'ApiKeys',
        component: () => import('@/views/apikey/ApiKeyListPage.vue'),
      },
      {
        path: '/settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsPage.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫（S7：会话由 HttpOnly Cookie 承载，isLoggedIn 基于本地会话标记）
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  // 首页 / 是公开营销页：登录与否都展示，导航栏按登录态切换按钮（不再强制跳控制台）
  if (to.meta.requiresAuth !== false && !authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 刷新后内存态丢失，但 Cookie 仍在：用 /me 恢复会话
  if (to.meta.requiresAuth !== false && authStore.isLoggedIn && !authStore.userId) {
    await authStore.fetchMe()
    if (!authStore.isLoggedIn) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }
  if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
    next('/dashboard')
    return
  }
  next()
})

// C2: 多标签页统一登出——其他标签页清除 localStorage(authed) 时，本标签页同步登出
window.addEventListener('storage', (e) => {
  if (e.key === 'authed' && e.newValue === null) {
    const authStore = useAuthStore()
    if (authStore.isLoggedIn) {
      authStore.clearSession()
      router.push('/login')
    }
  }
})

export default router
