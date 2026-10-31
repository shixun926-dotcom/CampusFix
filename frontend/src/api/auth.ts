import request from './request'

export interface LoginForm {
  username: string
  password: string
}

export const login = (data: LoginForm) => request.post('/auth/login', data)
export const getProfile = () => request.get('/auth/me')
export const logout = () => request.post('/auth/logout')
export const changePassword = (data: { oldPassword: string; newPassword: string }) =>
  request.put('/auth/password', data)
