import { http } from './http'

export const monitorApi={
  overview:()=>http.get('/monitor/overview'),
  objects:(params:any)=>http.get('/monitor/objects',{params}),
  metrics:(type:string,id:number,params:any)=>http.get(`/monitor/objects/${type}/${id}/metrics`,{params}),
  collect:(triggerType='MANUAL')=>http.post('/monitor/collect',null,{params:{triggerType}})
}

export const alarmApi={
  list:(params:any)=>http.get('/alarms',{params}),
  detail:(id:number)=>http.get(`/alarms/${id}`),
  acknowledge:(id:number,remark:string)=>http.post(`/alarms/${id}/acknowledge`,{remark}),
  process:(id:number,remark:string)=>http.post(`/alarms/${id}/process`,{remark}),
  close:(id:number,remark:string)=>http.post(`/alarms/${id}/close`,{remark}),
  note:(id:number,remark:string)=>http.post(`/alarms/${id}/notes`,{remark}),
  rules:(params:any)=>http.get('/alarms/rules',{params}),
  createRule:(data:any)=>http.post('/alarms/rules',data),
  updateRule:(id:number,data:any)=>http.put(`/alarms/rules/${id}`,data),
  ruleStatus:(id:number,status:string)=>http.post(`/alarms/rules/${id}/status`,{status}),
  deleteRule:(id:number)=>http.delete(`/alarms/rules/${id}`),
  notifications:(params:any)=>http.get('/alarms/notifications',{params})
}

export const statisticsApi={
  overview:()=>http.get('/statistics/overview'),
  resources:(params:any)=>http.get('/statistics/resources',{params}),
  ips:(params:any)=>http.get('/statistics/ips',{params}),
  customers:(params:any)=>http.get('/statistics/customers',{params}),
  products:(params:any)=>http.get('/statistics/products',{params}),
  suppliers:(params:any)=>http.get('/statistics/suppliers',{params}),
  recalculate:(hour?:string,triggerType='MANUAL')=>http.post('/statistics/recalculate',null,{params:{...(hour?{hour}:{}),triggerType}})
}
