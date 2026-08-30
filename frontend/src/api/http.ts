import axios from 'axios'

export const http = axios.create({ baseURL: '/api/admin/v1', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('ipproxy_access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  config.headers['X-Request-Id'] = `web_${crypto.randomUUID().replaceAll('-', '')}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('ipproxy_access_token')
      if (location.pathname !== '/login') location.href = '/login'
    }
    return Promise.reject(error)
  }
)
