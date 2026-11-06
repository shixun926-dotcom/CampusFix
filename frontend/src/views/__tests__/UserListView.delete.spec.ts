import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import UserListView from '../UserListView.vue'
import * as systemApi from '../../api/system'

/* 确认弹窗与轻提示都换成可控替身，避免真实定时器与全局状态 */
const { confirmMock, toastOkMock, toastErrMock } = vi.hoisted(() => ({
  confirmMock: vi.fn(),
  toastOkMock: vi.fn(),
  toastErrMock: vi.fn()
}))

vi.mock('../../ui/dialog', () => ({ confirmDialog: confirmMock }))
vi.mock('../../ui/toast', () => ({
  toast: vi.fn(),
  toastOk: toastOkMock,
  toastErr: toastErrMock
}))

vi.mock('../../api/system', () => ({
  pageUsers: vi.fn(),
  deleteUser: vi.fn(),
  deleteUsersBatch: vi.fn(),
  getDeptTree: vi.fn(),
  listRoles: vi.fn(),
  toggleUserStatus: vi.fn(),
  resetPassword: vi.fn(),
  createStudent: vi.fn(),
  importStudents: vi.fn(),
  downloadStudentTemplate: vi.fn(),
  getUserDetail: vi.fn(),
  updateUserBasic: vi.fn()
}))

// 只覆盖路由钩子，保留其余导出（request.ts 会引入 router 实例）
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    useRoute: () => ({ query: {}, path: '/users' }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
  }
})

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({
    has: () => true,
    hasRole: () => true,
    displayName: '管理员',
    roles: ['ADMIN'],
    user: { userId: 99, id: 99, username: 'admin' }
  })
}))

const SELF = {
  id: 99,
  username: 'admin',
  realName: '林振华',
  status: 1,
  roleCodes: ['ADMIN'],
  roleNames: ['系统管理员'],
  creditScore: 100
}

function member(id: number, realName: string) {
  return {
    id,
    username: `u${id}`,
    realName,
    status: 1,
    roleCodes: ['STUDENT'],
    roleNames: ['学生'],
    creditScore: 100,
    className: '计算机2201'
  }
}

const LIST = [SELF, member(1, '陈晓'), member(2, '李雷')]

function mountView() {
  return mount(UserListView, {
    global: { stubs: { Icon: true, UserEditModal: true } }
  })
}

/** 等待挂载时的首次列表请求完成 */
async function ready() {
  const wrapper = mountView()
  await flushPromises()
  return wrapper
}

describe('用户管理 · 删除与批量删除', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmMock.mockResolvedValue(true)
    ;(systemApi.pageUsers as any).mockResolvedValue({ list: LIST, total: LIST.length })
    ;(systemApi.deleteUser as any).mockResolvedValue(undefined)
    ;(systemApi.deleteUsersBatch as any).mockResolvedValue({ success: 2, failed: [] })
  })

  it('未勾选时不显示批量删除工具条', async () => {
    const wrapper = await ready()
    expect(wrapper.find('.bulk-bar').exists()).toBe(false)
  })

  it('勾选多行后出现批量删除，确认后按 id 提交', async () => {
    const wrapper = await ready()
    const boxes = wrapper.findAll('tbody .chk')
    expect(boxes).toHaveLength(3)

    await boxes[1].trigger('click')
    await boxes[2].trigger('click')

    expect(wrapper.find('.bulk-cnt').text()).toContain('2')
    await wrapper.find('.bulk-bar .btn-danger').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(systemApi.deleteUsersBatch).toHaveBeenCalledWith([1, 2])
    expect(toastOkMock).toHaveBeenCalledWith('已删除 2 名成员')
  })

  it('全选只勾选可删除成员，当前登录账号被排除', async () => {
    const wrapper = await ready()
    await wrapper.find('thead .chk').trigger('click')

    expect(wrapper.find('.bulk-cnt').text()).toContain('2')
    await wrapper.find('.bulk-bar .btn-danger').trigger('click')
    await flushPromises()

    // 99 是当前登录账号，不应出现在提交列表中
    expect(systemApi.deleteUsersBatch).toHaveBeenCalledWith([1, 2])
  })

  it('单条删除：确认后调接口并把该行移出列表', async () => {
    const wrapper = await ready()
    await wrapper.findAll('tbody .del-btn')[1].trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledWith(
      expect.objectContaining({ title: '删除成员', danger: true })
    )
    expect(systemApi.deleteUser).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
  })

  it('取消确认时不调用删除接口', async () => {
    confirmMock.mockResolvedValue(false)
    const wrapper = await ready()
    await wrapper.findAll('tbody .del-btn')[1].trigger('click')
    await flushPromises()

    expect(systemApi.deleteUser).not.toHaveBeenCalled()
    expect(wrapper.findAll('tbody tr')).toHaveLength(3)
  })

  it('批量删除存在跳过项时，弹窗逐条说明原因', async () => {
    ;(systemApi.deleteUsersBatch as any).mockResolvedValue({
      success: 1,
      failed: [{ id: 2, name: '李雷（u2）', reason: '仍有 2 张未完结工单，请先转派或办结' }]
    })
    const wrapper = await ready()
    await wrapper.find('thead .chk').trigger('click')
    await wrapper.find('.bulk-bar .btn-danger').trigger('click')
    await flushPromises()

    expect(wrapper.find('.del-reason').text()).toContain('未完结工单')
    expect(toastOkMock).not.toHaveBeenCalled()
  })

  it('当前登录账号的删除按钮被禁用', async () => {
    const wrapper = await ready()
    const selfRowBtn = wrapper.findAll('tbody .del-btn')[0]
    expect(selfRowBtn.attributes('disabled')).toBeDefined()
  })
})
