import { http } from './http'

// Every method is read-only; server enforces permissions for both the parent
// object and the related child domain.
const base='/relations'
export const businessRelationsApi={
  customerOrders:(id:number)=>http.get(`${base}/customers/${id}/orders`),
  orderServices:(id:number)=>http.get(`${base}/orders/${id}/services`),
  packageServices:(id:number)=>http.get(`${base}/packages/${id}/services`),
  serviceBindings:(id:number)=>http.get(`${base}/services/${id}/bindings`),
  paidRenewalOrders:(id:number)=>http.get(`${base}/services/${id}/paid-renewal-orders`),
  linePools:(id:number)=>http.get(`${base}/lines/${id}/pools`),
  lineServices:(id:number)=>http.get(`${base}/lines/${id}/services`),
  poolServices:(id:number)=>http.get(`${base}/pools/${id}/services`),
  purchaseProxies:(id:number)=>http.get(`${base}/supplier-purchases/${id}/proxies`),
  purchaseServices:(id:number)=>http.get(`${base}/supplier-purchases/${id}/services`)
}
