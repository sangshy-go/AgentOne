<template>
  <div class="page-container">
    <h1 class="page-title">发布审批</h1>
    <p class="page-desc">Agent 发布须经他人审批（双人原则：提交人不可审批自己的申请）</p>

    <div class="filter-bar">
      <n-select
        v-model:value="filter.status"
        :options="statusOptions"
        clearable
        placeholder="全部状态"
        style="width: 150px;"
        @update:value="resetAndLoad"
      />
      <n-button type="primary" ghost @click="resetAndLoad">查询</n-button>
    </div>

    <div class="page-card">
      <n-data-table
        :columns="columns"
        :data="requests"
        :bordered="false"
        :loading="loading"
      />
      <div v-if="total > pageSize" class="pagination-wrap">
        <n-pagination
          :page="page"
          :page-size="pageSize"
          :item-count="total"
          @update:page="handlePageChange"
        />
      </div>
      <n-empty v-if="!loading && requests.length === 0" description="暂无审批记录" style="margin-top: 40px;" />
    </div>

    <!-- 驳回弹窗：理由必填（前端校验 + 后端 8004 兜底） -->
    <n-modal v-model:show="rejectVisible" preset="card" title="驳回发布申请" style="width: 520px;">
      <n-form>
        <n-form-item label="驳回理由" required>
          <n-input
            v-model:value="rejectComment"
            type="textarea"
            :rows="4"
            placeholder="请说明驳回原因，便于提交人修改后重新提交"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="rejectVisible = false">取消</n-button>
          <n-button type="error" :loading="rejecting" @click="handleReject">确认驳回</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import type { VNodeChild } from 'vue'
import { useRouter } from 'vue-router'
import {
  NDataTable, NSelect, NButton, NTag, NPopconfirm, NModal, NForm, NFormItem,
  NInput, NSpace, NEmpty, NPagination, NEllipsis, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { PublishRequest } from '@/types'
import {
  listPublishRequests,
  approvePublishRequest,
  rejectPublishRequest,
  withdrawPublishRequest,
} from '@/services/approval'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const requests = ref<PublishRequest[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filter = reactive({ status: null as string | null })

const statusOptions = [
  { label: '待审批', value: 'pending' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
  { label: '已撤回', value: 'withdrawn' },
]

async function loadRequests() {
  loading.value = true
  try {
    const res = await listPublishRequests({
      status: filter.status || undefined,
      page: page.value,
      size: pageSize.value,
    })
    const data = res.data.data
    requests.value = data?.records || []
    total.value = data?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载审批列表失败')
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  page.value = 1
  loadRequests()
}

function handlePageChange(p: number) {
  page.value = p
  loadRequests()
}

// ---------- 操作 ----------

async function handleApprove(row: PublishRequest) {
  try {
    await approvePublishRequest(row.id)
    message.success('已通过，Agent 已发布上线')
    await loadRequests()
  } catch (e: any) {
    message.error(e?.message || '审批失败')
  }
}

const rejectVisible = ref(false)
const rejecting = ref(false)
const rejectComment = ref('')
const rejectingRow = ref<PublishRequest | null>(null)

function openReject(row: PublishRequest) {
  rejectingRow.value = row
  rejectComment.value = ''
  rejectVisible.value = true
}

async function handleReject() {
  if (!rejectComment.value.trim()) {
    message.warning('请填写驳回理由')
    return
  }
  if (!rejectingRow.value) return
  rejecting.value = true
  try {
    await rejectPublishRequest(rejectingRow.value.id, rejectComment.value.trim())
    message.success('已驳回，申请回到草稿状态')
    rejectVisible.value = false
    await loadRequests()
  } catch (e: any) {
    message.error(e?.message || '驳回失败')
  } finally {
    rejecting.value = false
  }
}

async function handleWithdraw(row: PublishRequest) {
  try {
    await withdrawPublishRequest(row.id)
    message.success('申请已撤回')
    await loadRequests()
  } catch (e: any) {
    message.error(e?.message || '撤回失败')
  }
}

// ---------- 表格列 ----------

function formatTime(t: string | null) {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}

const STATUS_META: Record<string, { label: string; type: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'error' },
  withdrawn: { label: '已撤回', type: 'default' },
}

const columns: DataTableColumns<PublishRequest> = [
  {
    title: 'Agent',
    key: 'agentName',
    ellipsis: { tooltip: true },
    render(row) {
      return h(
        'a',
        {
          style: 'color: var(--primary); cursor: pointer; font-weight: 600;',
          onClick: () => router.push(`/agents/${row.agentId}`),
        },
        row.agentName || row.agentId
      )
    },
  },
  { title: '提交人', key: 'submitterEmail', width: 200, ellipsis: { tooltip: true } },
  { title: '提交时间', key: 'submittedAt', width: 150, render(row) { return formatTime(row.submittedAt) } },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render(row) {
      const meta = STATUS_META[row.status] || { label: row.status, type: 'default' as const }
      return h(NTag, { type: meta.type, size: 'small', round: true }, () => meta.label)
    },
  },
  {
    title: '审批意见',
    key: 'reviewComment',
    width: 220,
    render(row) {
      if (!row.reviewComment) {
        return h('span', { style: 'color: var(--text-muted)' }, '—')
      }
      return h(NEllipsis, { tooltip: true }, () => row.reviewComment)
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render(row) {
      if (row.status !== 'pending') return h('span', { style: 'color: var(--text-muted)' }, '—')
      const buttons: VNodeChild[] = []
      // 提交人本人：可撤回
      if (row.submitterId === authStore.userId) {
        buttons.push(
          h(NPopconfirm, { onPositiveClick: () => handleWithdraw(row) }, {
            trigger: () => h(NButton, { size: 'small', quaternary: true }, () => '撤回'),
            default: () => '确认撤回该发布申请？',
          })
        )
      }
      // 审批人：有审批权且非提交人本人（双人原则，后端 8003 兜底）
      if (authStore.canApprove && row.submitterId !== authStore.userId) {
        buttons.push(
          h(NPopconfirm, { onPositiveClick: () => handleApprove(row) }, {
            trigger: () => h(NButton, { size: 'small', type: 'primary', quaternary: true }, () => '通过'),
            default: () => '确认通过该发布申请？通过后 Agent 立即上线并升版。',
          }),
          h(NButton, { size: 'small', type: 'error', quaternary: true, onClick: () => openReject(row) }, () => '驳回')
        )
      }
      if (buttons.length === 0) {
        return h('span', { style: 'color: var(--text-muted)' }, '等待审批')
      }
      return h(NSpace, { size: 4 }, () => buttons)
    },
  },
]

onMounted(loadRequests)
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
