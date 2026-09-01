<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <n-text>绑定 Skill 后，其提示词会注入 Agent 系统提示以引导回答；当前版本仅作提示词注入，不会实际执行外部调用</n-text>
      <n-button type="primary" size="small" :disabled="!agent" @click="openAdd">绑定 Skill</n-button>
    </div>

    <n-data-table
      :columns="columns"
      :data="boundSkills"
      :bordered="false"
      :loading="loading"
    />

    <n-empty v-if="!loading && boundSkills.length === 0" description="暂未绑定 Skill" style="margin-top: 40px;" />

    <n-modal
      v-model:show="showAdd"
      preset="dialog"
      title="绑定 Skill"
      style="width: 640px;"
      positive-text="绑定"
      negative-text="取消"
      :positive-button-props="{ loading: binding, disabled: selectedSkills.length === 0 }"
      @positive-click="handleBind"
    >
      <n-input v-model:value="searchKey" placeholder="搜索 Skill..." style="margin-bottom: 12px;" />
      <n-checkbox-group v-model:value="selectedSkills">
        <div v-for="group in groupedAvailable" :key="group.type" style="margin-bottom: 14px;">
          <n-text depth="2" style="display: block; font-size: 12px; font-weight: 600; margin-bottom: 6px;">
            {{ group.label }}（{{ group.items.length }}）
          </n-text>
          <n-space vertical>
            <n-checkbox
              v-for="s in group.items"
              :key="s.id"
              :value="s.id"
              :disabled="boundSkillIds.has(s.id)"
            >
              <span>{{ s.name }}</span>
              <n-text v-if="s.description" depth="3" style="margin-left: 8px; font-size: 12px;">
                {{ s.description }}
              </n-text>
            </n-checkbox>
          </n-space>
        </div>
      </n-checkbox-group>
      <n-text v-if="filteredAvailable.length === 0" depth="3">没有可绑定的 Skill</n-text>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, computed, watch, onMounted } from 'vue'
import {
  NButton, NText, NDataTable, NEmpty, NModal, NInput,
  NCheckboxGroup, NCheckbox, NSpace, NTag, NPopconfirm, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { Agent } from '@/types'
import {
  listPlaza, listAgentSkillBindings, bindSkill, unbindSkill, toggleSkill,
  type SkillItem, type AgentSkillBinding,
} from '@/services/skill'

const props = defineProps<{ agent: Agent | null }>()
const message = useMessage()

// Skill 类型 → UI 展示名（DB 字段保持不变，仅改前端文案）
const TYPE_LABEL: Record<string, string> = {
  builtin: '系统内置',
  prompt: '指令模板',
  api: 'API 封装',
  mcp: 'MCP 工具',
  market: '市场安装',
}
// 分组排序：内置优先，MCP 靠后
const TYPE_ORDER = ['builtin', 'prompt', 'api', 'market', 'mcp']

const boundSkills = ref<AgentSkillBinding[]>([])
const availableSkills = ref<SkillItem[]>([])
const showAdd = ref(false)
const searchKey = ref('')
const selectedSkills = ref<string[]>([])
const loading = ref(false)
const binding = ref(false)

const boundSkillIds = computed(() => new Set(boundSkills.value.map((b) => b.skillId)))

const filteredAvailable = computed(() => {
  const kw = searchKey.value.trim().toLowerCase()
  return availableSkills.value.filter((s) => !kw || s.name.toLowerCase().includes(kw)
    || (s.description || '').toLowerCase().includes(kw))
})

const groupedAvailable = computed(() => {
  const map = new Map<string, SkillItem[]>()
  for (const s of filteredAvailable.value) {
    const t = s.type || 'other'
    if (!map.has(t)) map.set(t, [])
    map.get(t)!.push(s)
  }
  return TYPE_ORDER
    .filter((t) => map.has(t))
    .map((t) => ({ type: t, label: TYPE_LABEL[t] || t, items: map.get(t)! }))
})

async function loadBound() {
  if (!props.agent) {
    boundSkills.value = []
    return
  }
  loading.value = true
  try {
    const res = await listAgentSkillBindings(props.agent.id)
    boundSkills.value = res.data.data || []
  } catch (e: any) {
    message.error(e?.message || '加载绑定失败')
  } finally {
    loading.value = false
  }
}

async function loadAvailable() {
  try {
    // 绑定选择器走广场：只展示 active + 已发布的 MCP 工具，未发布的不出现在列表
    const res = await listPlaza({})
    availableSkills.value = res.data.data || []
  } catch (e: any) {
    message.error(e?.message || '加载 Skill 列表失败')
  }
}

function openAdd() {
  selectedSkills.value = []
  searchKey.value = ''
  showAdd.value = true
}

async function handleBind() {
  if (!props.agent) return
  binding.value = true
  try {
    for (const skillId of selectedSkills.value) {
      const skill = availableSkills.value.find((s) => s.id === skillId)
      await bindSkill({
        agentId: props.agent.id,
        skillId,
        skillVersion: skill?.version,
      })
    }
    message.success('绑定成功')
    showAdd.value = false
    await loadBound()
  } catch (e: any) {
    message.error(e?.message || '绑定失败')
  } finally {
    binding.value = false
  }
}

async function handleUnbind(row: AgentSkillBinding) {
  try {
    await unbindSkill(row.id)
    message.success('已解绑')
    await loadBound()
  } catch (e: any) {
    message.error(e?.message || '解绑失败')
  }
}

async function handleToggle(row: AgentSkillBinding) {
  try {
    await toggleSkill(row.id, !row.enabled)
    await loadBound()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

const columns: DataTableColumns<AgentSkillBinding> = [
  { title: 'Skill 名称', key: 'skillName' },
  { title: '类型', key: 'skillType', render(row) {
    const tagType = row.skillType === 'builtin' ? 'info'
      : row.skillType === 'mcp' ? 'warning'
      : row.skillType === 'prompt' ? 'info'
      : 'success'
    return h(NTag, { size: 'small', type: tagType }, () => TYPE_LABEL[row.skillType] || row.skillType)
  }},
  { title: '版本', key: 'skillVersion' },
  { title: '状态', key: 'enabled', render(row) {
    return h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, () => (row.enabled ? '已启用' : '已禁用'))
  }},
  { title: '操作', key: 'actions', render(row) {
    return h(NSpace, null, () => [
      h(NButton, { size: 'small', quaternary: true, onClick: () => handleToggle(row) },
        () => (row.enabled ? '禁用' : '启用')),
      h(NPopconfirm, { onPositiveClick: () => handleUnbind(row) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '解绑'),
        default: () => '确认解绑该 Skill？',
      }),
    ])
  }},
]

onMounted(() => {
  loadAvailable()
  loadBound()
})

watch(() => props.agent?.id, () => loadBound())
</script>
