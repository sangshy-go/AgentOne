<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-logo">灵一</div>
      <h1 class="login-title">创建账号</h1>
      <p class="login-subtitle">加入 AgentOne · 开启 AI Agent 之旅</p>

      <n-form ref="formRef" :model="form" :rules="rules" style="margin-top: 28px;">
        <n-form-item label="昵称" path="nickname">
          <n-input v-model:value="form.nickname" placeholder="请输入昵称（可选）" size="large" />
        </n-form-item>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="form.email" placeholder="请输入邮箱" size="large" />
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码（至少 6 位）"
            size="large"
          />
        </n-form-item>
        <n-form-item label="确认密码" path="confirmPassword">
          <n-input
            v-model:value="form.confirmPassword"
            type="password"
            show-password-on="click"
            placeholder="请再次输入密码"
            size="large"
            @keyup.enter="handleRegister"
          />
        </n-form-item>
        <n-button
          type="primary"
          block
          size="large"
          :loading="loading"
          style="margin-top: 8px; height: 46px; font-size: 15px; font-weight: 700;"
          @click="handleRegister"
        >
          注册
        </n-button>
      </n-form>

      <div class="login-footer">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)
const form = reactive({
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value: string) => {
        return value === form.password
      },
      message: '两次密码输入不一致',
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await authStore.register(form.email, form.password, form.nickname || undefined)
    message.success('注册成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '注册失败'
    message.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #EEF2FF 0%, #F5F3FF 50%, #ECFEFF 100%);
  padding: 20px;
}

.login-card {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-xl), var(--glow-primary-md);
  padding: 48px 44px;
  width: 420px;
  max-width: 90vw;
  animation: modalIn 0.5s cubic-bezier(0.16, 1, 0.3, 1);
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--grad-primary);
}

.login-logo {
  width: 68px;
  height: 68px;
  border-radius: var(--radius-lg);
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  font-weight: 800;
  margin: 0 auto 22px;
  box-shadow: var(--glow-primary-md);
  animation: float 3s ease-in-out infinite;
}

.login-title {
  text-align: center;
  font-size: 26px;
  font-weight: 800;
  margin-bottom: 6px;
  letter-spacing: -0.8px;
  background: var(--grad-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.login-subtitle {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 8px;
  font-weight: 500;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: var(--text-muted);
}

.login-footer a {
  color: var(--primary);
  font-weight: 600;
}

.login-footer a:hover {
  color: var(--primary-hover);
}
</style>
