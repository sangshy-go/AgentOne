<template>
  <div class="page-container">
    <h1 class="page-title">工作空间设置</h1>
    <p class="page-desc">管理你的工作空间信息和成员</p>

    <!-- 基本信息 -->
    <div class="page-card" style="margin-bottom: 24px;">
      <div class="section-title">基本信息</div>
      <n-form label-placement="left" label-width="120" style="max-width: 600px;">
        <n-form-item label="工作空间名称">
          <n-input v-model:value="wsName" placeholder="输入工作空间名称" :disabled="!authStore.isAdmin" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input
            v-model:value="wsDesc"
            type="textarea"
            placeholder="输入工作空间描述"
            :rows="3"
            :disabled="!authStore.isAdmin"
          />
        </n-form-item>
        <n-form-item>
          <button class="btn-gradient" :disabled="!authStore.isAdmin || saving" @click="onSave">
            {{ saving ? '保存中...' : '保存' }}
          </button>
          <span v-if="!authStore.isAdmin" class="perm-hint">仅管理员或所有者可修改</span>
        </n-form-item>
      </n-form>
    </div>

    <!-- 成员管理 -->
    <div class="page-card">
      <div class="member-header">
        <div class="section-title" style="margin-bottom: 0;">成员管理</div>
        <n-button v-if="authStore.isAdmin" type="primary" size="small" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          添加成员
        </n-button>
      </div>

      <n-data-table :columns="columns" :data="members" :bordered="false" :loading="membersLoading" />
      <div v-if="membersTotal > memberPageSize" class="pagination-wrap">
        <n-pagination
          :page="memberPage"
          :page-size="memberPageSize"
          :item-count="membersTotal"
          @update:page="handleMemberPageChange"
        />
      </div>
      <n-empty v-if="!membersLoading && members.length === 0" description="暂无成员" style="margin-top: 40px;" />
      <p v-if="!authStore.isAdmin" class="perm-hint" style="margin-top: 12px;">
        当前角色（{{ roleLabel(authStore.currentRole) }}）仅可查看成员列表
      </p>
    </div>

    <!-- 添加成员弹窗 -->
    <n-modal
      v-model:show="showAdd"
      preset="dialog"
      title="添加成员"
      positive-text="添加"
      negative-text="取消"
      :positive-button-props="{ loading: adding }"
      @positive-click="handleAdd"
    >
      <n-form>
        <n-form-item label="邮箱">
          <n-input v-model:value="addForm.email" placeholder="输入已注册用户的邮箱" />
        </n-form-item>
        <n-form-item label="角色">
          <n-select v-model:value="addForm.role" :options="assignableRoleOptions" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import {
  NForm, NFormItem, NInput, NButton, NDataTable, NModal, NSelect,
  NTag, NPopconfirm, NEmpty, NPagination, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { Member } from '@/types'
import { listMembers, addMember, updateMemberRole, removeMember, updateWorkspace } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'

const message = useMessage()
const authStore = useAuthStore()

// ---------- 基本信息 ----------
const wsName = ref('')
const wsDesc = ref('')
const saving = ref(false)

function loadWorkspaceForm() {
  const ws = authStore.workspaces.find((w) => w.id === authStore.workspaceId)
  wsName.value = ws?.name || ''
  wsDesc.value = ws?.description || ''
}

async function onSave() {
  if (!wsName.value.trim()) {
    message.warning('工作空间名称不能为空')
    return
  }
  saving.value = true
  try {
    await updateWorkspace(authStore.workspaceId, {
      name: wsName.value.trim(),
      description: wsDesc.value.trim(),
    })
    message.success('已保存')
    await authStore.loadWorkspaces() // 刷新侧边栏显示的名称与角色
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ---------- 成员管理 ----------
const members = ref<Member[]>([])
const membersLoading = ref(false)
const memberPage = ref(1)
const memberPageSize = ref(20)
const membersTotal = ref(0)

async function loadMembers() {
  membersLoading.value = true
  try {
    const res = await listMembers({ current: memberPage.value, size: memberPageSize.value })
    const data = res.data.data
    members.value = data?.records || []
    membersTotal.value = data?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载成员列表失败')
  } finally {
    membersLoading.value = false
  }
}

function handleMemberPageChange(p: number) {
  memberPage.value = p
  loadMembers()
}

const ROLE_LABELS: Record<string, string> = {
  owner: '所有者',
  admin: '管理员',
  developer: '开发者',
  observer: '观察者',
  auditor: '审计员',
}

function roleLabel(role: string) {
  return ROLE_LABELS[role] || role
}

function roleTagType(role: string): 'default' | 'info' | 'success' | 'warning' {
  if (role === 'owner') return 'warning'
  if (role === 'admin') return 'info'
  if (role === 'developer') return 'success'
  return 'default'
}

/** 可分配角色（owner 不可直接指派，创建者自动获得） */
const assignableRoleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '开发者', value: 'developer' },
  { label: '观察者', value: 'observer' },
  { label: '审计员', value: 'auditor' },
]

const showAdd = ref(false)
const adding = ref(false)
const addForm = reactive({ email: '', role: 'developer' })

function openAdd() {
  addForm.email = ''
  addForm.role = 'developer'
  showAdd.value = true
}

async function handleAdd() {
  const email = addForm.email.trim()
  if (!email) {
    message.warning('请输入邮箱')
    return false
  }
  adding.value = true
  try {
    await addMember({ email, role: addForm.role })
    message.success('成员已添加')
    showAdd.value = false
    await loadMembers()
  } catch (e: any) {
    message.error(e?.message || '添加失败')
    return false // 阻止弹窗关闭
  } finally {
    adding.value = false
  }
  return true
}

async function handleRoleChange(row: Member, role: string) {
  try {
    await updateMemberRole(row.userId, role)
    message.success('角色已更新')
    await loadMembers()
  } catch (e: any) {
    message.error(e?.message || '角色更新失败')
    await loadMembers() // 回滚显示
  }
}

async function handleRemove(row: Member) {
  try {
    await removeMember(row.userId)
    message.success('成员已移除')
    await loadMembers()
  } catch (e: any) {
    message.error(e?.message || '移除失败')
  }
}

const columns: DataTableColumns<Member> = [
  { title: '昵称', key: 'nickname', width: 160, render(row) { return row.nickname || '—' } },
  { title: '邮箱', key: 'email', ellipsis: { tooltip: true } },
  {
    title: '角色',
    key: 'role',
    width: 200,
    render(row) {
      // owner 行不可变更；非管理员只读标签
      if (row.role === 'owner' || !authStore.isAdmin) {
        return h(NTag, { type: roleTagType(row.role), size: 'small', round: true }, () => roleLabel(row.role))
      }
      return h(NSelect, {
        value: row.role,
        size: 'small',
        options: assignableRoleOptions,
        style: 'width: 140px;',
        onUpdateValue: (role: string) => handleRoleChange(row, role),
      })
    },
  },
  {
    title: '加入时间',
    key: 'joinedAt',
    width: 160,
    render(row) {
      return row.joinedAt ? row.joinedAt.replace('T', ' ').slice(0, 16) : '—'
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render(row) {
      if (!authStore.isAdmin || row.role === 'owner') {
        return h('span', { style: 'color: var(--text-muted)' }, '—')
      }
      return h(NPopconfirm, { onPositiveClick: () => handleRemove(row) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '移除'),
        default: () => `确认将 ${row.nickname || row.email} 移出工作空间？`,
      })
    },
  },
]

onMounted(async () => {
  // 工作空间列表可能尚未加载（直接进入本页时）
  if (authStore.workspaces.length === 0) {
    await authStore.loadWorkspaces()
  }
  loadWorkspaceForm()
  loadMembers()
})
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}

.member-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.perm-hint {
  margin-left: 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
