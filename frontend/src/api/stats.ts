import request from './request'

/* ---------------- 类型 ---------------- */

export interface Completions {
  today: number
  week: number
  month: number
}

export interface TrendPoint {
  date: string
  count: number
}

export interface NameCount {
  name: string
  count: number
}

/** 工作台总览：数据范围按角色（管理员全局 / 后勤本部门 / 维修工与个人） */
export interface StatsOverview {
  total: number
  statusCounts: Record<string, number>
  completed: Completions
  avgHandleMinutes: number
  avgRepairMinutes: number
  trend: TrendPoint[]
  categoryTop: NameCount[]
  buildingTop: NameCount[]
}

export interface FaultAlertVO {
  id: number
  buildingId: number
  categoryId: number
  buildingName: string
  categoryName: string
  orderCount: number
  windowStart: string
  windowEnd: string
  status: 'NEW' | 'READ' | 'HANDLED'
  content: string
  createdAt?: string
}

export interface FacilityVO {
  id: number
  facilityNo: string
  name: string
  typeName?: string
  status: string
}

/* ---------------- 洞察（数据统计页，管理端） ---------------- */

export interface RepairerRank {
  repairerId: number
  realName: string
  deptName?: string
  completedCount: number
  /** 三维加权平均分（1-5），无评价为 null */
  avgScore: number | null
  /** 好评率（总分 ≥4 占比 %），无评价为 null */
  goodRatePercent: number | null
  avgHandleMinutes: number
}

export interface MonthPoint {
  /** MM 格式 */
  month: string
  total: number
  completed: number
}

export interface ComplaintSummary {
  total: number
  pending: number
  processing: number
  resolved: number
  rejected: number
  ratePercent: number
}

export interface StatsInsight {
  repairerRank: RepairerRank[]
  monthlyTrend: MonthPoint[]
  complaint: ComplaintSummary
}

/* ---------------- 统计 ---------------- */

export const getStatsOverview = () =>
  request.get('/stats/overview') as unknown as Promise<StatsOverview>

/** 数据统计页洞察（后勤本部门 / 管理员全局） */
export const getStatsInsight = () =>
  request.get('/stats/insight') as unknown as Promise<StatsInsight>

/* ---------------- 故障预警 ---------------- */

export const listFaultAlerts = (params?: Record<string, any>) =>
  request.get('/fault-alerts', { params }) as unknown as Promise<FaultAlertVO[]>

export const getNewAlertCount = () =>
  request.get('/fault-alerts/new-count') as unknown as Promise<number>

export const updateAlertStatus = (id: number | string, status: 'READ' | 'HANDLED') =>
  request.put(`/fault-alerts/${id}/status`, { status }) as unknown as Promise<void>

export const scanFaultAlerts = () =>
  request.post('/fault-alerts/scan', {}) as unknown as Promise<number>

/* ---------------- 设施档案 ---------------- */

export const listFacilities = (locationId: number | string) =>
  request.get('/facilities', { params: { locationId } }) as unknown as Promise<FacilityVO[]>
