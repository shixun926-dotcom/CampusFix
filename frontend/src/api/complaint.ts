import request from './request'
import type { ComplaintVO, EvaluationVO } from './order'

/**
 * 管理端：投诉列表与处理；维修工：评价汇总。
 */

export interface ComplaintPageVO {
  page: { list: ComplaintVO[]; total: number; page: number; size: number }
  pendingCount: number
  processingCount: number
  resolvedCount: number
  rejectedCount: number
  /** 投诉率百分比，如 8.33 */
  complaintRate: number
  types: string[]
}

export interface EvaluationSummaryVO {
  repairerId: number
  repairerName?: string
  total: number
  avgTotal: number
  avgSpeed: number
  avgAttitude: number
  avgEffect: number
  goodRate: number
}

export const pageComplaints = (params: Record<string, any>) =>
  request.get('/complaints', { params }) as unknown as Promise<ComplaintPageVO>

export const handleComplaint = (
  id: number | string,
  data: { status: string; result?: string }
) => request.put(`/complaints/${id}/handle`, data) as unknown as Promise<ComplaintVO>

export const pageRepairerEvaluations = (repairerId: number | string, params: Record<string, any>) =>
  request.get(`/repairers/${repairerId}/evaluations`, { params }) as unknown as Promise<{
    list: EvaluationVO[]
    total: number
  }>

export const getEvaluationSummary = (repairerId: number | string) =>
  request.get(`/repairers/${repairerId}/evaluation-summary`) as unknown as Promise<EvaluationSummaryVO>
