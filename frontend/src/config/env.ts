/**
 * 前端运行时配置：**所有对外地址只在这里出现**。
 *
 * <p>前后端分离后，前端可能被部署在 www.example.com，后端在 api.example.com，
 * 因此接口地址、静态资源地址都不能写死。通过 Vite 环境变量注入：</p>
 *
 * <pre>
 * # 开发（vite dev，走代理，保持同源，无需后端开跨域）
 * VITE_API_BASE_URL=/api/v1
 * VITE_ASSET_BASE_URL=
 * VITE_PROXY_TARGET=http://127.0.0.1:8080
 *
 * # 生产（前后端不同域）
 * VITE_API_BASE_URL=https://api.example.com/api/v1
 * VITE_ASSET_BASE_URL=https://api.example.com
 * </pre>
 */

const env = import.meta.env as Record<string, string | undefined>

function trimSlash(s: string): string {
  return s.replace(/\/+$/, '')
}

/** 接口基地址：以 / 开头表示与本页面同源，以 http 开头表示直连后端域。 */
export const API_BASE_URL: string = (env.VITE_API_BASE_URL || '/api/v1').trim()

/** 上传图片等静态资源的基地址；留空表示与页面同源。 */
export const ASSET_BASE_URL: string = trimSlash((env.VITE_ASSET_BASE_URL || '').trim())

/**
 * 后端源站。用于拼 Swagger、健康检查等不属于 /api/v1 的地址。
 * 当 API 基地址是同源相对路径时，返回空串（调用方按当前 origin 处理）。
 */
export const SERVER_ORIGIN: string = (() => {
  if (/^https?:\/\//i.test(API_BASE_URL)) {
    return trimSlash(API_BASE_URL.replace(/\/api\/v\d+\/?$/i, ''))
  }
  return ''
})()

/** 后端是否与前端不同源（用于提示与调试）。 */
export const IS_CROSS_ORIGIN = /^https?:\/\//i.test(API_BASE_URL)

/**
 * 把后端返回的资源地址解析成浏览器可直接访问的 URL。
 *
 * <p>后端在本地存储模式下返回 `/files/...` 相对路径，跨域部署时这个路径会打到前端域上，
 * 所以这里统一补上资源基地址（或后端源站）。已经是绝对地址（OSS/CDN）则原样返回。</p>
 */
export function resolveAssetUrl(url?: string | null): string {
  if (!url) {
    return ''
  }
  if (/^(https?:)?\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url
  }
  const base = ASSET_BASE_URL || SERVER_ORIGIN
  if (!base) {
    return url
  }
  return trimSlash(base) + (url.startsWith('/') ? url : '/' + url)
}

/** 接口文档地址（后端 Swagger UI）。 */
export function swaggerUrl(): string {
  const origin = SERVER_ORIGIN || window.location.origin
  return `${origin}/swagger-ui.html`
}

/**
 * 站内消息 WebSocket 地址。浏览器 WebSocket API 不能带 Authorization 头，
 * token 由调用方以 query 参数拼接：`<wsUrl()>?token=...`。
 */
export function wsUrl(): string {
  const origin = SERVER_ORIGIN || window.location.origin
  return origin.replace(/^http/i, 'ws') + '/ws'
}
