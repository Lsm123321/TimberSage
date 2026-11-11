<template>
  <div class="map-explorer">
    <!--
      搜索已并入顶栏（支持全部/建筑名/朝代/地点）。
      这里只保留当前筛选状态回显 + 点位统计，避免整条搜索栏重复占地方。
    -->
    <div class="filter-hint v2-glass" v-if="keyword.trim() || activeRegionCode">
      <span class="hint-label">当前筛选</span>
      <el-tag v-if="keyword.trim()" closable @close="clearKeywordFilter">{{ scopeLabel }}：{{ keyword }}</el-tag>
      <el-tag v-if="activeRegionCode" type="warning" closable @close="clearSelection">{{ activeRegionName }}</el-tag>
      <span class="hint-count">匹配点位 {{ filteredPoints.length }} / {{ points.length }}</span>
      <el-button text size="small" @click="clearAllFilters">重置</el-button>
    </div>

    <div class="stage" :class="{ 'no-panel': !activeRegionCode }">
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

      <!--
        未选中区域时，右侧用「朝代分布 + 点位速览」填充。
        必须放在 stage 网格内部才能跟地图同屏；放外面会被挤到视口下方，用户看不到。
      -->
      <div class="insight-deck v2-glass" v-if="!activeRegionCode">
        <div class="deck-block dynasty-block">
          <div class="deck-title">
            <span>朝代分布</span>
            <span class="deck-hint">点击筛选</span>
          </div>
          <div class="dynasty-chips">
            <button
              v-for="item in dynastyStats"
              :key="item.name"
              type="button"
              class="dynasty-chip"
              :class="{ active: dynastyFilter === item.name }"
              @click="toggleDynastyFilter(item.name)"
            >
              <span class="chip-name">{{ item.name }}</span>
              <span class="chip-count">{{ item.count }}</span>
            </button>
            <span v-if="!dynastyStats.length" class="deck-empty">暂无数据</span>
          </div>
        </div>

        <div class="deck-block points-block">
          <div class="deck-title">
            <span>点位速览</span>
            <span class="deck-hint">共 {{ quickPoints.length }} 处，点击定位</span>
          </div>
          <div class="quick-points">
            <button
              v-for="point in quickPoints"
              :key="point.id"
              type="button"
              class="quick-point"
              @click="selectPoint(point)"
            >
              <span class="qp-name">{{ point.hint }}</span>
              <span class="qp-meta">{{ [point.dynasty, point.location].filter(Boolean).join(' · ') || point.regionName }}</span>
            </button>
            <span v-if="!quickPoints.length" class="deck-empty">没有匹配的点位</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generateMapInsight, getMapPoints, getRegionModels } from '@/api/map'
import { usePageSearchStore } from '@/stores/pageSearch'
import * as echarts from 'echarts'
import chinaGeoJson from '@/assets/maps/china-geo.json'
import defaultThumb from '@/assets/maps/default-thumb.svg'

const pageSearchStore = usePageSearchStore()
// 关键词与范围由顶栏搜索驱动，本页不再自带输入框
const keyword = computed(() => pageSearchStore.keyword)
const searchScope = computed(() => pageSearchStore.scope)
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
const dynastyFilter = ref('')

/**
 * 朝代分布：直接按当前点位聚合，不额外请求接口。
 * 空朝代统一归到「未知」，避免出现空白标签。
 */
const dynastyStats = computed(() => {
  const counter = new Map()
  for (const point of filteredPoints.value) {
    const name = (point.dynasty || '').trim() || '未知'
    counter.set(name, (counter.get(name) || 0) + 1)
  }
  return Array.from(counter, ([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 10)
})

/** 速览列表：朝代筛选优先，其次跟随搜索关键词，默认取前 12 个 */
const quickPoints = computed(() => {
  let list = filteredPoints.value
  if (dynastyFilter.value) {
    list = list.filter(point => ((point.dynasty || '').trim() || '未知') === dynastyFilter.value)
  }
  return list.slice(0, 12)
})

const toggleDynastyFilter = (name) => {
  dynastyFilter.value = dynastyFilter.value === name ? '' : name
}

/** 筛选标签上显示当前范围的中文名 */
const scopeLabel = computed(() => {
  switch (searchScope.value) {
    case 'model': return '建筑名'
    case 'dynasty': return '朝代'
    case 'location': return '地点'
    default: return '关键词'
  }
})

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
    const resp = await getMapPoints(keyword.value.trim(), searchScope.value)
    const payload = resp.data?.data || {}
    points.value = payload.points || []
    filteredPoints.value = payload.points || []
    compliance.value = payload.compliance || compliance.value

    if (activeRegionCode.value) {
      const matchedPoint = points.value.find(point => point.regionCode === activeRegionCode.value)
      if (matchedPoint) {
        activePoint.value = matchedPoint
        activeRegionName.value = matchedPoint.regionName || activeRegionName.value
      } else {
        // 过滤后当前区域已无点位，收起右侧面板免得展示不相关内容
        clearSelection()
      }
    }

    renderMap()
    await refreshActiveRegionModels()
  } catch (error) {
    ElMessage.error('加载地图点位失败')
  }
}

/** 清除关键词筛选（保留已选区域） */
const clearKeywordFilter = () => {
  pageSearchStore.reset()
  loadPoints()
}

/** 一次性清掉关键词与区域选择 */
/**
 * 一键重置：清掉关键词、收起右侧区域、把地图视角也拉回初始缩放。
 * 只清数据不重置视图的话，用户还会停在之前放大的位置，达不到"重新开始"的效果。
 */
