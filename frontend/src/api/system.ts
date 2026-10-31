import request from './request'

export const pageUsers = (params: Record<string, any>) => request.get('/users', { params })
export const getDeptTree = () => request.get('/depts/tree')
export const listRoles = () => request.get('/roles')
export const toggleUserStatus = (id: number | string, status: number) =>
  request.put(`/users/${id}/status?status=${status}`)
export const resetPassword = (id: number | string) => request.put(`/users/${id}/reset-password`)

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
