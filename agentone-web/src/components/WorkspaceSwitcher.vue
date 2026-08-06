<template>
  <n-dropdown :options="workspaceOptions" trigger="click" @select="handleSelect">
    <n-button quaternary size="small">
      <template #icon>
        <span>🏢</span>
      </template>
      {{ currentWorkspaceName }}
    </n-button>
  </n-dropdown>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { NDropdown, NButton } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

onMounted(async () => {
  if (authStore.isLoggedIn && authStore.workspaces.length === 0) {
    await authStore.loadWorkspaces()
  }
})

const workspaceOptions = computed<DropdownOption[]>(() =>
  authStore.workspaces.map((ws) => ({
    label: ws.name,
    key: ws.id,
  }))
)

const currentWorkspaceName = computed(() => {
  const ws = authStore.workspaces.find((w) => w.id === authStore.workspaceId)
  return ws?.name || '选择工作空间'
})

async function handleSelect(key: string) {
  // S7: 切换工作空间需调用后端重新签发 JWT 并刷新 Cookie（否则后端仍用旧 workspace）
  await authStore.switchWorkspace(key)
}
</script>
