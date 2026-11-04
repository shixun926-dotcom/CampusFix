import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'
import * as authApi from '../../api/auth'

vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  getProfile: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn()
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('正常流程', () => {
    it('登录成功：写入 token 与用户信息并持久化', async () => {
      vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok-1', refreshToken: 'ref-1' })
      vi.mocked(authApi.getProfile).mockResolvedValue({
        id: 1000,
        username: '1000',
        realName: '系统管理员',
        roles: ['ADMIN'],
        permissions: ['order:assign']
      })

      const store = useAuthStore()
      await store.login({ username: '1000', password: 'Cf@123456' })

      expect(store.token).toBe('tok-1')
      expect(store.user.username).toBe('1000')
      expect(localStorage.getItem('cf_token')).toBe('tok-1')
      expect(localStorage.getItem('cf_refresh')).toBe('ref-1')
      expect(localStorage.getItem('cf_user')).toContain('系统管理员')
      expect(authApi.login).toHaveBeenCalledWith({ username: '1000', password: 'Cf@123456' })
    })

    it('has / hasRole 权限判断正确', () => {
      const store = useAuthStore()
      store.$patch({
        user: { roles: ['ADMIN', 'MANAGER'], permissions: ['order:assign', 'order:cancel'] }
      })
      expect(store.hasRole('ADMIN')).toBe(true)
      expect(store.hasRole('STUDENT')).toBe(false)
      expect(store.has('order:assign')).toBe(true)
      expect(store.has('order:grab')).toBe(false)
    })

    it('displayName 优先取 realName，缺失回退 username', () => {
      const store = useAuthStore()
      store.$patch({ user: { realName: '张三', username: '1000' } })
      expect(store.displayName).toBe('张三')

      store.$patch({ user: { realName: '', username: '1000' } })
      expect(store.displayName).toBe('1000')

      store.$patch({ user: null })
      expect(store.displayName).toBe('')
    })

    it('logout 清理 token 与用户信息', async () => {
      vi.mocked(authApi.logout).mockResolvedValue(undefined)
      const store = useAuthStore()
      store.$patch({ token: 'tok-1', user: { username: '1000' } })
      localStorage.setItem('cf_token', 'tok-1')
      localStorage.setItem('cf_refresh', 'ref-1')
      localStorage.setItem('cf_user', '{"username":"1000"}')

      await store.logout()

      expect(store.token).toBe('')
      expect(store.user).toBeNull()
      expect(localStorage.getItem('cf_token')).toBeNull()
      expect(localStorage.getItem('cf_refresh')).toBeNull()
      expect(localStorage.getItem('cf_user')).toBeNull()
    })
  })

  describe('边界与异常', () => {
    it('登录失败：抛错且不写入 token', async () => {
      vi.mocked(authApi.login).mockRejectedValue(new Error('账号或密码错误'))

      const store = useAuthStore()
      await expect(store.login({ username: 'x', password: 'y' })).rejects.toThrow('账号或密码错误')
      expect(store.token).toBe('')
      expect(localStorage.getItem('cf_token')).toBeNull()
    })

    it('fetchProfile 失败时透传异常', async () => {
      vi.mocked(authApi.getProfile).mockRejectedValue(new Error('未登录'))
      const store = useAuthStore()
      await expect(store.fetchProfile()).rejects.toThrow('未登录')
    })

    it('logout 即使接口失败也清理本地状态', async () => {
      vi.mocked(authApi.logout).mockRejectedValue(new Error('令牌失效'))
      const store = useAuthStore()
      store.$patch({ token: 'tok-1', user: { username: '1000' } })

      await store.logout() // 不应抛错

      expect(store.token).toBe('')
      expect(store.user).toBeNull()
    })

    it('空 roles/permissions 时 has/hasRole 返回 false', () => {
      const store = useAuthStore()
      store.$patch({ user: { roles: [], permissions: [] } })
      expect(store.hasRole('ADMIN')).toBe(false)
      expect(store.has('order:assign')).toBe(false)
    })
  })
})
