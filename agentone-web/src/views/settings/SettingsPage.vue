<template>
  <div class="page-container">
    <h1 class="page-title">工作空间设置</h1>
    <p class="page-desc">管理你的工作空间信息和成员</p>

    <div class="page-card" style="margin-bottom: 24px;">
      <div class="section-title">基本信息</div>
      <n-form label-placement="left" label-width="120" style="max-width: 600px;">
        <n-form-item label="工作空间名称">
          <n-input placeholder="输入工作空间名称" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input type="textarea" placeholder="输入工作空间描述" :rows="3" />
        </n-form-item>
        <n-form-item>
          <button class="btn-gradient">保存</button>
        </n-form-item>
      </n-form>
    </div>

    <div class="page-card">
      <div class="section-title">成员管理</div>
      <div class="empty-hint">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" style="color: var(--primary); opacity: 0.4;">
          <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 00-3-3.87" />
          <path d="M16 3.13a4 4 0 010 7.75" />
        </svg>
        <p style="font-size: 13px; color: var(--text-muted); margin-top: 12px;">成员管理功能将在后续迭代中完善</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { NForm, NFormItem, NInput } from 'naive-ui'
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}

.empty-hint {
  text-align: center;
  padding: 40px 24px;
}
</style>
