import api from './api'
import type { Result } from '@/types'

interface LoginParams {
  email: string
  password: string
}

interface RegisterParams {
  email: string
  password: string
  nickname?: string
}

interface AuthData {
  token: string
  userId: string
  email: string
  nickname: string
  workspaceId: string
}

/** 登录 */
export function login(params: LoginParams) {
  return api.post<Result<AuthData>>('/api/auth/login', params)
}

/** 注册 */
export function register(params: RegisterParams) {
  return api.post<Result<AuthData>>('/api/auth/register', params)
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return api.get<Result<AuthData>>('/api/auth/me')
}
