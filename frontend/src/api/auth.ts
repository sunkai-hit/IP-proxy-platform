import { http } from './http'
import type { ApiResponse, LoginResponse } from '@/types/api'

export const loginApi = (username: string, password: string) =>
  http.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })

export const meApi = () => http.get<ApiResponse<LoginResponse['user']>>('/auth/me')
