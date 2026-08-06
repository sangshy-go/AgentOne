<template>
  <div>
    <n-alert type="info" :bordered="false" style="margin-bottom: 16px;">
      使用 Markdown 编写 Agent 的人格、指令和行为规范。支持变量注入：{{ varHint }}
    </n-alert>

    <n-split direction="horizontal" style="height: 500px;">
      <template #1>
        <div style="padding: 12px; height: 100%;">
          <n-input
            v-model:value="localMd"
            type="textarea"
            placeholder="# 角色设定&#10;&#10;你是一个专业的 AI 助手...&#10;&#10;## 行为规范&#10;&#10;- 始终使用中文回答&#10;- 回答简洁准确"
            :resizable="false"
            style="height: 100%; font-family: 'JetBrains Mono', monospace; font-size: 14px;"
          />
        </div>
      </template>
      <template #2>
        <div style="padding: 16px; height: 100%; overflow-y: auto; background: var(--color-bg);">
          <n-text v-if="!localMd" depth="3">预览区域 - 在左侧编辑后实时预览</n-text>
          <div v-else class="md-preview" v-html="previewHtml" />
        </div>
      </template>
    </n-split>

    <div style="margin-top: 16px; text-align: right;">
      <n-button type="primary" @click="save">保存</n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { NSplit, NInput, NButton, NAlert, NText } from 'naive-ui'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { Agent } from '@/types'

const props = defineProps<{ agent: Agent | null }>()
const emit = defineEmits<{ save: [data: Partial<Agent>] }>()

const varHint = '{{user_name}}、{{current_time}}'
const localMd = ref(props.agent?.agentsMd || '')

const previewHtml = computed(() => {
  const raw = marked.parse(localMd.value, { async: false, breaks: true }) as string
  return DOMPurify.sanitize(raw)
})

watch(() => props.agent?.agentsMd, (val) => {
  if (val !== undefined) localMd.value = val
})

function save() {
  emit('save', { agentsMd: localMd.value })
}
</script>

<style scoped>
.md-preview {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text);
  word-break: break-word;
}
.md-preview :deep(h1),
.md-preview :deep(h2),
.md-preview :deep(h3),
.md-preview :deep(h4) {
  margin: 16px 0 8px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--text);
}
.md-preview :deep(h1) { font-size: 20px; }
.md-preview :deep(h2) { font-size: 17px; }
.md-preview :deep(h3) { font-size: 15px; }
.md-preview :deep(h4) { font-size: 14px; }
.md-preview :deep(h1:first-child),
.md-preview :deep(h2:first-child),
.md-preview :deep(h3:first-child) {
  margin-top: 0;
}
.md-preview :deep(p) {
  margin: 8px 0;
}
.md-preview :deep(ul),
.md-preview :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}
.md-preview :deep(li) {
  margin: 4px 0;
}
.md-preview :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--indigo-bg);
  color: var(--primary);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
}
.md-preview :deep(pre) {
  margin: 10px 0;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: #0F172A;
  overflow-x: auto;
}
.md-preview :deep(pre code) {
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: #E2E8F0;
}
.md-preview :deep(blockquote) {
  margin: 10px 0;
  padding: 6px 14px;
  border-left: 3px solid var(--primary-border);
  background: var(--surface-alt);
  color: var(--text-secondary);
}
.md-preview :deep(table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 13px;
}
.md-preview :deep(th),
.md-preview :deep(td) {
  padding: 6px 10px;
  border: 1px solid var(--border);
  text-align: left;
}
.md-preview :deep(th) {
  background: var(--surface-alt);
  font-weight: 600;
}
.md-preview :deep(hr) {
  margin: 14px 0;
  border: none;
  border-top: 1px solid var(--border);
}
.md-preview :deep(a) {
  color: var(--primary);
  text-decoration: none;
}
.md-preview :deep(a:hover) {
  text-decoration: underline;
}
</style>
