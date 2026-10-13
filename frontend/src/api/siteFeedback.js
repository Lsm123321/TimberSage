import request from '../utils/request'

export const createSiteFeedback = (data) => {
  return request({
    url: '/site-feedback',
    method: 'post',
    data
  })
}

export const adminGetSiteFeedback = (params) => {
  return request({
    url: '/site-feedback/admin',
    method: 'get',
    params
  })
}

export const adminUpdateSiteFeedback = (id, data) => {
  return request({
    url: `/site-feedback/admin/${id}`,
    method: 'put',
    data
  })
}

