import request from '../utils/request'

/**
 * 地图点位。
 * scope：model=模型名 / dynasty=朝代 / location=地点 / all=三者任一
 */
export const getMapPoints = (keyword = '', scope = 'all') => {
  return request({
    url: '/map/points',
    method: 'get',
    params: { keyword, scope }
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

