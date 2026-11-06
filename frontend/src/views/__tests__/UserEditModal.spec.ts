import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import UserEditModal from '../UserEditModal.vue'
import * as systemApi from '../../api/system'

// 接口层整体 mock：详情、角色、部门、保存
vi.mock('../../api/system', () => ({
  getUserDetail: vi.fn(),
  listRoles: vi.fn(),
  getDeptTree: vi.fn(),
  updateUserBasic: vi.fn()
}))

vi.mock('../../ui/dialog', () => ({
  confirmDialog: vi.fn().mockResolvedValue(true)
}))

vi.mock('../../ui/toast', () => ({
  toastOk: vi.fn(),
  toastErr: vi.fn(),
  toast: vi.fn()
}))

const STU = {
  id: 9004,
  username: 'demo_stu',
  realName: '陈晓',
  studentNo: 'D2026099',
  staffNo: null,
  className: '计算机 2026-1 班',
  deptId: null,
  deptName: null,
  phone: '13700009004',
  creditScore: 91,
  status: 1,
  roleCodes: ['STUDENT'],
  roleNames: ['学生'],
  lastLoginAt: '2026-11-06 09:12:33'
}

const REPAIRER = {
  ...STU,
  id: 9003,
  username: 'demo_rep',
  realName: '张建国',
  studentNo: null,
  staffNo: 'D0003',
  className: null,
  deptId: 2,
  deptName: '水电维修组',
  roleCodes: ['REPAIRER'],
  roleNames: ['维修人员']
}

function mountModal() {
  return mount(UserEditModal, {
    props: { open: true, userId: 9004 },
    global: { stubs: { Icon: true } }
  })
}

describe('UserEditModal 编辑用户', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(systemApi.listRoles).mockResolvedValue([
      { code: 'STUDENT', name: '学生', status: 1 },
      { code: 'REPAIRER', name: '维修人员', status: 1 },
      { code: 'MANAGER', name: '后勤管理员', status: 1 },
      { code: 'ADMIN', name: '系统管理员', status: 1 }
    ] as any)
    vi.mocked(systemApi.getDeptTree).mockResolvedValue([
      { id: 1, name: '后勤保障处', children: [{ id: 2, name: '水电维修组', children: [] }] }
    ] as any)
    vi.mocked(systemApi.getUserDetail).mockResolvedValue(STU as any)
  })

  it('打开后加载详情：只读信息与学生班级正确回填', async () => {
    const wrapper = mountModal()
    await flushPromises()

    expect(systemApi.getUserDetail).toHaveBeenCalledWith(9004)
    expect(wrapper.text()).toContain('demo_stu')
    expect(wrapper.text()).toContain('D2026099')
    expect(wrapper.text()).toContain('91')

    const classInput = wrapper.find('input[placeholder="如 计算机 2026-1 班"]')
    expect((classInput.element as HTMLInputElement).value).toBe('计算机 2026-1 班')
    // 学生不显示部门下拉
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('教职工显示部门下拉且不显示班级', async () => {
    vi.mocked(systemApi.getUserDetail).mockResolvedValue(REPAIRER as any)
    const wrapper = mount(UserEditModal, {
      props: { open: true, userId: 9003 },
      global: { stubs: { Icon: true } }
    })
    await flushPromises()

    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="如 计算机 2026-1 班"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('D0003')
  })

  it('手机号格式非法：内联报错且不调用保存接口', async () => {
    const wrapper = mountModal()
    await flushPromises()

    await wrapper.find('input[placeholder="留空表示清除"]').setValue('12345')
    await wrapper.find('.btn-primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('手机号格式不正确')
    expect(systemApi.updateUserBasic).not.toHaveBeenCalled()
  })

  it('姓名为空：拒绝提交', async () => {
    const wrapper = mountModal()
    await flushPromises()

    await wrapper.find('input[placeholder="如 张建国"]').setValue('   ')
    await wrapper.find('.btn-primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入姓名')
    expect(systemApi.updateUserBasic).not.toHaveBeenCalled()
  })

  it('保存学生：payload 带班级且 deptId=0（清除部门）', async () => {
    vi.mocked(systemApi.updateUserBasic).mockResolvedValue({ ...STU, realName: '陈晓晓' } as any)
    const wrapper = mountModal()
    await flushPromises()

    await wrapper.find('input[placeholder="如 张建国"]').setValue('陈晓晓')
    await wrapper.find('.btn-primary').trigger('click')
    await flushPromises()

    expect(systemApi.updateUserBasic).toHaveBeenCalledWith(9004, expect.objectContaining({
      realName: '陈晓晓',
      roleCodes: ['STUDENT'],
      className: '计算机 2026-1 班',
      deptId: 0,
      status: 1
    }))
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('保存教职工：payload 带部门且清空班级', async () => {
    vi.mocked(systemApi.getUserDetail).mockResolvedValue(REPAIRER as any)
    vi.mocked(systemApi.updateUserBasic).mockResolvedValue(REPAIRER as any)
    const wrapper = mount(UserEditModal, {
      props: { open: true, userId: 9003 },
      global: { stubs: { Icon: true } }
    })
    await flushPromises()

    await wrapper.find('.btn-primary').trigger('click')
    await flushPromises()

    expect(systemApi.updateUserBasic).toHaveBeenCalledWith(9003, expect.objectContaining({
      roleCodes: ['REPAIRER'],
      className: '',
      deptId: 2
    }))
  })

  it('保存失败：展示后端错误且保持弹窗打开', async () => {
    vi.mocked(systemApi.updateUserBasic).mockRejectedValue({ message: '系统至少保留一名启用的系统管理员' })
    const wrapper = mountModal()
    await flushPromises()

    await wrapper.find('.btn-primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('系统至少保留一名启用的系统管理员')
    expect(wrapper.emitted('close')).toBeFalsy()
  })
})
