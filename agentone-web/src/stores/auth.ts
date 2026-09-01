import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, getUserInfo } from '@/services/auth'
import type { Workspace } from '@/types'
import { listWorkspaces } from '@/services/workspace'
import api from '@/services/api'

export const useAuthStore = defineStore('auth', () => {
  // S7: 真实 JWT 由 HttpOnly Cookie 承载，前端不再保存 token（避免 XSS 窃取）。
  // 仅用「authed」标记表示会话存在，用户资料（非敏感）持久化用于 UI 展示。
  const hasSession = ref(localStorage.getItem('authed') === 'true')
  const userId = ref(localStorage.getItem('userId') || '')
  const email = ref(localStorage.getItem('email') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const workspaceId = ref(localStorage.getItem('workspaceId') || '')
  const workspaces = ref<Workspace[]>([])
  const isDarkMode = ref(localStorage.getItem('darkMode') === 'true')

  const isLoggedIn = computed(() => hasSession.value)

  /** 当前工作空间角色（RBAC，来自工作空间列表，切换空间后随 loadWorkspaces 刷新） */
  const currentRole = computed(
    () => workspaces.value.find((w) => w.id === workspaceId.value)?.role ?? 'observer'
  )
  const isAdmin = computed(() => currentRole.value === 'owner' || currentRole.value === 'admin')
  /** 观察者 / 审计员：全局只读，前端隐藏写操作（后端 WorkspaceRbacFilter 兜底强制） */
  const isReadOnly = computed(() => currentRole.value === 'observer' || currentRole.value === 'auditor')
  /** 审计员：只读 + 可查审计日志与审批列表（课题⑩） */
  const isAuditor = computed(() => currentRole.value === 'auditor')
  /** 审批权：仅 owner / admin（双人原则由后端按提交人校验兜底） */
  const canApprove = computed(() => isAdmin.value)
  /** 审计日志可见：owner / admin / auditor */
  const canViewAudit = computed(() => isAdmin.value || isAuditor.value)

  /** 把后端返回的用户信息写入内存 + 本地（不含 token） */
  function applyAuth(data: { userId: string; email: string; nickname: string; workspaceId: string }) {
    userId.value = data.userId
    email.value = data.email
    nickname.value = data.nickname
    workspaceId.value = data.workspaceId
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('email', data.email)
    localStorage.setItem('nickname', data.nickname)
    localStorage.setItem('workspaceId', data.workspaceId)
    hasSession.value = true
    localStorage.setItem('authed', 'true')
  }

  /** 登录 */
  async function login(emailVal: string, password: string) {
    const res = await loginApi({ email: emailVal, password })
    const data = res.data.data
    applyAuth(data)
    return data
  }

  /** 注册 */
  async function register(emailVal: string, password: string, nicknameVal?: string) {
    const res = await registerApi({ email: emailVal, password, nickname: nicknameVal })
    const data = res.data.data
    applyAuth(data)
    return data
  }

  /** S7: 通过 /api/auth/me 恢复会话（页面刷新后 Cookie 仍在，但内存状态丢失） */
  async function fetchMe() {
    try {
      const res = await getUserInfo()
      const data = res.data.data
      applyAuth(data)
    } catch {
      clearSession()
    }
  }

  /** 清空本地会话（不改变 Cookie；真正登出由后端 /api/auth/logout 清除 Cookie） */
  function clearSession() {
    hasSession.value = false
    userId.value = ''
    email.value = ''
    nickname.value = ''
    workspaceId.value = ''
    workspaces.value = []
    localStorage.removeItem('authed')
    localStorage.removeItem('userId')
    localStorage.removeItem('email')
    localStorage.removeItem('nickname')
    localStorage.removeItem('workspaceId')
  }

  /** 切换工作空间：调用后端重新签发 JWT 并刷新 Cookie */
  async function switchWorkspace(wsId: string) {
    const res = await api.post('/api/auth/switch-workspace/' + wsId)
    const data = res.data.data
    // 同步后端返回的完整用户信息（workspaceId 已变，token/refreshToken 由 Cookie 更新）
    applyAuth({
      userId: data.userId,
      email: data.email,
      nickname: data.nickname,
      workspaceId: data.workspaceId,
    })
  }

  /** 加载工作空间列表 */
  async function loadWorkspaces() {
    const res = await listWorkspaces()
    workspaces.value = res.data.data
  }

  /** 退出登录：后端清除 HttpOnly Cookie */
  async function logout() {
    try {
      await api.post('/api/auth/logout')
    } catch {
      // 即使后端失败也清理本地状态
    }
    clearSession()
  }

  /** 切换暗色模式 */
  function toggleDarkMode() {
    isDarkMode.value = !isDarkMode.value
    localStorage.setItem('darkMode', String(isDarkMode.value))
  }

  return {
    hasSession,
    userId,
    email,
    nickname,
    workspaceId,
    workspaces,
    isDarkMode,
    isLoggedIn,
    currentRole,
    isAdmin,
    isReadOnly,
    isAuditor,
    canApprove,
    canViewAudit,
    login,
    register,
    fetchMe,
    switchWorkspace,
    loadWorkspaces,
    logout,
    clearSession,
    toggleDarkMode,
  }
})
