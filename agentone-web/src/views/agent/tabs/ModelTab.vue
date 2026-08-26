<template>
  <div>
    <n-form label-placement="left" label-width="140" style="max-width: 600px;">
      <n-form-item label="Chat 模型">
        <n-select
          v-model:value="config.chatModelId"
          :options="chatModelOptions"
          placeholder="选择 Chat 模型（未选择时回退系统默认模型）"
          filterable
          clearable
        />
      </n-form-item>
      <n-form-item label="Temperature">
        <n-slider v-model:value="config.temperature" :min="0" :max="2" :step="0.1" />
        <n-text depth="3" style="margin-left: 12px; min-width: 30px;">{{ config.temperature }}</n-text>
      </n-form-item>
      <n-form-item label="Max Tokens">
        <n-input-number v-model:value="config.maxTokens" :min="100" :max="128000" :step="100" />
      </n-form-item>
      <n-form-item label="Top P">
        <n-slider v-model:value="config.topP" :min="0" :max="1" :step="0.05" />
        <n-text depth="3" style="margin-left: 12px; min-width: 30px;">{{ config.topP }}</n-text>
      </n-form-item>
      <n-form-item label=" ">
        <n-button type="primary" @click="save">保存</n-button>
      </n-form-item>
    </n-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, onMounted, ref, computed } from 'vue'
import { NForm, NFormItem, NSelect, NSlider, NInputNumber, NButton, NText, useMessage } from 'naive-ui'
import type { Agent, Model } from '@/types'
import { listModelsByType } from '@/services/model'

const props = defineProps<{ agent: Agent | null }>()
const emit = defineEmits<{ save: [data: Partial<Agent>] }>()
const message = useMessage()

const chatModels = ref<Model[]>([])

const chatModelOptions = computed(() =>
  chatModels.value.map((m) => ({
    label: `${m.displayName || m.modelId}（${m.providerName}）`,
    value: m.id,
  }))
)

const config = reactive({
  chatModelId: null as string | null,
  temperature: 0.7,
  maxTokens: 4096,
  topP: 0.9,
})

onMounted(async () => {
  try {
    const res = await listModelsByType('chat')
    chatModels.value = res.data.data || []
  } catch {
    chatModels.value = []
    message.error('模型列表加载失败，请稍后重试')
  }
})

watch(() => props.agent?.modelConfig, (val) => {
  if (val) {
    try {
      const parsed = JSON.parse(val)
      Object.assign(config, parsed)
    } catch { /* ignore */ }
  }
}, { immediate: true })

function save() {
  emit('save', {
    modelConfig: JSON.stringify(config),
  })
  if (!config.chatModelId) {
    message.warning('未选择 Chat 模型：若未配置系统默认模型（OPENAI_API_KEY），该 Agent 将无法对话')
  }
}
</script>
