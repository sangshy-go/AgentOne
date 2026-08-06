<template>
  <n-config-provider
    :locale="zhCN"
    :date-locale="dateZhCN"
    :theme="theme"
    :theme-overrides="themeOverrides"
  >
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  zhCN,
  dateZhCN,
  darkTheme,
  type GlobalThemeOverrides,
} from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const theme = computed(() => (authStore.isDarkMode ? darkTheme : null))

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#6366F1',
    primaryColorHover: '#4F46E5',
    primaryColorPressed: '#4338CA',
    primaryColorSuppl: '#6366F1',
    borderRadius: '12px',
    borderRadiusSmall: '8px',
    fontFamily: "'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif",
  },
  Card: {
    borderRadius: '20px',
    borderColor: '#E2E8F0',
    color: '#FFFFFF',
    paddingMedium: '24px',
  },
  Button: {
    borderRadiusMedium: '12px',
    borderRadiusSmall: '8px',
    fontWeight: '700',
  },
  Input: {
    borderRadius: '12px',
    border: '1.5px solid #E2E8F0',
    borderHover: '1.5px solid #6366F1',
    borderFocus: '1.5px solid #6366F1',
    boxShadowFocus: '0 0 0 4px rgba(99, 102, 241, 0.15)',
  },
  Tag: {
    borderRadius: '20px',
  },
  DataTable: {
    borderRadius: '20px',
    thColor: '#F8FAFF',
    thFontWeight: '700',
    thTextColor: '#64748B',
    fontSizeMedium: '13.5px',
  },
  Tabs: {
    tabFontWeightActive: '700',
    barColor: '#6366F1',
    tabTextColorLine: '#475569',
    tabTextColorActiveLine: '#6366F1',
    tabTextColorHoverLine: '#6366F1',
  },
  Menu: {
    borderRadius: '12px',
    itemColorHover: '#F5F3FF',
    itemColorActive: '#EEF2FF',
    itemTextColorActive: '#6366F1',
    itemTextColorActiveHover: '#6366F1',
    itemIconColorActive: '#6366F1',
  },
  Modal: {
    borderRadius: '28px',
  },
  Statistic: {
    labelFontWeight: '600',
    labelTextColor: '#475569',
  },
}
</script>
