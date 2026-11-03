import { defineStore } from 'pinia'
import { getUnreadCount } from '../api/message'

/**
 * 全局未读通知状态。
 *
 * <p>未读角标同时出现在「顶栏铃铛」「侧边栏消息中心」「消息中心页」三处，
 * 若各自维护局部 ref，会出现「某处点了已读、别处角标不变」的不同步问题。
 * 这里用单一 store 作为唯一事实来源，所有读写未读数量的地方都收敛到此。</p>
 */
export const useNotificationStore = defineStore('notification', {
  state: () => ({
    /** 当前未读通知总数 */
    unreadCount: 0,
    /** 是否已从服务端拉取过初始值（避免重复请求覆盖本地增量） */
    initialized: false
  }),
  getters: {
    unread: (state) => state.unreadCount
  },
  actions: {
    /** 从服务端拉取未读总数（登录后 / 首次进入时调用一次）。 */
    async fetchUnread() {
      try {
        this.unreadCount = (await getUnreadCount()) || 0
      } catch {
        /* 静默：拉取失败保留现值 */
      } finally {
        this.initialized = true
      }
    },
    /** 服务端推送一条新通知时，本地 +1。 */
    increment() {
      this.unreadCount += 1
    },
    /** 标记一条为已读时，本地 -1（下限 0，避免并发/重复标记导致负数）。 */
    decrement() {
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    },
    /** 一次性标记 N 条已读（全部已读）。 */
    decrementBy(n: number) {
      this.unreadCount = Math.max(0, this.unreadCount - n)
    },
    /** 以服务端返回的权威值覆盖（列表接口返回的 unreadCount 更精确时用）。 */
    setUnread(n: number) {
      this.unreadCount = Math.max(0, n)
    },
    /** 登出 / 重置时清空。 */
    reset() {
      this.unreadCount = 0
      this.initialized = false
    }
  }
})
