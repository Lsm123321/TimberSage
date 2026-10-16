<template>
  <div class="map-explorer">
    <div class="top-bar v2-glass">
      <el-input
        v-model="keyword"
        class="search-input"
        clearable
        placeholder="搜索古建筑名称，地图点位将高亮提示"
        @input="handleKeywordChange"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <div class="search-status">
        <span v-if="keyword">匹配点位：{{ filteredPoints.length }} 个</span>
        <span v-else>总点位：{{ points.length }} 个</span>
      </div>
      <el-button plain @click="goBack">返回上一页</el-button>
    </div>

    <div class="stage">
      <div class="map-board v2-glass-strong">
        <div class="map-chart" ref="mapChartRef"></div>

        <div class="compliance">
          <div>地图来源：{{ compliance.source }}</div>
          <div>审图号：{{ compliance.reviewNo }}（{{ compliance.reviewYear }}）</div>
          <div v-if="!compliance.configured" class="warn">提示：审图号未配置，此页面不可用于正式发布。</div>
        </div>
      </div>

      <transition name="card-pop">
        <div class="region-panel v2-glass" v-if="activeRegionCode">
          <div class="panel-header">
            <h3>{{ activeRegionName }} · 建筑模型</h3>
            <el-button text @click="clearSelection">收起</el-button>
          </div>
          <div class="panel-body" v-loading="regionLoading">
            <div class="model-card" v-for="model in regionModels" :key="model.id">
              <img :src="getThumbnailSrc(model)" :alt="model.modelName" @error="handleThumbError(model)">
              <div class="meta">
                <h4>{{ model.modelName }}</h4>
                <div class="sub">{{ model.dynasty || '未知朝代' }} · {{ model.location || '未知地点' }}</div>
                <p>{{ model.insight || 'AI 正在生成相关建筑成就与历史意义...' }}</p>
                <div class="links">
                  <a
                    v-for="link in model.links"
                    :key="`${model.id}-${link.url}`"
                    :href="link.url"
                    target="_blank"
                    rel="noreferrer"
                  >
                    {{ link.title }} · 了解更多
                  </a>
                </div>
              </div>
            </div>
            <el-empty v-if="!regionLoading && regionModels.length === 0" description="该区域暂无可展示模型" />
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { generateMapInsight, getMapPoints, getRegionModels } from '@/api/map'
import * as echarts from 'echarts'
import chinaGeoJson from '@/assets/maps/china-geo.json'
import defaultThumb from '@/assets/maps/default-thumb.svg'

const router = useRouter()
const keyword = ref('')
const points = ref([])
const filteredPoints = ref([])
const compliance = ref({
  source: '国家地理信息公共服务平台（https://www.tianditu.gov.cn/）',
  reviewNo: '待配置审图号',
  reviewYear: '2026',
  configured: false
})
const defaultImage = defaultThumb
const activeRegionCode = ref('')
const activeRegionName = ref('')
const activePoint = ref(null)
const regionModels = ref([])
const regionLoading = ref(false)
let pollingTimer = null
const mapChartRef = ref(null)
let mapChart = null
const onResize = () => mapChart?.resize()
const insightCache = new Map()
let regionRefreshing = false

const getThumbnailSrc = (model) => {
  if (model.thumbLoadFailed) return defaultImage
  return model.thumbnailPath || defaultImage
}

const handleThumbError = (model) => {
  model.thumbLoadFailed = true
}

const upsertRegionModels = (rows) => {
  const prevById = new Map(regionModels.value.map(item => [item.id, item]))
  regionModels.value = rows.map(item => {
    const previous = prevById.get(item.id)
    const cached = insightCache.get(item.id)
    return {
      ...item,
      insight: previous?.insight || cached?.insight || '',
      links: previous?.links || cached?.links || [],
      thumbLoadFailed: false
    }
  })
}

const loadPoints = async () => {
  try {
    const resp = await getMapPoints(keyword.value.trim())
    const payload = resp.data?.data || {}
    points.value = payload.points || []
    filteredPoints.value = payload.points || []
    compliance.value = payload.compliance || compliance.value

    if (activeRegionCode.value) {
      const matchedPoint = points.value.find(point => point.regionCode === activeRegionCode.value)
      if (matchedPoint) {
        activePoint.value = matchedPoint
        activeRegionName.value = matchedPoint.regionName || activeRegionName.value
      }
    }

    renderMap()
    await refreshActiveRegionModels()
  } catch (error) {
    ElMessage.error('加载地图点位失败')
  }
}

const handleKeywordChange = async () => {
  await loadPoints()
}

const hydrateInsights = async () => {
  for (const model of regionModels.value) {
    if (model.insight) continue
    try {
      const resp = await generateMapInsight({
        modelId: model.id,
        modelName: model.modelName,
        dynasty: model.dynasty,
        location: model.location,
        description: model.description
      })
      const payload = resp.data?.data || {}
      model.insight = payload.insight || ''
      model.links = payload.links || []
      insightCache.set(model.id, {
        insight: model.insight,
        links: model.links
      })
    } catch (error) {
      model.insight = '该建筑在地域文化与结构工艺方面具有代表性价值，建议结合模型细节进行重点观看。'
      model.links = []
    }
  }
}

