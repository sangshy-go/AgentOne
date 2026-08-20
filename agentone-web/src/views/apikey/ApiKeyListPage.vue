<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h1 class="page-title">API Key 管理</h1>
        <p class="page-desc" style="margin-bottom: 0;">生成 API Key，接入你的应用</p>
      </div>
      <n-button type="primary" @click="openCreate">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        创建 API Key
      </n-button>
    </div>

    <n-data-table
      :columns="columns"
      :data="apiKeys"
      :bordered="false"
      :loading="loading"
    />

    <div v-if="total > pageSize" class="pagination-wrap">
      <n-pagination
        :page="page"
        :page-size="pageSize"
        :item-count="total"
        show-size-picker
        :page-sizes="[10, 20, 50]"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <n-empty v-if="!loading && apiKeys.length === 0" description="暂无 API Key" style="margin-top: 40px;" />

    <!-- Create Modal -->
    <n-modal
      v-model:show="showCreate"
      preset="dialog"
      title="创建 API Key"
      positive-text="创建"
      negative-text="取消"
      :positive-button-props="{ loading: creating }"
      @positive-click="handleCreate"
    >
      <n-form>
        <n-form-item label="环境">
          <n-select v-model:value="createForm.env" :options="envOptions" />
        </n-form-item>
        <n-form-item label="每日调用上限">
          <n-input-number v-model:value="createForm.dailyLimit" :min="1" :max="100000" />
        </n-form-item>
      </n-form>
    </n-modal>

    <!-- 创建成功：仅展示一次的完整 Key -->
    <n-modal
      v-model:show="showResult"
      preset="dialog"
      title="API Key 创建成功"
      positive-text="我已保存"
      @positive-click="showResult = false"
    >
      <n-alert type="warning" :show-icon="true" style="margin-bottom: 12px;">
        请立即复制保存，密钥仅展示一次，关闭后无法再次查看。
      </n-alert>
      <n-input :value="createdKey" readonly type="textarea" :autosize="{ minRows: 2 }" />
      <n-button block secondary type="primary" style="margin-top: 12px;" @click="copyKey">
        复制密钥
      </n-button>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import {
  NDataTable, NModal, NForm, NFormItem,
  NSelect, NInputNumber, NTag, NButton, NPopconfirm, NEmpty, NAlert, NPagination, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { ApiKey } from '@/types'
import { listApiKeys, createApiKey, disableApiKey } from '@/services/apikey'

const message = useMessage()
const apiKeys = ref<ApiKey[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showCreate = ref(false)
const creating = ref(false)
const showResult = ref(false)
const createdKey = ref('')
const createForm = reactive({ env: 'live', dailyLimit: 1000 })
const envOptions = [
  { label: '生产 (Live)', value: 'live' },
  { label: '测试 (Test)', value: 'test' },
]

async function loadList() {
  loading.value = true
  try {
    const res = await listApiKeys(page.value, pageSize.value)
    const data = res.data.data
    apiKeys.value = data?.records || []
    total.value = data?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadList()
}

function handlePageSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadList()
}

function openCreate() {
  createForm.env = 'live'
  createForm.dailyLimit = 1000
  showCreate.value = true
}

async function handleCreate() {
  creating.value = true
  try {
    const res = await createApiKey({ env: createForm.env, dailyLimit: createForm.dailyLimit })
    createdKey.value = res.data.data.apiKey
    showResult.value = true
    showCreate.value = false
    await loadList()
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleDisable(row: ApiKey) {
  try {
    await disableApiKey(row.id)
    message.success('已停用')
    await loadList()
  } catch (e: any) {
    message.error(e?.message || '停用失败')
  }
}

function copyKey() {
  navigator.clipboard?.writeText(createdKey.value).then(
    () => message.success('已复制'),
    () => fallbackCopy(createdKey.value),
  )
}

/** 非安全上下文（无 navigator.clipboard）时的降级复制 */
function fallbackCopy(text: string) {
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    if (ok) {
      message.success('已复制')
      return
    }
  } catch {
    /* ignore */
  }
  message.error('复制失败，请手动复制')
}

/** 将 ISO 时间格式化为 YYYY-MM-DD HH:mm */
function formatDate(v: string | null | undefined): string {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const columns: DataTableColumns<ApiKey> = [
  { title: 'Key 前缀', key: 'keyPrefix' },
  { title: '环境', key: 'env', render(row) {
    return h(NTag, { type: row.env === 'live' ? 'success' : 'warning', size: 'small', round: true }, () => row.env)
  }},
  { title: '状态', key: 'status', render(row) {
    return h(NTag, { type: row.status === 'active' ? 'success' : 'error', size: 'small', round: true }, () => row.status)
  }},
  { title: '每日上限', key: 'dailyLimit' },
  { title: '创建时间', key: 'createdAt', render(row) {
    return formatDate(row.createdAt)
  } },
  { title: '操作', key: 'actions', render(row) {
    if (row.status !== 'active') return h('span', { style: 'color: var(--text-muted)' }, '已停用')
    return h(NPopconfirm, {
      onPositiveClick: () => handleDisable(row),
    }, {
      trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '停用'),
      default: () => '确认停用该 API Key？停用后立即失效。',
    })
  }},
]

onMounted(loadList)
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 14px;
}
</style>
