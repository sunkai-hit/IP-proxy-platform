import { http } from './http'

export const systemApi = {
  users: () => http.get('/system/users'),
  createUser: (data: any) => http.post('/system/users', data),
  updateUser: (id: number, data: any) => http.put(`/system/users/${id}`, data),
  resetPassword: (id: number, password: string) => http.post(`/system/users/${id}/reset-password`, { password }),
  roles: () => http.get('/system/roles'),
  createRole: (data: any) => http.post('/system/roles', data),
  updateRole: (id: number, data: any) => http.put(`/system/roles/${id}`, data),
  rolePermissions: (id: number) => http.get(`/system/roles/${id}/permissions`),
  saveRolePermissions: (id: number, ids: number[]) => http.put(`/system/roles/${id}/permissions`, { ids }),
  permissions: () => http.get('/system/permissions'),
  dictTypes: () => http.get('/system/dict-types'),
  createDictType: (data: any) => http.post('/system/dict-types', data),
  updateDictType: (id: number, data: any) => http.put(`/system/dict-types/${id}`, data),
  dictItems: (id: number) => http.get(`/system/dict-types/${id}/items`),
  createDictItem: (id: number, data: any) => http.post(`/system/dict-types/${id}/items`, data),
  updateDictItem: (id: number, data: any) => http.put(`/system/dict-items/${id}`, data),
  parameters: () => http.get('/system/parameters'),
  createParameter: (data: any) => http.post('/system/parameters', data),
  updateParameter: (id: number, data: any) => http.put(`/system/parameters/${id}`, data),
  loginLogs: () => http.get('/system/login-logs'),
  operationLogs: () => http.get('/system/operation-logs')
}
