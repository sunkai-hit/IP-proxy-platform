import { http } from './http'
const root='/v12'
export const v12ResourceApi={
  overview:()=>http.get(`${root}/resources/overview`),
  options:()=>http.get(`${root}/resources/options`),
  centos:(params:any)=>http.get(`${root}/resources/centos`,{params}),
  centosDetail:(id:number)=>http.get(`${root}/resources/centos/${id}`),
  ros:(params:any)=>http.get(`${root}/resources/ros`,{params}),
  rosDetail:(id:number)=>http.get(`${root}/resources/ros/${id}`),
  setRosAutoSwitch:(id:number,enabled:boolean,reason:string)=>http.post(`${root}/resources/ros/${id}/auto-switch`,{enabled,reason}),
  setRosHa:(id:number,data:any)=>http.post(`${root}/resources/ros/${id}/ha`,data),
  lines:(params:any)=>http.get(`${root}/resources/lines`,{params}),
  lineDetail:(id:number)=>http.get(`${root}/resources/lines/${id}`),
  lineIpHistory:(id:number,limit=200)=>http.get(`${root}/resources/lines/${id}/ip-history`,{params:{limit}}),
  lineOperations:(id:number,limit=200)=>http.get(`${root}/resources/lines/${id}/operations`,{params:{limit}}),
  pools:(params:any)=>http.get(`${root}/resource-pools`,{params}),
  pool:(id:number)=>http.get(`${root}/resource-pools/${id}`),
  createPool:(data:any)=>http.post(`${root}/resource-pools`,data),
  updatePool:(id:number,data:any)=>http.put(`${root}/resource-pools/${id}`,data),
  poolLines:(id:number,params:any)=>http.get(`${root}/resource-pools/${id}/lines`,{params}),
  replacePoolLines:(id:number,lineIds:number[],reason:string)=>http.put(`${root}/resource-pools/${id}/lines`,{lineIds,reason})
}
