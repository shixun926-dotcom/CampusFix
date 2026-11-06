import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import OrderListView from '../OrderListView.vue'
import * as orderApi from '../../api/order'

vi.mock('../../api/order', () => ({
  listCategories: vi.fn(),
  pageOrders: vi.fn(),
  pagePool: vi.fn()
}))

// 只覆盖路由钩子，保留 createRouter 等导出（request.ts 会引入 router 实例）
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    useRoute: () => ({ query: {}, path: '/orders' }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
  }
})

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({
    has: () => true,
    hasRole: (r: string) => r === 'ADMIN',
    displayName: '管理员',
    roles: ['ADMIN'],
    user: {}
  })
}))

function row(id: number, status: string, statusLabel: string) {
  return {
    id,
    orderNo: `DM${id}`,
    title: `工单${id}`,
    status,
    statusLabel,
    categoryName: '水电故障',
    locationPath: '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 101',
    createdAt: '2026-11-06 10:00:00',
    urgency: 1,
    isWarranty: false,
    reworkFlag: false
  }
}

const PENDING = row(1, 'PENDING', '待处理')
const PROCESSING = row(2, 'PROCESSING', '处理中')

function mountView() {
  return mount(OrderListView, {
    global: {
      stubs: { Icon: true, OrderDetailDrawer: true, NewOrderModal: true, OrderStatusTag: true }
    }
  })
}

describe('工单列表 · 本页状态筛选', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(orderApi.listCategories).mockResolvedValue([] as any)
    vi.mocked(orderApi.pagePool).mockResolvedValue({ list: [], total: 0 } as any)
    // 未按状态筛选返回两条；按状态筛选只返回对应的一条（模拟后端真实行为）
    vi.mocked(orderApi.pageOrders).mockImplementation(async (p: any) => {
      if (p?.status) {
        const hit = [PENDING, PROCESSING].filter((r) => r.status === p.status)
        return { list: hit, total: hit.length } as any
      }
      return { list: [PENDING, PROCESSING], total: 2 } as any
    })
  })

  it('初次加载展示全部状态选项', async () => {
    const wrapper = mountView()
    await flushPromises()

    const chips = wrapper.findAll('.strip-chip')
    // 第一个是「全部」，其余为各状态
    expect(chips.length).toBe(3)
    expect(wrapper.text()).toContain('待处理')
    expect(wrapper.text()).toContain('处理中')
  })

  it('点击单个状态后，其余状态选项仍然可见可切换（回归用例）', async () => {
    const wrapper = mountView()
    await flushPromises()

    const pendingChip = wrapper.findAll('.strip-chip').find((c) => c.text().includes('待处理'))!
    await pendingChip.trigger('click')
    await flushPromises()

    // 关键断言：筛选后接口只返回 1 条，但选项仍应为 3 个（全部 + 2 状态）
    expect(wrapper.findAll('.strip-chip').length).toBe(3)
    // 被点选项处于选中态
    expect(pendingChip.classes()).toContain('on')
    // 请求确实带上了状态参数
    expect(vi.mocked(orderApi.pageOrders).mock.calls.at(-1)![0]).toMatchObject({ status: 'PENDING' })
  })

  it('筛选状态下「全部」计数仍取未筛选值，不随筛选缩小', async () => {
    const wrapper = mountView()
    await flushPromises()

    const allChip = wrapper.findAll('.strip-chip')[0]
    expect(allChip.text()).toContain('2')

    const pendingChip = wrapper.findAll('.strip-chip').find((c) => c.text().includes('待处理'))!
    await pendingChip.trigger('click')
    await flushPromises()

    // 筛选后列表只剩 1 条，但「全部」仍显示筛选前的 2
    expect(wrapper.findAll('.strip-chip')[0].text()).toContain('2')
  })

  it('点击「全部」可恢复完整列表', async () => {
    const wrapper = mountView()
    await flushPromises()

    const pendingChip = wrapper.findAll('.strip-chip').find((c) => c.text().includes('待处理'))!
    await pendingChip.trigger('click')
    await flushPromises()

    const allChip = wrapper.findAll('.strip-chip')[0]
    await allChip.trigger('click')
    await flushPromises()

    expect(allChip.classes()).toContain('on')
    expect(vi.mocked(orderApi.pageOrders).mock.calls.at(-1)![0]).toMatchObject({ status: undefined })
    expect(wrapper.findAll('.strip-chip').length).toBe(3)
  })
})
