import request from '@/utils/request'

/**
 * 单次最多提交多少个构件给后端生成/检索称谓。
 * 这个值要和后端的参数校验保持一致（超过会被拒绝或返回错误）。
 */
export const PART_LABEL_BATCH_SIZE = 40

/**
 * 批量获取构件的中文称谓（按 technicalName 推断，返回 { [partId]: label }）。
 * 后端接口：POST /api/models/{modelId}/part-labels
 *
 * @param {number|string} modelId 模型 id
 * @param {Array<{id: string, technicalName: string}>} parts 构件列表
 */
export const labelModelParts = (modelId, parts) => {
  return request({
    url: `/models/${modelId}/part-labels`,
    method: 'post',
    data: { parts }
  })
}

/**
 * 部件较多时分批请求并合并（避免超过单次条数限制）。
 * @param {function} onProgress 可选 (batchIndex, totalBatches, batchSize) => void
 */
/**
 * 获取单个构件的简要介绍。
 * 后端接口：POST /api/models/{modelId}/part-blurb
 */
export const fetchPartBlurb = (modelId, payload) => {
  return request({
    url: `/models/${modelId}/part-blurb`,
    method: 'post',
    data: payload
  })
}

/**
 * 当构件很多时，用分批请求把结果合并成一个大 Map。
 * 不做并发（串行请求），避免同时打太多请求导致后端限流/排队。
 */
export const labelModelPartsBatched = async (modelId, parts, onProgress) => {
  const size = PART_LABEL_BATCH_SIZE
  const merged = {}
  const totalBatches = Math.max(1, Math.ceil(parts.length / size))
  let batchIndex = 0
  for (let i = 0; i < parts.length; i += size) {
    batchIndex += 1
    const slice = parts.slice(i, i + size)
    onProgress?.(batchIndex, totalBatches, slice.length)
    const res = await labelModelParts(modelId, slice)
    const data = res.data?.data
    if (data && typeof data === 'object') {
      Object.assign(merged, data)
    }
  }
  return merged
}
