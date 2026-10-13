import request from '@/utils/request'

/** 匿名预览：仅「允许所有人下载」的已发布模型会返回 url */
export const getPublicModelFileUrl = (modelId) => {
  return request({
    url: `/public/models/${modelId}/file-url`,
    method: 'get'
  })
}
