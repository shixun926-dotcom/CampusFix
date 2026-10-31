import { reactive } from 'vue'

export type ToastType = 'info' | 'ok' | 'err'

export interface ToastItem {
  id: number
  msg: string
  type: ToastType
}

export const toastState = reactive<{ items: ToastItem[] }>({ items: [] })

let seq = 1

/** 轻提示：替代 ElMessage，样式与设计系统一致 */
export function toast(msg: string, type: ToastType = 'info', duration = 2600) {
  const id = seq++
  toastState.items.push({ id, msg, type })
  window.setTimeout(() => {
    const i = toastState.items.findIndex((t) => t.id === id)
    if (i >= 0) toastState.items.splice(i, 1)
  }, duration)
}

export const toastOk = (msg: string) => toast(msg, 'ok')
export const toastErr = (msg: string) => toast(msg, 'err')
