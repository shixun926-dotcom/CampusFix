import request from './request'
import { wsUrl } from '../config/env'

/* ---------------- 类型 ---------------- */

export type NotifyType = 'ORDER_STATUS' | 'CHAT' | 'COMPLAINT' | 'SYSTEM'

export interface NotificationVO {
  id: number
  type: NotifyType
  title: string
  content: string
  bizId?: number
  readFlag: number
  createdAt?: string
}

export interface NotificationPageVO {
  list: NotificationVO[]
  total: number
  unreadCount: number
}

export interface ChatMessageVO {
  id: number
  orderId: number
  senderId: number
  senderName: string
  receiverId: number
  content: string
  readFlag: number
  createdAt?: string
}

/* ---------------- 通知 REST ---------------- */

export const pageNotifications = (params: Record<string, any>) =>
  request.get('/notifications', { params }) as unknown as Promise<NotificationPageVO>

export const getUnreadCount = () =>
  request.get('/notifications/unread-count') as unknown as Promise<number>

export const markNotificationRead = (id: number | string) =>
  request.put(`/notifications/${id}/read`, {}) as unknown as Promise<void>

export const markAllNotificationsRead = () =>
  request.put('/notifications/read-all', {}) as unknown as Promise<number>

/* ---------------- 工单聊天 REST ---------------- */

export const listMessages = (orderId: number | string) =>
  request.get(`/orders/${orderId}/messages`) as unknown as Promise<ChatMessageVO[]>

export const sendMessage = (orderId: number | string, content: string) =>
  request.post(`/orders/${orderId}/messages`, { content }) as unknown as Promise<ChatMessageVO>

/* ---------------- 实时推送（WebSocket） ----------------
 *
 * 服务端 /ws 端点，握手鉴权用 query 参数带 access token。
 * 下行帧：{ type: 'NOTIFY' | 'CHAT' | 'CONNECTED' | 'PONG', payload }
 *   - NOTIFY：新通知（含聊天产生的通知），角标 +1
 *   - CHAT：聊天消息本体（工单聊天面板用它刷新气泡）
 *   - CHAT 会同时产生一条 NOTIFY 通知，角标只按 NOTIFY 计数，避免重复
 * 断线自动重连（2s 起步、15s 封顶），有心跳保活；连接失败静默降级——
 * 通知与消息都已落库，页面轮询兜底。
 */

export interface PushEvent {
  type: 'NOTIFY' | 'CHAT' | 'CONNECTED' | 'PONG'
  payload?: any
}

type PushListener = (event: PushEvent) => void

let socket: WebSocket | null = null
let retryCount = 0
let heartbeatTimer: number | undefined
const listeners = new Set<PushListener>()

/** 订阅服务端推送；返回取消订阅函数。首个订阅者触发建连。 */
export function onPush(listener: PushListener): () => void {
  listeners.add(listener)
  ensureConnected()
  return () => {
    listeners.delete(listener)
    // 无人订阅时挂断，登录页等场景不再保持连接
    if (listeners.size === 0) {
      closeSocket()
    }
  }
}

export function pushConnected(): boolean {
  return socket?.readyState === WebSocket.OPEN
}

function ensureConnected() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }
  const token = localStorage.getItem('cf_token')
  if (!token) {
    return
  }
  let next: WebSocket
  try {
    next = new WebSocket(`${wsUrl()}?token=${encodeURIComponent(token)}`)
  } catch {
    scheduleReconnect()
    return
  }
  socket = next

  next.onopen = () => {
    retryCount = 0
    heartbeatTimer = window.setInterval(() => {
      if (next.readyState === WebSocket.OPEN) next.send('PING')
    }, 25_000)
  }
  next.onmessage = (ev) => {
    try {
      const frame = JSON.parse(ev.data) as PushEvent
      listeners.forEach((fn) => fn(frame))
    } catch {
      /* 非 JSON 帧忽略 */
    }
  }
  next.onclose = () => {
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer)
      heartbeatTimer = undefined
    }
    if (socket === next) socket = null
    if (listeners.size > 0) scheduleReconnect()
  }
  next.onerror = () => next.close()
}

function scheduleReconnect() {
  window.setTimeout(ensureConnected, Math.min(15_000, 2_000 * ++retryCount))
}

function closeSocket() {
  if (heartbeatTimer) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
  if (socket) {
    const s = socket
    socket = null
    try {
      s.close()
    } catch {
      /* 已关闭 */
    }
  }
}
