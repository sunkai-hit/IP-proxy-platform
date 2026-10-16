import { http } from './http'
const root='/long-monitor'
export const longMonitorApi={
  overview:()=>http.get(`${root}/overview`),
  options:(customerId?:number)=>http.get(`${root}/options`,{params:{customerId}}),
  bots:(params:any)=>http.get(`${root}/bots`,{params}),
  bot:(id:number)=>http.get(`${root}/bots/${id}`),
  createBot:(data:any)=>http.post(`${root}/bots`,data),
  updateBot:(id:number,data:any)=>http.put(`${root}/bots/${id}`,data),
  setBotEnabled:(id:number,enabled:boolean,reason:string)=>http.post(`${root}/bots/${id}/enabled`,{enabled,reason}),
  testBot:(id:number,reason:string)=>http.post(`${root}/bots/${id}/test`,{reason}),
  monitors:(params:any)=>http.get(`${root}/monitors`,{params}),
  monitor:(id:number)=>http.get(`${root}/monitors/${id}`),
  createMonitor:(data:any)=>http.post(`${root}/monitors`,data),
  updateMonitor:(id:number,data:any)=>http.put(`${root}/monitors/${id}`,data),
  setMonitorEnabled:(id:number,enabled:boolean,reason:string)=>http.post(`${root}/monitors/${id}/enabled`,{enabled,reason}),
  check:(id:number,reason:string)=>http.post(`${root}/monitors/${id}/check`,{reason}),
  alarms:(params:any)=>http.get(`${root}/alarms`,{params}),
  alarm:(id:number)=>http.get(`${root}/alarms/${id}`),
  closeAlarm:(id:number,reason:string)=>http.post(`${root}/alarms/${id}/close`,{reason}),
  notifications:(params:any)=>http.get(`${root}/notifications`,{params})
}
