import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBuildings, getBuildingById, getPopularBuildings } from '@/api/building'

export const useBuildingStore = defineStore('building', () => {
  const buildings = ref([])
  const currentBuilding = ref(null)
  const popularBuildings = ref([])
  const total = ref(0)
  const isLoading = ref(false)

  const mapModelToBuilding = (item) => ({
    id: item?.id,
    name: item?.modelName || '',
    nameEn: item?.modelName || '',
    dynasty: item?.dynasty || '',
    location: item?.location || '',
    coverImage: item?.thumbnailPath || '',
    modelType: 'GLB',
    specifications: item?.description || '',
    historicalSignificance: item?.description || '',
    description: item?.description || '',
    viewCount: item?.viewCount || 0,
    favoriteCount: item?.likeCount || 0,
    isFavorite: item?.isFavorite || false
  })

  async function fetchBuildings(page = 1, pageSize = 10, keyword = '', dynasty = '') {
    isLoading.value = true
    try {
      const response = await getBuildings(page, pageSize, keyword, dynasty)
      const payload = response.data?.data ?? response.data
      const records = payload?.records ?? payload?.content ?? []
      const totalCount = payload?.total ?? payload?.totalElements ?? 0

      buildings.value = records.map(mapModelToBuilding)
      total.value = totalCount
      return { records: buildings.value, total: totalCount }
    } catch (error) {
      console.error('[BuildingStore] 获取建筑列表失败', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchBuildingById(id) {
    isLoading.value = true
    try {
      const response = await getBuildingById(id)
      const raw = response.data?.data ?? response.data
      currentBuilding.value = mapModelToBuilding(raw)
      return currentBuilding.value
    } catch (error) {
      console.error('[BuildingStore] 获取建筑详情失败', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchPopularBuildings(limit = 10) {
    try {
      const response = await getPopularBuildings(limit)
      const raw = response.data?.data ?? response.data
      popularBuildings.value = (raw || []).map(mapModelToBuilding)
      return popularBuildings.value
    } catch (error) {
      console.error('[BuildingStore] 获取热门建筑失败', error)
      throw error
    }
  }

  function clearCurrentBuilding() {
    currentBuilding.value = null
  }

  return {
    buildings,
    currentBuilding,
    popularBuildings,
    total,
    isLoading,
    fetchBuildings,
    fetchBuildingById,
    fetchPopularBuildings,
    clearCurrentBuilding
  }
})
