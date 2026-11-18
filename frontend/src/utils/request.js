import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { openLoginGuide } from '@/composables/useLoginGuide'

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

/*
  401/403 统一引导登录，不再弹红色错误 toast。

  背景：项目允许游客只读浏览，但游客（或带着失效 token 的访问者）
  打开个人中心这类页面时，页面并发发出的请求会全部 403。
  之前每个 403 都 ElMessage.error('没有访问权限')，屏幕上连弹四条红框，
  用户只看到报错、不知道下一步该干什么。
  现在改成弹「登录引导」——同一个弹窗是单例，并发触发也只显示一次，
  文案明确告诉用户"登录后才能操作"，并给出去登录的按钮。

  从 URL 推断用户想做什么，让引导文案更贴切。
*/
const actionFromUrl = (url = '') => {
  if (url.includes('/profile')) return '修改资料'
  if (url.includes('/material-presets')) return '修改资料'
  if (url.includes('/favorites') || url.includes('/likes')) return '收藏'
  if (url.includes('/comments')) return '评论'
  if (url.includes('/downloads')) return '下载'
  if (url.includes('/model-feedback')) return '纠错'
  if (url.includes('/contribution-applies') || url.includes('/role-upgrade')) return '上传'
  return ''
}

request.interceptors.response.use(
  response => {
    const res = response.data

    // 后端有两种返回格式，都要兼容：
    // 1) 统一包装 { code, message, data }，code 非 200 按业务失败弹错
    // 2) 直接返回业务对象（没有 code 字段），原样放行
    if (res && Object.prototype.hasOwnProperty.call(res, 'code') && res.code !== 200) {
      // 业务层的权限拒绝也走登录引导，而不是红 toast
      if (res.code === 401 || res.code === 403) {
        openLoginGuide(actionFromUrl(response.config?.url))
        return Promise.reject(new Error(res.message || '需要登录'))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return response
  },
  error => {
    if (error.response) {
      const { status, data, config } = error.response

      if (status === 401) {
        // token 失效：清掉本地状态，弹登录引导（不强制跳转，用户可以继续浏览）
        const authStore = useAuthStore()
        authStore.clearAuth()
        openLoginGuide(actionFromUrl(config?.url))
      } else if (status === 403) {
        openLoginGuide(actionFromUrl(config?.url))
      } else if (status !== 404) {
        // 404 多为空数据的正常表现，不弹错打扰
        ElMessage.error(data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误')
    }

    return Promise.reject(error)
  }
)

export default request
