import request from '../utils/request'

export const getMapPoints = (keyword = '') => {
  return request({
    url: '/map/points',
    method: 'get',
    params: { keyword }
  })
}

export const getRegionModels = (regionCode) => {
  return request({
    url: `/map/regions/${regionCode}/models`,
    method: 'get'
  })
}

export const generateMapInsight = (payload) => {
  return request({
    url: '/map/insight',
    method: 'post',
    data: payload
  })
}

