import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useModelStore = defineStore('model', () => {
  const models = ref([])
  const currentModel = ref(null)
  const total = ref(0)
  const isLoading = ref(false)

  async function fetchModels(page = 1, pageSize = 10, keyword = '', categoryId = null, dynasty = '', status = 1, keywordScope = 'all') {
    isLoading.value = true
    try {
      const params = { page, pageSize }
      if (keyword) params.keyword = keyword
      if (keywordScope) params.keywordScope = keywordScope
      if (categoryId !== null && categoryId !== undefined && categoryId !== '') params.categoryId = categoryId
      if (dynasty) params.dynasty = dynasty
      if (status !== null && status !== undefined && status !== '') params.status = status

      const response = await request({
        url: '/models',
        method: 'get',
        params
      })

      // 后端有的接口包 { records, total }，有的直接回 Spring 的 Page（content/totalElements），都接住
      const raw = response.data?.data ?? response.data
      const records = raw?.records ?? raw?.content ?? []
      const totalCount = raw?.total ?? raw?.totalElements ?? 0

      models.value = records
      total.value = totalCount
      return { records, total: totalCount, raw }
    } catch (error) {
      console.error('[ModelStore] 获取模型列表失败', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchModelById(id) {
    isLoading.value = true
    try {
      const response = await request({
        url: `/models/${id}`,
        method: 'get'
      })

      currentModel.value = response.data?.data ?? response.data
      return currentModel.value
    } catch (error) {
      console.error('[ModelStore] 获取模型详情失败', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchHotModels(sortBy = 'viewCount', limit = 10, status = 1) {
    try {
      const response = await request({
        url: '/models/hot',
        method: 'get',
        params: { sortBy, limit }
      })
      const raw = response.data?.data ?? response.data
      if (!Array.isArray(raw)) return raw
      if (status === null || status === undefined || status === '') return raw
      return raw.filter(item => item?.status === status)
    } catch (error) {
      console.error('[ModelStore] 获取热门模型失败', error)
      throw error
    }
  }

  async function toggleLike(modelId) {
    try {
      await request({
        url: `/models/${modelId}/like`,
        method: 'post'
      })

      const safeNumber = (v) => {
        const n = Number(v)
        return Number.isFinite(n) ? n : 0
      }

      // 后端只返回成功与否，点赞状态本地翻转（详情和列表两处都要改）
      if (currentModel.value && currentModel.value.id === modelId) {
        const nextLiked = !currentModel.value.isLiked
        currentModel.value.isLiked = nextLiked
        currentModel.value.likeCount = safeNumber(currentModel.value.likeCount) + (nextLiked ? 1 : -1)
      }

      const model = models.value.find(m => m.id === modelId)
      if (model) {
        const nextLiked = !model.isLiked
        model.isLiked = nextLiked
        model.likeCount = safeNumber(model.likeCount) + (nextLiked ? 1 : -1)
      }

      return { success: true }
    } catch (error) {
      console.error('[ModelStore] 点赞失败', error)
      const backendMessage = error?.response?.data?.message
        || error?.response?.data?.error
        || error?.message
      return { success: false, message: backendMessage || '操作失败' }
    }
  }

  async function addFavorite(modelId) {
    await request({
      url: `/models/${modelId}/favorite`,
      method: 'post'
    })
  }

  async function removeFavorite(modelId) {
    await request({
      url: `/models/${modelId}/favorite`,
      method: 'delete'
    })
  }

  async function checkFavorite(modelId) {
    const response = await request({
      url: `/models/${modelId}/favorite`,
      method: 'get'
    })
    return response.data
  }

  async function getDownloadUrl(modelId) {
    try {
      const response = await request({
        url: `/models/${modelId}/download-url`,
        method: 'get'
      })

      return response.data?.data ?? response.data
    } catch (error) {
      console.error('[ModelStore] 获取下载链接失败', error)
      throw error
    }
  }

  /** 在线预览 / 管理端加载 GLB，不计入下载统计 */
  async function getModelFileUrl(modelId) {
    try {
      const response = await request({
        url: `/models/${modelId}/file-url`,
        method: 'get'
      })

      return response.data?.data ?? response.data
    } catch (error) {
      console.error('[ModelStore] 获取模型文件地址失败', error)
      throw error
    }
  }

  function clearCurrentModel() {
    currentModel.value = null
  }

  return {
    models,
    currentModel,
    total,
    isLoading,
    fetchModels,
    fetchModelById,
    fetchHotModels,
    toggleLike,
    addFavorite,
    removeFavorite,
    checkFavorite,
    getDownloadUrl,
    getModelFileUrl,
    clearCurrentModel
  }
})
