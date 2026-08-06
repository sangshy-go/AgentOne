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
      positive-text="绑定"
      negative-text="取消"
      :positive-button-props="{ loading: binding, disabled: selectedSkills.length === 0 }"
      @positive-click="handleBind"
    >
      <n-input v-model:value="searchKey" placeholder="搜索 Skill..." style="margin-bottom: 12px;" />
      <n-checkbox-group v-model:value="selectedSkills">
        <n-space vertical>
          <n-checkbox
            v-for="s in filteredAvailable"
            :key="s.id"
            :value="s.id"
            :label="`${s.name}（${s.type}）`"
            :disabled="boundSkillIds.has(s.id)"
          />
        </n-space>
      </n-checkbox-group>
      <div v-if="skillTotal > skillPageSize" style="margin-top: 12px; display: flex; justify-content: center;">
        <n-pagination
          :page="skillPage"
          :page-size="skillPageSize"
          :item-count="skillTotal"
          @update:page="handleSkillPageChange"
        />
      </div>
      <n-text v-if="filteredAvailable.length === 0" depth="3">没有可绑定的 Skill</n-text>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, computed, watch, onMounted } from 'vue'
import {
  NButton, NText, NDataTable, NEmpty, NModal, NInput,
  NCheckboxGroup, NCheckbox, NSpace, NTag, NPopconfirm, NPagination, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { Agent } from '@/types'
import {
  listSkills, listAgentSkillBindings, bindSkill, unbindSkill, toggleSkill,
  type SkillItem, type AgentSkillBinding,
} from '@/services/skill'

const props = defineProps<{ agent: Agent | null }>()
const message = useMessage()

const boundSkills = ref<AgentSkillBinding[]>([])
const availableSkills = ref<SkillItem[]>([])
const showAdd = ref(false)
const searchKey = ref('')
const selectedSkills = ref<string[]>([])
const loading = ref(false)
const binding = ref(false)
const skillPage = ref(1)
const skillPageSize = ref(20)
const skillTotal = ref(0)

const boundSkillIds = computed(() => new Set(boundSkills.value.map((b) => b.skillId)))

const filteredAvailable = computed(() => {
  const kw = searchKey.value.trim().toLowerCase()
  return availableSkills.value.filter((s) => !kw || s.name.toLowerCase().includes(kw))
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
    const res = await listSkills(skillPage.value, skillPageSize.value)
    const data = res.data.data
    availableSkills.value = data?.records || []
    skillTotal.value = data?.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载 Skill 列表失败')
  }
}

function handleSkillPageChange(p: number) {
  skillPage.value = p
  loadAvailable()
}

function openAdd() {
  selectedSkills.value = []
  searchKey.value = ''
  skillPage.value = 1
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
    return h(NTag, { size: 'small', type: row.skillType === 'builtin' ? 'info' : 'success' }, () => row.skillType)
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
