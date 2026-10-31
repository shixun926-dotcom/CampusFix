import request from './request'

/* ---------------- 类型 ---------------- */

export interface OrderVO {
  id: number
  orderNo: string
  title: string
  status: string
  statusLabel: string
  categoryId: number
  categoryName?: string
  locationId: number
  locationPath: string
  buildingId?: number
  urgency: number
  priority: number
  reporterId: number
  reporterName?: string
  reporterPhone?: string
  assigneeId?: number
  assigneeName?: string
  assigneePhone?: string
  deptId?: number
  deptName?: string
  isWarranty?: number
  reworkFlag?: number
  reworkCount?: number
  autoAssigned?: number
  /** 是否由系统超时自动验收 */
  autoConfirmed?: boolean
  planTime?: string
  createdAt: string
  acceptedAt?: string
  finishedAt?: string
  confirmDeadline?: string
  actions?: string[]
}

export interface OrderDetail extends OrderVO {
  description: string
  facilityId?: number
  rejectReason?: string
  reviewAt?: string
  arrivedAt?: string
  startedAt?: string
  confirmedAt?: string
  images: { id: number; stage: string; objectKey: string; url: string; sort: number }[]
}

export interface OrderLog {
  id: number
  fromStatus?: string
  toStatus: string
  fromLabel?: string
  toLabel: string
  action: string
  operatorName: string
  operatorRole: string
  remark?: string
  createdAt: string
}

export interface RepairerVO {
  id: number
  realName: string
  staffNo?: string
  phone?: string
  deptId?: number
  deptName?: string
  wipCount: number
  creditScore: number
}

export interface CategoryVO {
  id: number
  name: string
  code: string
  deptId?: number
  deptName?: string
}

export interface LocationNode {
  id: number
  parentId: number
  level: number
  name: string
  code: string
  type: string
  children: LocationNode[]
}

/* ---------------- 工单 ---------------- */

export const pageOrders = (params: Record<string, any>) => request.get('/orders', { params })
export const pagePool = (params: Record<string, any>) => request.get('/orders/pool', { params })
export const getOrder = (id: number | string) => request.get(`/orders/${id}`)
export const getOrderLogs = (id: number | string) => request.get(`/orders/${id}/logs`)

export const createOrder = (data: Record<string, any>) => request.post('/orders', data)

export const reviewOrder = (id: number | string, remark?: string) =>
  request.post(`/orders/${id}/review`, { remark })
export const rejectOrder = (id: number | string, reason: string) =>
  request.post(`/orders/${id}/reject`, { reason })
export const cancelOrder = (id: number | string, reason?: string) =>
  request.post(`/orders/${id}/cancel`, { reason })

/* ---------------- 派单 ---------------- */

export const assignOrder = (id: number | string, repairerId: number, remark?: string) =>
  request.post(`/orders/${id}/assign`, { repairerId, remark })
export const transferOrder = (id: number | string, repairerId: number, remark?: string) =>
  request.post(`/orders/${id}/transfer`, { repairerId, remark })
export const grabOrder = (id: number | string) => request.post(`/orders/${id}/grab`)
export const acceptOrder = (id: number | string) => request.post(`/orders/${id}/accept`)
export const returnOrder = (id: number | string, reason?: string) =>
  request.post(`/orders/${id}/return`, { reason })

export const listRepairers = () => request.get('/repairers')

/* ---------------- 维修工日历 ---------------- */

export interface CalendarSlot {
  orderId: number
  orderTitle?: string
  slotStart?: string
  slotEnd?: string
}

export interface CalendarDay {
  date: string
  count: number
  slots: CalendarSlot[]
}

export interface CalendarVO {
  repairerId: number
  days: number
  windowStart: string
  windowEnd: string
  wipCount: number
  confirmedCount: number
  items: CalendarDay[]
}

/** 维修工未来 N 天已确认上门时段（指派/转派看忙闲；本人亦可查） */
export const getRepairerCalendar = (repairerId: number | string, days = 7) =>
  request.get(`/repairers/${repairerId}/calendar`, { params: { days } }) as unknown as Promise<CalendarVO>

/* ---------------- 基础数据 ---------------- */

export const listCategories = () => request.get('/categories')
export const getLocationTree = () => request.get('/locations/tree')

export const uploadImage = (file: File, stage = 'REPORT') => {
  const form = new FormData()
  form.append('file', file)
  form.append('stage', stage)
  return request.post('/files/upload', form)
}

/* ---------------- 预约 ---------------- */

export interface ScheduleVO {
  id: number
  orderId: number
  repairerId: number
  repairerName?: string
  planDate: string
  slotStart: string
  slotEnd: string
  status: string
  statusLabel: string
  proposedBy: string
  remark?: string
  /** 是否为改约产生的新提议 */
  reschedule: boolean
  /** 报修人当前可选中 */
  selectable: boolean
  /** 报修人当前可接受（改约提议） */
  acceptable: boolean
}

export interface SlotPayload {
  planDate: string
  slotStart: string
  slotEnd: string
  remark?: string
}

export interface RecordVO {
  id: number
  orderId: number
  repairerId?: number
  repairerName?: string
  arriveAt?: string
  startAt?: string
  endAt?: string
  content?: string
  material?: string
  result?: string
  remark?: string
  durationMinutes?: number
}

export const listSchedules = (orderId: number | string) =>
  request.get(`/orders/${orderId}/schedules`) as unknown as Promise<ScheduleVO[]>

export const offerSchedules = (orderId: number | string, slots: SlotPayload[]) =>
  request.post(`/orders/${orderId}/schedules`, { slots }) as unknown as Promise<ScheduleVO[]>