const clearAllFilters = async () => {
  pageSearchStore.reset()
  dynastyFilter.value = ''
  activePoint.value = null
  activeRegionCode.value = ''
  activeRegionName.value = ''
  regionModels.value = []
  await loadPoints()
  // loadPoints 内部会调 renderMap，此时 activePoint 已清空，zoom 会自动回到 1.08
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
  // 右侧面板撑开会让地图变窄，等 CSS 过渡结束再 resize，否则画布尺寸对不上
  resizeMapAfterLayout()
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
  resizeMapAfterLayout()
}

/** 栅格列宽有 340ms 过渡，地图必须在这之后重新量一次尺寸 */
const resizeMapAfterLayout = () => {
  window.setTimeout(() => mapChart?.resize(), 360)
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

// 顶栏搜索提交后（tick 变化）重新拉点位
watch(() => pageSearchStore.tick, async () => {
  await loadPoints()
  // 关键词变了，之前选中的区域可能已不在结果里，由 loadPoints 内部判断处理
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

  // 搜索已并入顶栏，这里只保留筛选状态回显条
  .filter-hint {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    padding: 10px 14px;
    border-radius: 12px;

    .hint-label {
      color: var(--v2-muted);
      font-size: 13px;
    }

    .hint-count {
      color: var(--v2-muted);
      font-size: 13px;
      margin-left: auto;
      white-space: nowrap;
    }

    :deep(.el-tag) {
      color: #1f2937 !important;
      background: #f1f5f9 !important;
      border-color: rgba(148, 163, 184, 0.45) !important;
      font-weight: 600;
    }
  }

  .stage {
    display: grid;
    grid-template-columns: minmax(0, 1.45fr) minmax(300px, 1fr);
    gap: 14px;
    min-height: 620px;
    // 右侧面板与速览区切换时让地图平滑变宽/变窄
    transition: grid-template-columns 340ms cubic-bezier(.2,.8,.2,1);

    // 选中区域时右侧是模型卡片，未选中时是速览区，
    // 两种情况宽度一致，地图尺寸不跳变
    &.no-panel {
      grid-template-columns: minmax(0, 1.45fr) minmax(300px, 1fr);
    }
  }

  .insight-deck {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 14px 16px;
    border-radius: var(--v2-radius-xl);
    // 跟地图一致用 min-height 对齐基线高度，再用 max-height 防止内容把整块撑爆；
    // 内部列表自己滚动，保证整页在视口内不用滚
    min-height: 620px;
    max-height: 620px;
    overflow: hidden;
    animation: deck-in 320ms cubic-bezier(.2,.8,.2,1);

    .deck-block {
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    // 速览占满剩余高度，自己内部滚动，不让整页跟着变长
    .points-block {
      flex: 1;
      min-height: 0;
    }

    .deck-title {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 10px;
      color: var(--v2-text);
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.4px;

      .deck-hint {
        color: var(--v2-muted);
        font-size: 12px;
        font-weight: 400;
      }
    }

    .deck-empty {
      color: var(--v2-muted);
      font-size: 12px;
    }

    .dynasty-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .dynasty-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 999px;
      cursor: pointer;
      color: var(--v2-text-dim);
      font-size: 12px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.1);
      transition: all 180ms ease;

      &:hover {
        border-color: rgba(140, 234, 255, 0.4);
        color: var(--v2-text);
        transform: translateY(-1px);
      }

      &.active {
        color: #0b1524;
        background: linear-gradient(180deg, rgba(214, 183, 122, 0.95), rgba(190, 158, 98, 0.95));
        border-color: transparent;
        box-shadow: 0 6px 18px rgba(214, 183, 122, 0.3);
      }

      .chip-count {
        font-variant-numeric: tabular-nums;
        opacity: 0.75;
      }
    }

    .quick-points {
      display: flex;
      flex-direction: column;
      gap: 8px;
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      padding-right: 4px;
    }

    .quick-point {
      display: flex;
      flex-direction: column;
      gap: 3px;
      padding: 9px 12px;
      text-align: left;
      border-radius: 12px;
      cursor: pointer;
      background: rgba(8, 16, 28, 0.5);
      border: 1px solid rgba(255, 255, 255, 0.08);
      transition: all 200ms ease;

      &:hover {
        transform: translateY(-2px);
        border-color: rgba(140, 234, 255, 0.42);
        box-shadow: 0 12px 26px rgba(12, 34, 60, 0.4);
      }

      .qp-name {
        color: var(--v2-text);
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .qp-meta {
        color: var(--v2-muted);
        font-size: 11px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
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
    // 和地图对齐高度，内容多了在内部滚动，别把整页顶长
    min-height: 620px;
    max-height: 620px;
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
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
      // 让内部卡片能根据自身宽度切换横排/竖排
      container-type: inline-size;
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

      // 面板宽度不够时改成上下排，否则右侧文字会被挤成一列单字
      @container (max-width: 340px) {
        grid-template-columns: 1fr;
      }

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

        @container (max-width: 340px) {
          width: 100%;
          height: 120px;
        }
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

@keyframes deck-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
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

// 只有真正窄屏（平板竖屏以下）才堆成单列；
// 1200px 会把常见的笔记本窗口也打成单列，右侧内容就被挤到地图下面了
@media (max-width: 900px) {
  .map-explorer .stage,
  .map-explorer .stage.no-panel {
    grid-template-columns: 1fr;
  }
}
</style>

