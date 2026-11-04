import { afterEach } from 'vitest'
import { enableAutoUnmount } from '@vue/test-utils'

// 每个测试用例后自动卸载挂载的组件，避免 DOM 泄漏与状态串扰
enableAutoUnmount(afterEach)

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

// jsdom 未实现 matchMedia，部分组件（若用到）会报错，这里兜底
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false
  })) as any
}