export const selectSchedule = (scheduleId: number | string) =>
  request.put(`/schedules/${scheduleId}/select`, {}) as unknown as Promise<ScheduleVO[]>

export const confirmSchedule = (scheduleId: number | string) =>
  request.put(`/schedules/${scheduleId}/confirm`, {}) as unknown as Promise<ScheduleVO[]>

export const rescheduleOrder = (orderId: number | string, slot: SlotPayload) =>
  request.post(`/orders/${orderId}/reschedule`, slot) as unknown as Promise<ScheduleVO[]>

export const acceptSchedule = (scheduleId: number | string) =>
  request.put(`/schedules/${scheduleId}/accept`, {}) as unknown as Promise<ScheduleVO[]>

export const rejectSchedule = (scheduleId: number | string, reason?: string) =>
  request.put(`/schedules/${scheduleId}/reject`, { reason }) as unknown as Promise<ScheduleVO[]>

/* ---------------- 维修过程 ---------------- */

export const getRecord = (orderId: number | string) =>
  request.get(`/orders/${orderId}/record`) as unknown as Promise<RecordVO | null>

export const arriveOrder = (orderId: number | string) =>
  request.post(`/orders/${orderId}/arrive`) as unknown as Promise<RecordVO>

export const startOrder = (orderId: number | string) =>
  request.post(`/orders/${orderId}/start`) as unknown as Promise<RecordVO>

export const saveRecord = (orderId: number | string, data: Record<string, any>) =>
  request.put(`/orders/${orderId}/record`, data) as unknown as Promise<RecordVO>

export const completeOrder = (orderId: number | string, data: Record<string, any>) =>
  request.post(`/orders/${orderId}/complete`, data) as unknown as Promise<RecordVO>

/** 挂起（必填原因，挂起时长不计入维修时长） */
export const suspendOrder = (orderId: number | string, reason: string) =>
  request.post(`/orders/${orderId}/suspend`, { reason }) as unknown as Promise<RecordVO>

/** 恢复（挂起 → 处理中） */
export const resumeOrder = (orderId: number | string) =>
  request.post(`/orders/${orderId}/resume`, {}) as unknown as Promise<RecordVO>

/** 判定无法维修（必填原因，工单转已驳回） */
export const unrepairableOrder = (orderId: number | string, reason: string) =>
  request.post(`/orders/${orderId}/unrepairable`, { reason }) as unknown as Promise<RecordVO>

/* ---------------- 验收与返修 ---------------- */

export interface ExtensionVO {
  id: number
  orderId: number
  studentId?: number
  studentName?: string
  reason: string
  status: string
  statusLabel: string
  handlerId?: number
  handlerName?: string
  handleResult?: string
  handledAt?: string
  createdAt?: string
}

export interface AcceptanceVO {
  orderId: number
  status: string
  statusLabel: string
  confirmDeadline?: string
  hoursLeft?: number
  overdue?: boolean
  autoConfirmed?: boolean
  autoConfirmDays?: number
  extensionDays?: number
  hasPendingExtension?: boolean
  extensions: ExtensionVO[]
}

export const getAcceptance = (orderId: number | string) =>
  request.get(`/orders/${orderId}/acceptance`) as unknown as Promise<AcceptanceVO>

export const confirmOrder = (orderId: number | string, remark?: string) =>
  request.post(`/orders/${orderId}/confirm`, { remark })

export const reworkOrder = (orderId: number | string, reason: string) =>
  request.post(`/orders/${orderId}/rework`, { reason })

export const applyExtension = (orderId: number | string, reason: string) =>
  request.post(`/orders/${orderId}/extension`, { reason }) as unknown as Promise<ExtensionVO>

export const handleExtension = (
  extensionId: number | string,
  data: { approve: boolean; result?: string; extraDays?: number }
) => request.put(`/extensions/${extensionId}/handle`, data) as unknown as Promise<ExtensionVO>

/* ---------------- 评价与投诉（工单内） ---------------- */

export interface EvaluationVO {
  id: number
  orderId: number
  orderNo?: string
  studentId?: number
  studentName?: string
  repairerId?: number
  repairerName?: string
  speedScore: number
  attitudeScore: number
  effectScore: number
  totalScore: number
  content?: string
  anonymous: boolean
  createdAt?: string
}

export interface ComplaintVO {
  id: number
  orderId: number
  orderNo?: string
  orderTitle?: string
  studentId?: number
  studentName?: string
  repairerId?: number
  repairerName?: string
  type: string
  typeLabel: string
  content: string
  status: string
  statusLabel: string
  handlerId?: number
  handlerName?: string
  handleResult?: string
  handledAt?: string
  createdAt?: string
}

export const evaluateOrder = (
  orderId: number | string,
  data: {
    speedScore: number
    attitudeScore: number
    effectScore: number
    content?: string
    anonymous?: boolean
  }
) => request.post(`/orders/${orderId}/evaluation`, data) as unknown as Promise<EvaluationVO>

export const getEvaluation = (orderId: number | string) =>
  request.get(`/orders/${orderId}/evaluation`) as unknown as Promise<EvaluationVO | null>

export const submitComplaint = (
  orderId: number | string,
  data: { type: string; content: string }
) => request.post(`/orders/${orderId}/complaint`, data) as unknown as Promise<ComplaintVO>

export const listOrderComplaints = (orderId: number | string) =>
  request.get(`/orders/${orderId}/complaints`) as unknown as Promise<ComplaintVO[]>
