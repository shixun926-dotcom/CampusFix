import request from './request'

export const pageUsers = (params: Record<string, any>) => request.get('/users', { params })
export const getDeptTree = () => request.get('/depts/tree')
export const listRoles = () => request.get('/roles')
export const toggleUserStatus = (id: number | string, status: number) =>
  request.put(`/users/${id}/status?status=${status}`)
export const resetPassword = (id: number | string) => request.put(`/users/${id}/reset-password`)

/* ---------------- 用户详情与编辑 ---------------- */

export interface UserVO {
  id: number
  username: string
  realName: string
  studentNo?: string | null
  className?: string | null
  staffNo?: string | null
  deptId?: number | null
  deptName?: string | null
  phone?: string | null
  avatar?: string | null
  creditScore: number
  status: number
  roleCodes: string[]
  roleNames: string[]
  lastLoginAt?: string | null
  createdAt?: string | null
}

export const getUserDetail = (id: number | string) =>
  request.get(`/users/${id}`) as unknown as Promise<UserVO>

/**
 * 编辑用户基本信息（部分更新）。
 *
 * <p>语义：字段不传 = 不修改；`phone` / `className` 传空串 = 清除；
 * `deptId` 传 0 = 清除部门。后端据此拼装动态 SQL，只更新提交且有变化的列。</p>
 */
export interface UserBasicUpdatePayload {
  realName?: string
  phone?: string
  className?: string
  deptId?: number
  status?: number
  roleCodes?: string[]
  expectedUpdatedAt?: string
}

export const updateUserBasic = (id: number | string, data: UserBasicUpdatePayload) =>
  request.put(`/users/${id}/basic`, data) as unknown as Promise<UserVO>

/* ---------------- 学生导入 ---------------- */

export const createStudent = (data: {
  realName: string
  studentNo: string
  className: string
  phone?: string
}) => request.post('/users/students', data)

/** 下载导入模板（blob，不走统一响应解包） */
export const downloadStudentTemplate = () =>
  request.get('/users/import/template', { responseType: 'blob' })

export interface ImportRowError {
  rowNum: number
  studentNo: string
  realName: string
  reason: string
}

export interface ImportResult {
  total: number
  success: number
  failed: number
  errors: ImportRowError[]
}

export const importStudents = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/users/import', form) as Promise<ImportResult>
}
