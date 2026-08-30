import { http } from './http'
export const productApi = {
  overview: () => http.get('/products/overview'), options: () => http.get('/products/options'),
  products: (params:any) => http.get('/products',{params}), product:(id:number)=>http.get(`/products/${id}`),
  createProduct:(data:any)=>http.post('/products',data), updateProduct:(id:number,data:any)=>http.put(`/products/${id}`,data),
  config:(id:number)=>http.get(`/products/${id}/config`), updateConfig:(id:number,data:any)=>http.put(`/products/${id}/config`,data),
  enableProduct:(id:number,reason:string)=>http.post(`/products/${id}/enable`,{reason}), disableProduct:(id:number,reason:string)=>http.post(`/products/${id}/disable`,{reason}), copyProduct:(id:number,reason:string)=>http.post(`/products/${id}/copy`,{reason}),
  packages:(params:any)=>http.get('/packages',{params}), pkg:(id:number)=>http.get(`/packages/${id}`), createPackage:(data:any)=>http.post('/packages',data), updatePackage:(id:number,data:any)=>http.put(`/packages/${id}`,data), copyPackage:(id:number,reason:string)=>http.post(`/packages/${id}/copy`,{reason}), enablePackage:(id:number,reason:string)=>http.post(`/packages/${id}/enable`,{reason}), disablePackage:(id:number,reason:string)=>http.post(`/packages/${id}/disable`,{reason}),
  strategies:(params:any)=>http.get('/resource-strategies',{params}), strategy:(id:number)=>http.get(`/resource-strategies/${id}`), createStrategy:(data:any)=>http.post('/resource-strategies',data), updateStrategy:(id:number,data:any)=>http.put(`/resource-strategies/${id}`,data), validateStrategy:(id:number,reason:string)=>http.post(`/resource-strategies/${id}/validate`,{reason}), enableStrategy:(id:number,reason:string)=>http.post(`/resource-strategies/${id}/enable`,{reason}), disableStrategy:(id:number,reason:string)=>http.post(`/resource-strategies/${id}/disable`,{reason})
}
