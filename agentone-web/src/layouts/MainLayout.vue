<template>
  <div class="app-shell">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed }">
      <!-- Logo -->
      <div class="sidebar-header">
        <a class="sidebar-logo">
          <div class="icon-box">灵一</div>
          <div v-if="!collapsed" class="logo-text">
            灵一 AgentOne
            <span class="version">v1.0</span>
          </div>
        </a>
      </div>

      <!-- Workspace Switcher -->
      <div v-if="!collapsed" class="workspace-switcher" @click="showWsDropdown = !showWsDropdown">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span class="ws-dot"></span>
          <span class="ws-name">{{ currentWorkspaceName }}</span>
        </div>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </div>
      <!-- Workspace Dropdown -->
      <div v-if="showWsDropdown && !collapsed" class="ws-dropdown">
        <div
          v-for="ws in authStore.workspaces"
          :key="ws.id"
          class="ws-dropdown-item"
          :class="{ active: ws.id === authStore.workspaceId }"
          @click="handleSwitchWorkspace(ws.id)"
        >
          {{ ws.name }}
        </div>
        <div v-if="authStore.workspaces.length === 0" class="ws-dropdown-empty">暂无工作空间</div>
      </div>

      <!-- Navigation -->
      <nav class="sidebar-nav">
        <template v-for="group in navGroups" :key="group.label">
          <div class="nav-section-label">{{ group.label }}</div>
          <div
            v-for="item in group.items"
            :key="item.key"
            class="nav-item"
            :class="{ active: activeMenu === item.key }"
            @click="handleMenuClick(item.key)"
          >
            <span class="nav-icon" v-html="item.icon"></span>
            <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
          </div>
        </template>
      </nav>

      <!-- Footer: User Card -->
      <div class="sidebar-footer">
        <div class="user-card" @click="showUserMenu = !showUserMenu">
          <div class="user-avatar">{{ authStore.nickname?.charAt(0) || 'U' }}</div>
          <div v-if="!collapsed" class="user-info">
            <div class="user-name">{{ authStore.nickname || '用户' }}</div>
            <div class="user-role">管理员</div>
          </div>
          <svg v-if="!collapsed" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--text-muted); flex-shrink: 0;">
            <path d="M6 9l6 6 6-6" />
          </svg>
        </div>
        <!-- User Dropdown -->
        <div v-if="showUserMenu" class="user-dropdown">
          <div class="user-dropdown-item" @click="handleMenuClick('Settings'); showUserMenu = false">个人设置</div>
          <div class="user-dropdown-item" @click="handleLogout">退出登录</div>
        </div>
        <!-- Collapse Trigger -->
        <div class="collapse-trigger" @click="collapsed = !collapsed">
          <svg :style="{ transform: collapsed ? 'rotate(180deg)' : '' }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
          <span v-if="!collapsed">收起侧边栏</span>
        </div>
      </div>
    </aside>

    <!-- Main -->
    <main class="main" :style="{ marginLeft: collapsed ? '64px' : '260px' }">
      <!-- Header -->
      <header class="header">
        <div class="header-breadcrumb">
          {{ currentRouteName }}
        </div>
        <div class="header-right">
          <button class="header-btn" title="暗色模式" @click="authStore.toggleDarkMode">
            {{ authStore.isDarkMode ? '☀️' : '🌙' }}
          </button>
          <button class="header-btn" title="帮助">?</button>
        </div>
      </header>

      <!-- Content -->
      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const message = useMessage()

const collapsed = ref(false)
const showWsDropdown = ref(false)
const showUserMenu = ref(false)

// 加载工作空间列表，供侧边栏切换器使用
onMounted(() => {
  if (authStore.workspaces.length === 0) {
    authStore.loadWorkspaces()
  }
})

// Navigation groups with SVG icons (from the prototype)
const navGroups = [
  {
    label: '概览',
    items: [
      {
        key: 'Dashboard',
        label: '工作台',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>',
      },
    ],
  },
  {
    label: '构建',
    items: [
      {
        key: 'Agents',
        label: 'Agent 管理',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="9" r="3"/><circle cx="8" cy="16" r="2"/><circle cx="16" cy="16" r="2"/><path d="M12 12v2M9.2 14.5l3 3M14.8 14.5l-3 3"/></svg>',
      },
      {
        key: 'Knowledge',
        label: '知识库',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>',
      },
      {
        key: 'Skills',
        label: 'Skill 中心',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>',
      },
      {
        key: 'Mcp',
        label: 'MCP 集成',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 2v6M15 2v6"/><path d="M6 8h12v4a6 6 0 01-6 6 6 6 0 01-6-6V8z"/><path d="M12 18v4"/></svg>',
      },
    ],
  },
  {
    label: '接入',
    items: [
      {
        key: 'Models',
        label: '模型管理',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><circle cx="6" cy="6" r="1" fill="currentColor"/><circle cx="6" cy="18" r="1" fill="currentColor"/></svg>',
      },
      {
        key: 'ApiKeys',
        label: 'API Key',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg>',
      },
    ],
  },
  {
    label: '管理',
    items: [
      {
        key: 'Settings',
        label: '工作空间',
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>',
      },
    ],
  },
]

