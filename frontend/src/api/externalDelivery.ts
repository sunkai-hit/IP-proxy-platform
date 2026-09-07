import { http } from './http'
const root='/v12/external-delivery'
export const externalDeliveryApi={
  options:(customerId?:number)=>http.get(`${root}/options`,{params:{customerId}}),
  purchases:(params:any)=>http.get(`${root}/purchases`,{params}),
  purchase:(id:number)=>http.get(`${root}/purchases/${id}`),
  createPurchase:(data:any)=>http.post(`${root}/purchases`,data),
  updatePurchase:(id:number,data:any)=>http.put(`${root}/purchases/${id}`,data),
  purchaseStatus:(id:number,status:string,reason:string)=>http.post(`${root}/purchases/${id}/status`,{status,reason}),
  reverseProxies:(params:any)=>http.get(`${root}/reverse-proxies`,{params}),
  reverseProxy:(id:number)=>http.get(`${root}/reverse-proxies/${id}`),
  createReverseProxy:(data:any)=>http.post(`${root}/reverse-proxies`,data),
  updateReverseProxy:(id:number,data:any)=>http.put(`${root}/reverse-proxies/${id}`,data),
  applyReverseProxy:(id:number,reason:string)=>http.post(`${root}/reverse-proxies/${id}/apply`,{reason}),
  testReverseProxy:(id:number,reason:string)=>http.post(`${root}/reverse-proxies/${id}/test`,{reason}),
  disableReverseProxy:(id:number,reason:string)=>http.post(`${root}/reverse-proxies/${id}/disable`,{reason})
}