const refreshActiveRegionModels = async () => {
  if (!activeRegionCode.value || regionRefreshing) return
  regionRefreshing = true
  try {
    const resp = await getRegionModels(activeRegionCode.value)
    const rows = resp.data?.data || []
    upsertRegionModels(rows)
    await hydrateInsights()
  } catch (error) {
    // 后台轮询，失败了就等下一轮，别弹错打扰用户
  } finally {
    regionRefreshing = false
  }
}

const selectPoint = async (point) => {
  activePoint.value = point
  activeRegionCode.value = point.regionCode
  activeRegionName.value = point.regionName
  renderMap()
  regionLoading.value = true
  regionModels.value = []
  try {
    const resp = await getRegionModels(point.regionCode)
    const rows = resp.data?.data || []
    upsertRegionModels(rows)
    await hydrateInsights()
  } catch (error) {
    ElMessage.error('加载区域模型失败')
  } finally {
    regionLoading.value = false
  }
}

const clearSelection = () => {
  activePoint.value = null
  activeRegionCode.value = ''
  activeRegionName.value = ''
  regionModels.value = []
  renderMap()
}

const goBack = () => {
  router.back()
}

const startPolling = () => {
  pollingTimer = window.setInterval(() => {
    loadPoints()
  }, 30000)
}

