import { http } from './http'

export const runtimeLogApi={
  extracts:(params:any)=>http.get('/runtime-logs/ip-extract',{params}),
  apis:(params:any)=>http.get('/runtime-logs/api',{params}),
  usage:(params:any)=>http.get('/runtime-logs/usage',{params})
}
