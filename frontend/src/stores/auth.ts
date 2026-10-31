import { defineStore } from 'pinia'
import { getProfile, login as loginApi, logout as logoutApi } from '../api/auth'

const TOKEN_KEY = 'cf_token'
const USER_KEY = 'cf_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    displayName: (state) => state.user?.realName || state.user?.username || '',
    roles: (state) => state.user?.roles || [],
    permissions: (state) => state.user?.permissions || []
  },
  actions: {
    async login(form: { username: string; password: string }) {
      const data: any = await loginApi(form)
      this.token = data.accessToken
      localStorage.setItem(TOKEN_KEY, data.accessToken)
      localStorage.setItem('cf_refresh', data.refreshToken)
      await this.fetchProfile()
      return data
    },
    async fetchProfile() {
      const profile: any = await getProfile()
      this.user = profile
      localStorage.setItem(USER_KEY, JSON.stringify(profile))
      return profile
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 令牌可能已失效，忽略即可
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('cf_refresh')
      localStorage.removeItem(USER_KEY)
    },
    has(perm: string) {
      return this.permissions.includes(perm)
    },
    hasRole(role: string) {
      return this.roles.includes(role)
    }
  }
})
