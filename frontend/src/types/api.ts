export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId: string
  timestamp: number
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: { id: number; username: string; displayName: string; roles: string[]; permissions: string[] }
}
