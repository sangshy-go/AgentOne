<template>
  <div>
    <n-form label-placement="left" label-width="140" style="max-width: 600px;">
      <n-form-item label="最大迭代次数">
        <n-input-number v-model:value="config.maxIterations" :min="1" :max="50" />
        <n-text depth="3" style="margin-left: 12px;">ReAct 循环最大执行轮数</n-text>
      </n-form-item>
      <n-form-item label="超时时间 (秒)">
        <n-input-number v-model:value="config.timeoutSeconds" :min="10" :max="300" />
      </n-form-item>
      <n-form-item label="重试次数">
        <n-input-number v-model:value="config.retryCount" :min="0" :max="5" />
      </n-form-item>
      <n-form-item label=" ">
        <n-button type="primary" @click="save">保存</n-button>
      </n-form-item>
    </n-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { NForm, NFormItem, NInputNumber, NButton, NText } from 'naive-ui'
import type { Agent } from '@/types'

const props = defineProps<{ agent: Agent | null }>()
const emit = defineEmits<{ save: [data: Partial<Agent>] }>()

const config = reactive({
  maxIterations: 10,
  timeoutSeconds: 60,
  retryCount: 2,
})

watch(() => props.agent?.advancedConfig, (val) => {
  if (val) {
    try { Object.assign(config, JSON.parse(val)) } catch { /* ignore */ }
  }
}, { immediate: true })

function save() {
  emit('save', { advancedConfig: JSON.stringify(config) })
}
</script>