const activeMenu = computed(() => route.name as string)
const currentRouteName = computed(() => {
  const map: Record<string, string> = {
    Dashboard: '控制台',
    Agents: 'Agent 管理',
    AgentDetail: 'Agent 详情',
    Knowledge: '知识库',
    Skills: 'Skill 中心',
    Mcp: 'MCP 集成',
    Models: '模型管理',
    ApiKeys: 'API Key',
    Settings: '工作空间',
  }
  return map[route.name as string] || '工作台'
})

const currentWorkspaceName = computed(() => {
  const ws = authStore.workspaces.find((w) => w.id === authStore.workspaceId)
  return ws?.name || '选择工作空间'
})

function handleMenuClick(key: string) {
  router.push({ name: key })
  showWsDropdown.value = false
}

async function handleSwitchWorkspace(wsId: string) {
  showWsDropdown.value = false
  if (wsId === authStore.workspaceId) return
  await authStore.switchWorkspace(wsId)
  // 切换后刷新当前页面数据（各页面 onMounted 会重新加载）
  message.success('已切换工作空间')
  router.replace({ path: route.path, force: true })
}

function handleLogout() {
  authStore.logout()
  showUserMenu.value = false
  message.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
/* ============================================================
   App Shell
   ============================================================ */
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #F0F2F8;
}

/* ============================================================
   Sidebar
   ============================================================ */
.sidebar {
  width: 260px;
  background: #FFFFFF;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  overflow-y: auto;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-header {
  padding: 22px 20px 16px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  letter-spacing: -0.5px;
}

.icon-box {
  width: 40px;
  height: 40px;
  background: var(--grad-primary);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 800;
  box-shadow: var(--glow-primary-md);
  flex-shrink: 0;
}

.logo-text {
  font-size: 15px;
  font-weight: 800;
  background: var(--grad-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.version {
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 600;
  background: var(--surface);
  padding: 2px 8px;
  border-radius: 20px;
  border: 1px solid var(--border);
  -webkit-text-fill-color: var(--text-muted);
}

/* Workspace Switcher */
.workspace-switcher {
  margin: 16px 14px 0;
  padding: 10px 14px;
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  transition: var(--transition);
}

.workspace-switcher:hover {
  border-color: var(--primary-border);
  box-shadow: var(--glow-primary);
  transform: translateY(-1px);
}

.ws-name {
  font-weight: 700;
  color: var(--text);
}

.ws-dot {
  width: 8px;
  height: 8px;
  background: var(--green);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--green);
}

/* Workspace Dropdown */
.ws-dropdown {
  margin: 4px 14px 0;
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  animation: modalIn 0.2s ease;
}

.ws-dropdown-item {
  padding: 10px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: var(--transition);
  color: var(--text-secondary);
}

.ws-dropdown-item:hover {
  background: var(--primary-bg);
  color: var(--primary);
}

.ws-dropdown-item.active {
  color: var(--primary);
  font-weight: 700;
  background: var(--indigo-bg);
}

.ws-dropdown-empty {
  padding: 10px 14px;
  font-size: 12px;
  color: var(--text-muted);
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  padding: 8px 0;
}

.nav-section-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 22px 20px 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  margin: 2px 10px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13.5px;
  font-weight: 500;
  border-radius: var(--radius);
  transition: var(--transition);
  position: relative;
}

.nav-item:hover {
  background: #FFFFFF;
  color: var(--primary);
  box-shadow: var(--shadow-sm);
}

.nav-item.active {
  background: var(--grad-primary-2);
  color: white;
  font-weight: 600;
  box-shadow: var(--glow-primary-md);
}

.nav-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Sidebar Footer */
.sidebar-footer {
  margin-top: auto;
  padding: 14px;
  border-top: 1px solid var(--border-light);
  flex-shrink: 0;
  position: relative;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: var(--transition);
}

.user-card:hover {
  background: var(--surface-alt);
}

.user-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 700;
  font-size: 13px;
  flex-shrink: 0;
  box-shadow: var(--glow-primary);
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.user-role {
  font-size: 11px;
  color: var(--text-muted);
}

/* User Dropdown */
.user-dropdown {
  position: absolute;
  bottom: 100%;
  left: 14px;
  right: 14px;
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  margin-bottom: 4px;
  animation: modalIn 0.2s ease;
  z-index: 10;
}

.user-dropdown-item {
  padding: 10px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: var(--transition);
  color: var(--text-secondary);
}

.user-dropdown-item:hover {
  background: var(--primary-bg);
  color: var(--primary);
}

/* Collapse Trigger */
.collapse-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: var(--transition);
}

.collapse-trigger:hover {
  background: var(--surface-alt);
  color: var(--primary);
}

.collapse-trigger svg {
  transition: transform 0.25s ease;
  flex-shrink: 0;
}

/* ============================================================
   Main Content Area
   ============================================================ */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  transition: margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

/* Header */
.header {
  height: var(--header-h);
  background: #FFFFFF;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 32px;
  gap: 20px;
  position: sticky;
  top: 0;
  z-index: 50;
  flex-shrink: 0;
}

.header-breadcrumb {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
}

.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-btn {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: #FFFFFF;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: var(--transition);
  position: relative;
  font-size: 15px;
}

.header-btn:hover {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
  box-shadow: var(--glow-primary-md);
  transform: translateY(-2px);
}

/* Content */
.content {
  padding: 32px;
  flex: 1;
  animation: pageIn 0.4s ease;
}
</style>
