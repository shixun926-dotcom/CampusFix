import request from './request'

/* ---------------- 类型 ---------------- */

export interface OperLogVO {
  id: number
  userId?: number
  username?: string
  module?: string
  operation?: string
  method?: string
  requestUri?: string
  /** 请求参数 JSON（已脱敏），查看详情时展示原文 */
  requestParam?: string
  ip?: string
  /** 1 成功 0 失败 */
  status: number
  errorMsg?: string
  costMs?: number
  createdAt?: string
}

export interface OperLogPage {
  list: OperLogVO[]
  total: number
  page: number
  size: number
}

/* ---------------- 接口 ---------------- */

export const listOperLogs = (params: Record<string, any>) =>
  request.get('/oper-logs', { params }) as unknown as Promise<OperLogPage>

export const listOperLogModules = () =>
  request.get('/oper-logs/modules') as unknown as Promise<string[]>
