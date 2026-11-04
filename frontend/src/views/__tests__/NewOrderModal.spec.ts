import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import NewOrderModal from '../NewOrderModal.vue'
import * as orderApi from '../../api/order'
import * as statsApi from '../../api/stats'

vi.mock('../../api/order', () => ({
  createOrder: vi.fn(),
  listCategories: vi.fn(),
  getLocationTree: vi.fn(),
  uploadImage: vi.fn()
}))

vi.mock('../../api/stats', () => ({
  listFacilities: vi.fn()
}))

vi.mock('../../config/env', () => ({
  resolveAssetUrl: (u?: string | null) => u || ''
}))

vi.mock('../../ui/toast', () => ({
  toastOk: vi.fn(),
  toastErr: vi.fn(),
  toast: vi.fn()
}))

const CATEGORIES = [
  { id: 1, name: '空调故障', code: 'AC', deptName: '空调组' },
  { id: 2, name: '水电故障', code: 'WATER', deptName: '水电组' }
]

const LOCATION_TREE = [
  {
    id: 100, parentId: 0, level: 1, name: '主校区', code: 'C01', type: 'CAMPUS',
    children: [
      {
        id: 101, parentId: 100, level: 2, name: '宿舍区', code: 'SS', type: 'AREA',
        children: [
          {
            id: 102, parentId: 101, level: 3, name: '1号宿舍楼', code: 'B02', type: 'BUILDING',
            children: [
              {
                id: 103, parentId: 102, level: 4, name: '2层', code: 'F03', type: 'FLOOR',
                children: [
                  { id: 104, parentId: 103, level: 5, name: '201', code: 'R301', type: 'ROOM', children: [] }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
]

// 关键：组件用 watch(() => props.open)（无 immediate）加载数据，
// 因此必须先以 open=false 挂载，再 setProps open=true 触发 watch。
async function mountModal() {
  const wrapper = mount(NewOrderModal, {
    props: { open: false },
    global: { stubs: { Icon: true } }
  })
  await wrapper.setProps({ open: true })
  await flushPromises()
  return wrapper
}

// 逐级选满位置（校区→区域→楼栋→楼层→房间）
async function selectFullLocation(wrapper: any) {
  const ids = [100, 101, 102, 103, 104]
  for (const id of ids) {
    const selects = wrapper.findAll('.loc-row select')
    const target = selects[selects.length - 1] // 每次都选当前最后一个下拉
    await target.setValue(String(id))
    await flushPromises()
  }
}

// 上传一张照片（模拟 pics 非空）
async function uploadOnePic(wrapper: any) {
  const fileInput = wrapper.find('input[type="file"]')
  const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' })
  Object.defineProperty(fileInput.element, 'files', { value: [file] })
  await fileInput.trigger('change')
  await flushPromises()
}

describe('NewOrderModal 提交报修交互', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(orderApi.listCategories).mockResolvedValue(CATEGORIES as any)
    vi.mocked(orderApi.getLocationTree).mockResolvedValue(LOCATION_TREE as any)
    vi.mocked(statsApi.listFacilities).mockResolvedValue([] as any)
    vi.mocked(orderApi.uploadImage).mockResolvedValue({
      objectKey: 'k1', url: '/files/k1', fileName: 'a.jpg', fileSize: 100
    } as any)
  })

  describe('正常流程', () => {
    it('打开后加载分类与位置树', async () => {
      const wrapper = await mountModal()
      expect(orderApi.listCategories).toHaveBeenCalled()
      expect(orderApi.getLocationTree).toHaveBeenCalled()
      expect(wrapper.text()).toContain('空调故障')
    })

    it('逐级选择位置后显示完整路径', async () => {
      const wrapper = await mountModal()
      await selectFullLocation(wrapper)
      expect(wrapper.text()).toContain('主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201')
    })

    it('紧急程度按钮可切换', async () => {
      const wrapper = await mountModal()
      const urgBtns = wrapper.findAll('.urg-btn')
      expect(urgBtns.length).toBe(3)
      expect(urgBtns[0].classes()).toContain('on')
      await urgBtns[2].trigger('click')
      expect(wrapper.findAll('.urg-btn')[2].classes()).toContain('on')
      expect(wrapper.findAll('.urg-btn')[0].classes()).not.toContain('on')
    })
  })

  describe('边界情况：六重校验链', () => {
    it('标题少于 4 字：提示「标题至少 4 个字」', async () => {
      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('短')
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('标题至少 4 个字')
      expect(orderApi.createOrder).not.toHaveBeenCalled()
    })

    it('未选故障类型：提示「请选择故障类型」', async () => {
      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('请选择故障类型')
    })

    it('未选位置：提示「请选择到具体的房间」', async () => {
      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('select').setValue('1')
      await wrapper.find('textarea').setValue('空调不制冷，需要维修，谢谢')
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('请选择到具体的房间或区域节点')
    })

    it('描述少于 10 字：提示「故障描述至少 10 个字」', async () => {
      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('select').setValue('1')
      await selectFullLocation(wrapper)
      await wrapper.find('textarea').setValue('太短')
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('故障描述至少 10 个字')
    })

    it('未上传照片：提示「请至少上传 1 张现场照片」', async () => {
      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('select').setValue('1')
      await selectFullLocation(wrapper)
      await wrapper.find('textarea').setValue('空调不制冷，需要维修，谢谢')
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('请至少上传 1 张现场照片')
    })

    it('未勾选隐私确认：提示「请先确认隐私提示」', async () => {
      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('select').setValue('1')
      await selectFullLocation(wrapper)
      await wrapper.find('textarea').setValue('空调不制冷，需要维修，谢谢')
      await uploadOnePic(wrapper)
      // 不勾隐私直接提交
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('请先确认隐私提示')
    })
  })

  describe('异常输入', () => {
    it('提交接口失败：显示后端错误信息', async () => {
      vi.mocked(orderApi.createOrder).mockRejectedValue({ message: '该位置已有重复报修' })

      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('select').setValue('1')
      await selectFullLocation(wrapper)
      await wrapper.find('textarea').setValue('空调不制冷，需要维修，谢谢')
      await uploadOnePic(wrapper)
      await wrapper.find('.privacy input').setValue(true)
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('该位置已有重复报修')
    })

    it('提交成功：调用 createOrder 并触发 created/close 事件', async () => {
      vi.mocked(orderApi.createOrder).mockResolvedValue({ id: 999, message: '已提交报修' } as any)

      const wrapper = await mountModal()
      await wrapper.find('input[placeholder*="一句话说明问题"]').setValue('空调不制冷了')
      await wrapper.find('select').setValue('1')
      await selectFullLocation(wrapper)
      await wrapper.find('textarea').setValue('空调不制冷，需要维修，谢谢')
      await uploadOnePic(wrapper)
      await wrapper.find('.privacy input').setValue(true)
      await wrapper.find('.btn-primary').trigger('click')
      await flushPromises()

      expect(orderApi.createOrder).toHaveBeenCalledTimes(1)
      const payload = vi.mocked(orderApi.createOrder).mock.calls[0][0] as any
      expect(payload.title).toBe('空调不制冷了')
      expect(payload.categoryId).toBe(1)
      expect(payload.locationId).toBe(104)
      expect(payload.images).toHaveLength(1)
      expect(wrapper.emitted('created')?.[0]).toEqual([999])
      expect(wrapper.emitted('close')).toBeTruthy()
    })
  })
})
