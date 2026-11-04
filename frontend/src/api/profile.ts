import request from './request'

/** 个人中心资料 */
export interface ProfileVO {
  id: number
  username: string
  realName: string
  staffNo: string
  phone: string
  avatar: string
  avatarUrl: string
  deptId: number | null
  deptName: string
  /** 是否已更换自定义头像（avatar 非空即 true），false 时前端展示默认头像 + 更新提醒 */
  hasAvatar: boolean
  /** 头像是否已逾期未更换 */
  avatarOverdue: boolean
  /** 头像强制更换截止时间 */
  avatarDeadline?: string
}

export interface ProfileUpdateRequest {
  phone: string
  avatar?: string
}

/** 信息更改申请 */
export interface ProfileChangeVO {
  id: number
  applicantId: number
  applicantName?: string
  applicantStaffNo?: string
  fieldKey: string
  fieldLabel: string
  oldValue?: string
  newValue: string
  reason: string
  status: string
  statusLabel: string
  reviewerId?: number
  reviewerName?: string
  rejectReason?: string
  reviewedAt?: string
  createdAt: string
}

export interface ProfileChangeSubmitRequest {
  fieldKey: string
  newValue: string
  reason: string
}

export interface ProfileChangeReviewRequest {
  status: string // APPROVED / REJECTED
  rejectReason?: string
}

export const getMyProfile = () => request.get('/profile') as Promise<ProfileVO>

export const updateMyProfile = (data: ProfileUpdateRequest) =>
  request.put('/profile', data) as Promise<ProfileVO>

export const submitProfileChange = (data: ProfileChangeSubmitRequest) =>
  request.post('/profile-changes', data) as Promise<ProfileChangeVO>

export const myProfileChanges = (page = 1, size = 20) =>
  request.get('/profile-changes/mine', { params: { page, size } }) as Promise<{
    list: ProfileChangeVO[]
    total: number
    current: number
    size: number
  }>

export const reviewProfileChangeList = (params: { status?: string; page?: number; size?: number }) =>
  request.get('/profile-changes/review', { params }) as Promise<{
    list: ProfileChangeVO[]
    total: number
    current: number
    size: number
  }>

export const reviewProfileChange = (id: number, data: ProfileChangeReviewRequest) =>
  request.post(`/profile-changes/${id}/review`, data) as Promise<ProfileChangeVO>

/** 上传头像（stage=AVATAR），返回 objectKey + url */
export const uploadAvatar = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  form.append('stage', 'AVATAR')
  return request.post('/files/upload', form) as Promise<{
    objectKey: string
    url: string
    fileName: string
    fileSize: number
    stage: string
  }>
}
