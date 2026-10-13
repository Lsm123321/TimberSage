import request from '../utils/request'

export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export const getLoginCaptcha = () => {
  return request({
    url: '/auth/captcha',
    method: 'get'
  })
}

export const register = (data) => {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

export const getUserInfo = () => {
  return request({
    url: '/users/me',
    method: 'get'
  })
}

export const updateUserInfo = (data) => {
  return request({
    url: '/users/me',
    method: 'put',
    data
  })
}
