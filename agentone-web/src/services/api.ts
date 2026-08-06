import axios from 'axios'
import type { Result } from '@/types'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: '',
  timeout: 30000,
  // S7: 通过 HttpOnly Cookie 传输 JWT，配合 withCredentials 自动携带，避免 XSS 窃取
  withCredentials: true,
})

// 请求拦截器：认证已由 Cookie 承载，不再注入 Authorization 头
api.interceptors.request.use((config) => {
  return config
})

// 响应拦截器：统一错误处理 + C2 无感刷新
let refreshPromise: Promise<boolean> | null = null

async function doRefresh(): Promise<boolean> {
  if (refreshPromise) return refreshPromise
  refreshPromise = api
    .post('/api/auth/refresh')
    .then(() => true)
    .catch(() => false)
    .finally(() => {
      refreshPromise = null
    })
  return refreshPromise
}

api.interceptors.response.use(
  (response) => {
    const data = response.data as Result
    if (data.code !== 0) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  async (error) => {
    const status = error.response?.status
    const original = error.config
    // refresh 接口自身 401 或已重试过 → 直接登出，避免死循环
    if (
      status === 401 &&
      original &&
      !(original as any)._retry &&
      original.url !== '/api/auth/refresh'
    ) {
      ;(original as any)._retry = true
      const ok = await doRefresh()
      if (ok) {
        // 用新 cookie 重试原请求
        return api(original)
      }
      // 刷新失败 → 统一登出
      const authStore = useAuthStore()
      authStore.clearSession()
      router.push('/login')
      return Promise.reject(error)
    }
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.clearSession()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default api
