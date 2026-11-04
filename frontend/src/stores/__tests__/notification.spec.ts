import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '../notification'
import * as messageApi from '../../api/message'

vi.mock('../../api/message', () => ({
  getUnreadCount: vi.fn()
}))

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('正常流程', () => {
    it('fetchUnread 拉取服务端未读数并标记已初始化', async () => {
      vi.mocked(messageApi.getUnreadCount).mockResolvedValue(7)
      const store = useNotificationStore()
      await store.fetchUnread()

      expect(store.unreadCount).toBe(7)
      expect(store.initialized).toBe(true)
    })

    it('increment 未读 +1', () => {
      const store = useNotificationStore()
      store.unreadCount = 3
      store.increment()
      expect(store.unreadCount).toBe(4)
    })

    it('decrement 未读 -1', () => {
      const store = useNotificationStore()
      store.unreadCount = 3
      store.decrement()
      expect(store.unreadCount).toBe(2)
    })

    it('decrementBy 一次性减去 N 条', () => {
      const store = useNotificationStore()
      store.unreadCount = 10
      store.decrementBy(4)
      expect(store.unreadCount).toBe(6)
    })

    it('setUnread 以服务端权威值覆盖', () => {
      const store = useNotificationStore()
      store.setUnread(15)
      expect(store.unreadCount).toBe(15)
    })
  })

  describe('边界情况', () => {
    it('decrement 到 0 后不再为负', () => {
      const store = useNotificationStore()
      store.unreadCount = 1
      store.decrement()
      expect(store.unreadCount).toBe(0)
      store.decrement() // 再次 -1，应保持 0
      expect(store.unreadCount).toBe(0)
    })

    it('decrementBy 超过当前值时钳制到 0', () => {
      const store = useNotificationStore()
      store.unreadCount = 3
      store.decrementBy(10)
      expect(store.unreadCount).toBe(0)
    })

    it('setUnread 负数被钳制为 0', () => {
      const store = useNotificationStore()
      store.setUnread(-5)
      expect(store.unreadCount).toBe(0)
    })

    it('reset 清空未读与初始化标记', () => {
      const store = useNotificationStore()
      store.unreadCount = 9
      store.initialized = true
      store.reset()
      expect(store.unreadCount).toBe(0)
      expect(store.initialized).toBe(false)
    })
  })

  describe('异常输入', () => {
    it('fetchUnread 接口失败时保留现值并仍标记初始化', async () => {
      vi.mocked(messageApi.getUnreadCount).mockRejectedValue(new Error('网络异常'))
      const store = useNotificationStore()
      store.unreadCount = 5
      await store.fetchUnread()

      expect(store.unreadCount).toBe(5) // 保留现值
      expect(store.initialized).toBe(true) // 已尝试过初始化
    })

    it('fetchUnread 返回 null/undefined 时归一为 0', async () => {
      vi.mocked(messageApi.getUnreadCount).mockResolvedValue(undefined as any)
      const store = useNotificationStore()
      await store.fetchUnread()
      expect(store.unreadCount).toBe(0)
    })
  })
})
