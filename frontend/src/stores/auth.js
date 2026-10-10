import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getUserInfo } from '@/api/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(window.localStorage.getItem('token') || '')
  const userInfo = ref(null)
  const isLoading = ref(false)

  const isLoggedIn = computed(() => !!token.value)

  const debug = (...args) => {
    // 仅在开发环境输出调试信息，避免生产环境控制台噪音与敏感信息泄露
    if (import.meta.env.DEV) console.debug('[AuthStore]', ...args)
  }

  async function loginAction(loginData, options = {}) {
    const { redirect = true } = options
    isLoading.value = true
    try {
      const response = await login(loginData)
      const responseData = response.data
      debug('login response received')

      if (responseData.code !== 200) {
        console.error('[AuthStore] 登录失败:', responseData.message)
        return { success: false, message: responseData.message || '登录失败' }
      }

      const authData = responseData.data
      token.value = authData.token
      window.localStorage.setItem('token', authData.token)
      window.localStorage.setItem('userId', authData.userId)
      window.localStorage.setItem('username', authData.username)
      debug('token saved')

      await fetchUserInfo()
      debug('user info updated')
      if (redirect) {
        router.push('/')
      }

      return { success: true }
    } catch (error) {
      console.error('[AuthStore] 登录过程异常:', error)
      let errorMessage = '登录失败'
      if (error.response) {
        if (error.response.status === 401) {
          errorMessage = '用户名或密码错误'
        } else if (error.response.status === 400) {
          errorMessage = '请求参数错误'
        } else if (error.response.status === 500) {
          errorMessage = '服务器内部错误'
        } else if (error.response.data?.message) {
          errorMessage = error.response.data.message
        }
      } else if (error.message) {
        errorMessage = error.message
      }
      return { success: false, message: errorMessage }
    } finally {
      isLoading.value = false
    }
  }

  async function registerAction(registerData) {
    isLoading.value = true
    try {
      const response = await register(registerData)
      const responseData = response.data
      debug('register response received')

      if (responseData.code !== 200) {
        console.error('[AuthStore] 注册失败:', responseData.message)
        return { success: false, message: responseData.message || '注册失败' }
      }

      const authData = responseData.data
      token.value = authData.token
      window.localStorage.setItem('token', authData.token)
      window.localStorage.setItem('userId', authData.userId)
      window.localStorage.setItem('username', authData.username)
      debug('token saved')

      await fetchUserInfo()
      debug('user info updated')
      router.push('/')

      return { success: true }
    } catch (error) {
      console.error('[AuthStore] 注册过程异常:', error)
      let errorMessage = '注册失败'
      if (error.response) {
        if (error.response.status === 400) {
          errorMessage = '请求参数错误或用户名已存在'
        } else if (error.response.status === 500) {
          errorMessage = '服务器内部错误'
        } else if (error.response.data?.message) {
          errorMessage = error.response.data.message
        }
      } else if (error.message) {
        errorMessage = error.message
      }
      return { success: false, message: errorMessage }
    } finally {
      isLoading.value = false
    }
  }

  async function fetchUserInfo() {
    if (!token.value) {
      debug('skip getUserInfo: missing token')
      return
    }

    try {
      const response = await getUserInfo()
      const responseData = response.data
      debug('user info response received')

      if (responseData.code !== 200) {
        console.error('[AuthStore] 获取用户信息失败:', responseData.message)
        if (responseData.code === 401) {
          logout()
        }
        return
      }

      userInfo.value = responseData.data
      debug('user info updated')
    } catch (error) {
      console.error('[AuthStore] 获取用户信息失败', error)
      if (error.response?.status === 401) {
        debug('token expired, logout')
        logout()
      }
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    window.localStorage.removeItem('token')
    window.localStorage.removeItem('userId')
    window.localStorage.removeItem('username')
    debug('local storage cleared')
    router.push('/login')
  }

  function clearAuth() {
    token.value = ''
    userInfo.value = null
    window.localStorage.removeItem('token')
    window.localStorage.removeItem('userId')
    window.localStorage.removeItem('username')
  }

  return {
    token,
    userInfo,
    isLoading,
    isLoggedIn,
    loginAction,
    registerAction,
    fetchUserInfo,
    logout,
    clearAuth
  }
})