import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import LoginView from '../LoginView.vue'
import * as authApi from '../../api/auth'

// mock API：login 由 auth store 调用
vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  getProfile: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn()
}))

// mock 路由
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: {} })
}))

// mock 轻提示，避免 setTimeout 悬挂
vi.mock('../../ui/toast', () => ({
  toastOk: vi.fn(),
  toastErr: vi.fn(),
  toast: vi.fn()
}))

function mountLogin() {
  return mount(LoginView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          stubActions: false // 让 auth.login 真实执行，走 mock 的 api
        })
      ],
      stubs: { Icon: true }
    }
  })
}

describe('LoginView 登录页交互', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('正常流程', () => {
    it('渲染账号/密码输入框与登录按钮', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('input[placeholder="学号 / 工号"]').exists()).toBe(true)
      expect(wrapper.find('input[placeholder="请输入密码"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('登 录')
    })

    it('登录成功后跳转到 dashboard 并提示成功', async () => {
      vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok', refreshToken: 'ref' })
      vi.mocked(authApi.getProfile).mockResolvedValue({
        id: 1000, username: '1000', realName: '系统管理员', roles: ['ADMIN'], permissions: []
      })

      const wrapper = mountLogin()
      await wrapper.find('input[placeholder="学号 / 工号"]').setValue('1000')
      await wrapper.find('input[placeholder="请输入密码"]').setValue('Cf@123456')
      await wrapper.find('button.btn-primary').trigger('click')
      await flushPromises()

      expect(authApi.login).toHaveBeenCalledWith({ username: '1000', password: 'Cf@123456' })
      expect(pushMock).toHaveBeenCalledWith('/dashboard')
    })

    it('不展示测试账号与预填凭据，仅提示账号由学校统一开通', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('.acc').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('测试账号')
      expect(wrapper.text()).toContain('账号由学校统一开通')
      const usernameInput = wrapper.find('input[placeholder="学号 / 工号"]')
      expect((usernameInput.element as HTMLInputElement).value).toBe('')
    })
  })

  describe('边界与异常输入', () => {
    it('空账号提交：提示「请输入学号或工号」且不调接口', async () => {
      const wrapper = mountLogin()
      await wrapper.find('input[placeholder="学号 / 工号"]').setValue('   ')
      await wrapper.find('button.btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('请输入学号或工号')
      expect(authApi.login).not.toHaveBeenCalled()
    })

    it('空密码提交：提示「请输入密码」', async () => {
      const wrapper = mountLogin()
      await wrapper.find('input[placeholder="学号 / 工号"]').setValue('1000')
      await wrapper.find('input[placeholder="请输入密码"]').setValue('')
      await wrapper.find('button.btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('请输入密码')
      expect(authApi.login).not.toHaveBeenCalled()
    })

    it('登录失败：显示后端错误信息', async () => {
      vi.mocked(authApi.login).mockRejectedValue({ message: '账号或密码错误' })

      const wrapper = mountLogin()
      await wrapper.find('input[placeholder="学号 / 工号"]').setValue('1000')
      await wrapper.find('input[placeholder="请输入密码"]').setValue('wrong')
      await wrapper.find('button.btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('账号或密码错误')
      expect(pushMock).not.toHaveBeenCalled()
    })

    it('登录失败且无 message：显示兜底文案', async () => {
      // 无 message 的异常对象，触发兜底文案分支
      vi.mocked(authApi.login).mockRejectedValue({})

      const wrapper = mountLogin()
      await wrapper.find('input[placeholder="学号 / 工号"]').setValue('1000')
      await wrapper.find('input[placeholder="请输入密码"]').setValue('x')
      await wrapper.find('button.btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('登录失败，请检查账号与密码')
    })

    it('登录失败：Error 对象透传其 message', async () => {
      vi.mocked(authApi.login).mockRejectedValue(new Error('网络异常'))

      const wrapper = mountLogin()
      await wrapper.find('input[placeholder="学号 / 工号"]').setValue('1000')
      await wrapper.find('input[placeholder="请输入密码"]').setValue('x')
      await wrapper.find('button.btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('网络异常')
    })

    it('密码可见性切换：点击眼睛按钮切换 input type', async () => {
      const wrapper = mountLogin()
      const pwdInput = wrapper.find('input[placeholder="请输入密码"]')
      expect(pwdInput.attributes('type')).toBe('password')

      await wrapper.find('button.eye').trigger('click')
      expect(wrapper.find('input[placeholder="请输入密码"]').attributes('type')).toBe('text')

      await wrapper.find('button.eye').trigger('click')
      expect(wrapper.find('input[placeholder="请输入密码"]').attributes('type')).toBe('password')
    })
  })
})
