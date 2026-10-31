import axios from 'axios'
import { API_BASE_URL } from '../config/env'
import { toast, toastErr } from '../ui/toast'
import router from '../router'

/**
 * 统一请求封装。
 *
 * <p>接口基地址来自 {@link API_BASE_URL}（见 `src/config/env.ts`）：
 * 开发环境是 `/api/v1`（Vite 代理到后端），生产可配成 `https://api.example.com/api/v1` 直连。</p>
 *
 * <p>后端响应体为 { code, message, data, traceId }，此处直接返回 data，
 * 业务代码拿到的就是纯数据；非 0 的 code 统一弹错并 reject。</p>
 */
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('cf_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    // 文件下载（blob）直接返回原始数据，不按 { code, data } 解包
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      toastErr(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body ? body.data : null
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const code = body?.code
    if (status === 401 || code === 1001 || code === 1002) {
      localStorage.removeItem('cf_token')
      localStorage.removeItem('cf_refresh')
      localStorage.removeItem('cf_user')
      if (router.currentRoute.value.path !== '/login') {
        toast('登录已失效，请重新登录')
        router.push('/login')
      }
    } else if (status === 403 || code === 2001) {
      toastErr('没有该操作权限')
    } else {
      toastErr(body?.message || error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