const stopPolling = () => {
  if (pollingTimer) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const onFocus = () => {
  loadPoints()
}

const renderMap = () => {
  if (!mapChartRef.value) return
  if (!mapChart) {
    echarts.registerMap('china-local', chinaGeoJson)
    mapChart = echarts.init(mapChartRef.value)
    mapChart.on('click', (params) => {
      const raw = params?.data?.raw
      if (raw) {
        selectPoint(raw)
      }
    })
  }

  const seriesData = filteredPoints.value.map(point => ({
    value: [point.lng, point.lat],
    raw: point,
    labelText: point.hint
  }))

  const activeLinks = activePoint.value
    ? points.value
      .filter(point => point.regionCode !== activePoint.value.regionCode)
      .map(point => ({
        coords: [
          [activePoint.value.lng, activePoint.value.lat],
          [point.lng, point.lat]
        ]
      }))
    : []

  mapChart.setOption({
    backgroundColor: 'transparent',
    geo: {
      map: 'china-local',
      roam: true,
      zoom: activePoint.value ? 1.35 : 1.08,
      center: activePoint.value ? [activePoint.value.lng, activePoint.value.lat] : undefined,
      itemStyle: {
        areaColor: 'rgba(20, 50, 84, 0.82)',
        borderColor: 'rgba(101, 226, 255, 0.78)',
        borderWidth: 1.3,
        shadowColor: 'rgba(77, 185, 255, 0.35)',
        shadowBlur: 10
      },
      emphasis: {
        itemStyle: {
          areaColor: 'rgba(38, 100, 156, 0.92)',
          borderColor: 'rgba(165, 240, 255, 0.95)',
          borderWidth: 1.6,
          shadowColor: 'rgba(113, 222, 255, 0.9)',
          shadowBlur: 24
        }
      },
      label: {
        show: false
      }
    },
    tooltip: {
      trigger: 'item',
      formatter: (params) => params?.data?.raw ? `${params.data.raw.regionName}<br/>${params.data.raw.hint}` : ''
    },
    series: [
      {
        name: '光流轨迹',
        type: 'lines',
        coordinateSystem: 'geo',
        zlevel: 2,
        data: activeLinks,
        polyline: false,
        lineStyle: {
          color: 'rgba(108, 224, 255, 0.55)',
          width: 1.2,
          opacity: 0.9,
          curveness: 0.22
        },
        effect: {
          show: true,
          period: 3.6,
          trailLength: 0.38,
          symbol: 'circle',
          symbolSize: 5,
          color: '#8df1ff'
        }
      },
      {
        name: '地图点位',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: seriesData,
        showEffectOn: 'render',
        rippleEffect: {
          scale: 5,
          brushType: 'stroke'
        },
        itemStyle: {
          color: '#ffd57f',
          shadowBlur: 18,
          shadowColor: 'rgba(255, 213, 127, 0.95)'
        },
        symbolSize: (val, params) => {
          const isActive = activePoint.value?.id && params?.data?.raw?.id === activePoint.value.id
          const isMatched = keyword.value.trim() && (params?.data?.raw?.hint || '').includes(keyword.value.trim())
          if (isActive) return 16
          if (isMatched) return 13
          return 10
        },
        emphasis: {
          scale: 1.35,
          itemStyle: {
            color: '#8ceaff',
            shadowBlur: 28,
            shadowColor: 'rgba(140, 234, 255, 0.95)'
          }
        },
        label: {
          show: true,
          formatter: (params) => params?.data?.labelText || '',
          position: 'right',
          color: '#f8f4ea',
          fontSize: 12,
          backgroundColor: 'rgba(6, 14, 25, 0.75)',
          borderRadius: 8,
          padding: [3, 8, 3, 8]
        }
      }
    ]
  })
}

onMounted(async () => {
  await loadPoints()
  startPolling()
  window.addEventListener('focus', onFocus)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('focus', onFocus)
  window.removeEventListener('resize', onResize)
  if (mapChart) {
    mapChart.dispose()
    mapChart = null
  }
})
</script>

<style lang="scss" scoped>
.map-explorer {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100vh - 130px);

  .top-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;

    .search-input {
      max-width: 460px;
    }

    .search-status {
      color: var(--v2-text-dim);
      font-size: 13px;
    }
  }

  .stage {
    display: grid;
    grid-template-columns: 1.3fr 1fr;
    gap: 14px;
    min-height: 620px;
  }

  .map-board {
    position: relative;
    border-radius: var(--v2-radius-xl);
    overflow: hidden;
    min-height: 620px;
    perspective: 1200px;

    .map-chart {
      width: 100%;
      height: 100%;
      min-height: 620px;
      background:
        radial-gradient(circle at 25% 30%, rgba(214, 183, 122, 0.16), transparent 42%),
        radial-gradient(circle at 78% 35%, rgba(95, 199, 184, 0.12), transparent 40%),
        linear-gradient(150deg, rgba(11, 26, 44, 0.78), rgba(9, 18, 30, 0.86));
      animation: map-breath 4.2s ease-in-out infinite alternate;
    }

    &::before {
      content: '';
      position: absolute;
      inset: -1px;
      border-radius: inherit;
      pointer-events: none;
      border: 1px solid rgba(106, 227, 255, 0.34);
      box-shadow: inset 0 0 30px rgba(60, 170, 255, 0.16);
      z-index: 1;
    }

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: inherit;
      pointer-events: none;
      background: linear-gradient(120deg, transparent 0%, rgba(124, 224, 255, 0.16) 45%, transparent 80%);
      transform: translateX(-110%);
      animation: border-flow 5.2s linear infinite;
      z-index: 1;
    }

    .compliance {
      position: absolute;
      right: 14px;
      bottom: 12px;
      z-index: 3;
      font-size: 12px;
      line-height: 1.5;
      color: rgba(242, 237, 226, 0.9);
      text-align: right;

      .warn {
        color: #ffd28a;
      }
    }
  }

  .region-panel {
    border-radius: var(--v2-radius-xl);
    padding: 12px;
    overflow: hidden;
    min-height: 620px;
    display: flex;
    flex-direction: column;
    transform-style: preserve-3d;
    box-shadow: 0 24px 56px rgba(0, 0, 0, 0.36);

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      h3 {
        color: var(--v2-text);
      }
    }

    .panel-body {
      overflow: auto;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .model-card {
      display: grid;
      grid-template-columns: 120px 1fr;
      gap: 10px;
      padding: 10px;
      border-radius: 14px;
      border: 1px solid rgba(255, 255, 255, 0.12);
      background: rgba(8, 16, 28, 0.55);
      transition: transform 280ms ease, box-shadow 280ms ease, border-color 280ms ease;

      &:hover {
        transform: translateY(-4px) translateZ(0);
        border-color: rgba(140, 234, 255, 0.44);
        box-shadow: 0 18px 40px rgba(12, 34, 60, 0.42);
      }

      img {
        width: 120px;
        height: 86px;
        object-fit: cover;
        border-radius: 10px;
        border: 1px solid rgba(140, 234, 255, 0.34);
        box-shadow: 0 0 20px rgba(99, 198, 255, 0.24);
      }

      h4 {
        color: var(--v2-text);
        font-size: 14px;
      }

      .sub {
        margin: 4px 0 6px;
        color: var(--v2-muted);
        font-size: 12px;
      }

      p {
        color: var(--v2-text-dim);
        line-height: 1.6;
        font-size: 12px;
        white-space: pre-wrap;
      }

      .links {
        margin-top: 8px;
        display: flex;
        flex-direction: column;
        gap: 4px;

        a {
          color: #9cd8ff;
          font-size: 12px;
        }
      }
    }
  }
}

.card-pop-enter-active,
.card-pop-leave-active {
  transition: all 320ms cubic-bezier(.2,.8,.2,1);
}

.card-pop-enter-from,
.card-pop-leave-to {
  opacity: 0;
  transform: perspective(1200px) rotateY(-8deg) translateY(22px) scale(0.94);
}

@keyframes border-flow {
  from {
    transform: translateX(-110%);
  }
  to {
    transform: translateX(120%);
  }
}

@keyframes map-breath {
  0% {
    filter: saturate(1) brightness(1);
  }
  100% {
    filter: saturate(1.1) brightness(1.06);
  }
}

@media (max-width: 1200px) {
  .map-explorer .stage {
    grid-template-columns: 1fr;
  }
}
</style>

