import request from '../utils/request'

export const applyContributor = (data) => {
  return request({
    url: '/contribution-applies',
    method: 'post',
    data
  })
}

export const getUserFavorites = (params) => {
  return request({
    url: `/users/profile/favorites`,
    method: 'get',
    params
  })
}

export const getUserLikes = (params) => {
  return request({
    url: `/users/profile/likes`,
    method: 'get',
    params
  })
}

export const getUserComments = (params) => {
  return request({
    url: `/users/profile/comments`,
    method: 'get',
    params
  })
}

export const getUserChatHistory = (params) => {
  return request({
    url: `/chat/history`,
    method: 'get',
    params
  })
}

export const updateUserChatNote = (id, chatNote) => {
  return request({
    url: `/chat/history/${id}/note`,
    method: 'put',
    data: { chatNote }
  })
}

export const generateUserChatNote = (id) => {
  return request({
    url: `/chat/history/${id}/note/generate`,
    method: 'post'
  })
}

export const deleteUserChatHistory = (id) => {
  return request({
    url: `/chat/history/${id}`,
    method: 'delete'
  })
}

export const getUserDownloads = (params) => {
  return request({
    url: `/users/profile/downloads`,
    method: 'get',
    params
  })
}

export const getUserMaterialPresets = (params) => {
  // 有的地方直接传 modelId 数字过来，包一层不然 axios 的 params 会炸
  const normalizedParams = (typeof params === 'number' || typeof params === 'string')
    ? { modelId: params }
    : (params || {})
  return request({
    url: `/users/profile/material-presets`,
    method: 'get',
    params: normalizedParams
  })
}

export const deleteMaterialPreset = (id) => {
  return request({
    url: `/material-presets/${id}`,
    method: 'delete'
  })
}
