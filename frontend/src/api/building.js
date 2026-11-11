import request from '../utils/request'

// ======================
// 列表页沿用“buildings”命名，但数据源是 models
// ======================

export const getBuildings = (page = 1, pageSize = 10, keyword = '', dynasty = '', categoryId = null) => {
  return request({
    url: '/models',
    method: 'get',
    // 列表页走公开模型接口：不要在这里透传 status。
    // 约定：后端一旦收到 status，会优先按状态过滤，从而忽略 keyword/dynasty 等其它条件。
    // 对非管理员而言，不传 status 时服务端默认只返回“已发布”数据。
    // categoryId 为空时不能带上，否则后端会当成非法值去查。
    params: {
      page,
      pageSize,
      keyword,
      dynasty,
      ...(categoryId != null ? { categoryId } : {})
    }
  })
}

export const getBuildingById = (id) => {
  return request({
    url: `/models/${id}`,
    method: 'get'
  })
}

export const getPopularBuildings = (limit = 10) => {
  return request({
    url: '/models/hot',
    method: 'get',
    params: { sortBy: 'viewCount', limit }
  })
}

export const getBuildingsByFilter = (filter) => {
  return request({
    url: '/models',
    method: 'get',
    params: filter
  })
}

