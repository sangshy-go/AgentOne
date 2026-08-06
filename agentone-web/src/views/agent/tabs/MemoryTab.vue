<template>
  <div>
    <n-form label-placement="left" label-width="140" style="max-width: 600px;">
      <n-form-item label="记忆策略">
        <n-radio-group v-model:value="config.strategy">
          <n-radio-button value="sliding_window">滑动窗口</n-radio-button>
          <n-radio-button value="summary">摘要压缩</n-radio-button>
          <n-radio-button value="none">无记忆</n-radio-button>
        </n-radio-group>
      </n-form-item>
      <n-form-item label="最大轮次" v-if="config.strategy === 'sliding_window'">
        <n-input-number v-model:value="config.maxRounds" :min="1" :max="100" />
      </n-form-item>
      <n-form-item label="最大 Token" v-if="config.strategy !== 'none'">
        <n-input-number v-model:value="config.maxTokens" :min="500" :max="128000" :step="500" />
      </n-form-item>
      <n-form-item label=" ">
        <n-button type="primary" @click="save">保存</n-button>
      </n-form-item>
    </n-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { NForm, NFormItem, NRadioGroup, NRadioButton, NInputNumber, NButton } from 'naive-ui'
import type { Agent } from '@/types'

const props = defineProps<{ agent: Agent | null }>()
const emit = defineEmits<{ save: [data: Partial<Agent>] }>()

const config = reactive({
  strategy: 'sliding_window',
  maxRounds: 20,
  maxTokens: 8000,
})

watch(() => props.agent?.memoryConfig, (val) => {
  if (val) {
    try { Object.assign(config, JSON.parse(val)) } catch { /* ignore */ }
  }
}, { immediate: true })

function save() {
  emit('save', { memoryConfig: JSON.stringify(config) })
}
</script>
