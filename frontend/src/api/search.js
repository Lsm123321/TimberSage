import request from '../utils/request'

export const searchSuggestions = (keyword) => {
  return request({
    url: '/models/search/suggest',
    method: 'get',
    // 注意后端参数名是 prefix 不是 keyword
    params: { prefix: keyword }
  })
}

export const getHotSearches = () => {
  return request({
    // 热搜走的是老接口路径
    url: '/hot-search',
    method: 'get'
  })
}
