import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_API_BASE_URL || '/api',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  config => {
    const token = window.localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  response => {
    const res = response.data

    // 后端有两种返回格式，都要兼容：
    // 1) 统一包装 { code, message, data }，code 非 200 按业务失败弹错
    // 2) 直接返回业务对象（没有 code 字段），原样放行
    if (res && Object.prototype.hasOwnProperty.call(res, 'code') && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return response
  },
  error => {
    if (error.response) {
      const { status, data } = error.response

      if (status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('没有访问权限')
      } else {
        ElMessage.error(data.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误')
    }

    return Promise.reject(error)
  }
)

export default request
