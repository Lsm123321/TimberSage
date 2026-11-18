/**
 * 知识库管理接口封装。
 *
 * 全部走统一 request 封装（自动带 Bearer、统一错误提示），
 * 返回的是 axios response，业务数据取 response.data.data。
 */
import request from '@/utils/request'

/** 分页查询，支持模型/分类/类型/状态/关键词筛选 */
export const listKnowledge = (params) =>
  request.get('/admin/knowledge', { params })

/** 单条详情 */
export const getKnowledge = (id) =>
  request.get(`/admin/knowledge/${id}`)

/** 统计信息（各状态数量、总命中、节省 token） */
export const getKnowledgeStats = () =>
  request.get('/admin/knowledge/stats')

/** 新增 */
export const createKnowledge = (data) =>
  request.post('/admin/knowledge', data)

/** 编辑（后端会重算归一化并失效缓存） */
export const updateKnowledge = (id, data) =>
  request.put(`/admin/knowledge/${id}`, data)

/** 删除 */
export const deleteKnowledge = (id) =>
  request.delete(`/admin/knowledge/${id}`)

/**
 * 审核单条。
 * @param {number} id
 * @param {'approve'|'reject'|'archive'|'restore'} action
 * @param {string} [reason] 驳回原因，action=reject 时使用
 */
export const changeKnowledgeStatus = (id, action, reason) =>
  request.put(`/admin/knowledge/${id}/status`, null, {
    params: { action, reason }
  })

/** 批量审核 */
export const batchKnowledgeStatus = (ids, action, reason) =>
  request.put('/admin/knowledge/batch-status', { ids, action, reason })

/** 下拉用：模型列表（一次拉全，项目只有 66 个） */
export const getModelOptions = () =>
  request.get('/models', { params: { page: 1, pageSize: 500, sortBy: 'id' } })

/** 下拉用：分类列表 */
export const getCategoryOptions = () =>
  request.get('/categories')

/**
 * 图谱数据。
 * 默认只返回「分类 + 模型」两层（72 个节点），
 * withTypes=true 才把每个模型的 6 类知识也展开（468 个节点）。
 */
export const getKnowledgeGraph = (params) =>
  request.get('/admin/knowledge/graph', { params })

/** 点击模型节点时按需加载它的知识类型，避免首屏拉全量 */
export const expandGraphNode = (modelId) =>
  request.get(`/admin/knowledge/graph/expand/${modelId}`)
