import { http } from './http'

export const customerApi = {
  list: (params:any) => http.get('/customers',{params}),
  options: () => http.get('/customers/options'),
  detail: (id:number|string) => http.get(`/customers/${id}`),
  services: (id:number|string) => http.get(`/customers/${id}/services`),
  credentials: (id:number|string) => http.get(`/customers/${id}/credentials`),
  usage: (id:number|string) => http.get(`/customers/${id}/usage`),
  create: (data:any) => http.post('/customers',data),
  update: (id:number,data:any) => http.put(`/customers/${id}`,data),
  freeze: (id:number,reason:string) => http.post(`/customers/${id}/freeze`,{reason}),
  resume: (id:number,reason:string) => http.post(`/customers/${id}/resume`,{reason}),
  disable: (id:number,reason:string) => http.post(`/customers/${id}/disable`,{reason}),
  createAuth: (id:number,data:any) => http.post(`/customers/${id}/auth`,data),
  authList: (params:any) => http.get('/customer-auth',{params}),
  authDetail: (id:number) => http.get(`/customer-auth/${id}`),
  approveAuth: (id:number,opinion:string) => http.post(`/customer-auth/${id}/approve`,{opinion}),
  rejectAuth: (id:number,opinion:string) => http.post(`/customer-auth/${id}/reject`,{opinion}),
  accountList: (params:any) => http.get('/customer-accounts',{params}),
  accountDetail: (id:number) => http.get(`/customer-accounts/${id}`),
  createAccount: (data:any) => http.post('/customer-accounts',data),
  resetAccountPassword: (id:number,password:string,reason:string) => http.post(`/customer-accounts/${id}/reset-password`,{password,reason}),
  freezeAccount: (id:number,reason:string) => http.post(`/customer-accounts/${id}/freeze`,{reason}),
  resumeAccount: (id:number,reason:string) => http.post(`/customer-accounts/${id}/resume`,{reason}),
  disableAccount: (id:number,reason:string) => http.post(`/customer-accounts/${id}/disable`,{reason})
}
