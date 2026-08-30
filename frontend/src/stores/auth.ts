import { defineStore } from 'pinia'
import { loginApi, meApi } from '@/api/auth'

interface UserInfo { id: number; username: string; displayName: string; roles: string[] }

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: localStorage.getItem('ipproxy_access_token') || '', user: null as UserInfo | null }),
  getters: { loggedIn: (state) => Boolean(state.token) },
  actions: {
    async login(username: string, password: string) {
      const response = await loginApi(username, password)
      this.token = response.data.data.accessToken
      this.user = response.data.data.user
      localStorage.setItem('ipproxy_access_token', this.token)
    },
    async loadMe() {
      if (!this.token) return
      const response = await meApi()
      this.user = response.data.data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('ipproxy_access_token')
    }
  }
})
