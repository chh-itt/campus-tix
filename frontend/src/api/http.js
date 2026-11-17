import axios from 'axios'

// 统一请求封装：HTTP 恒 200，业务码在 body.code（0 = 成功）
const http = axios.create({ baseURL: '', timeout: 15000 })

http.interceptors.request.use(cfg => {
  const token = localStorage.getItem('ct_token')
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  return cfg
})

http.interceptors.response.use(resp => {
  const body = resp.data
  if (body && typeof body.code === 'number') {
    if (body.code === 0) return body.data
    if (body.code === 401) {
      localStorage.removeItem('ct_token')
      localStorage.removeItem('ct_user')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
    }
    return Promise.reject(body)
  }
  return resp.data
}, err => Promise.reject({ code: -1, message: err.message || '网络错误' }))

export default http
