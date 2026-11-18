<template>
  <div class="map-explorer">
    <!--
      筛选条收成一条椭圆胶囊放在地图上方。
      默认只显示摘要；点「筛选」后向下展开朝代分布与点位列表，
      点其中一个结果＝在地图上定位该点位。
    -->
    <div class="filter-capsule v2-glass" :class="{ 'is-open': filterOpen }">
      <button type="button" class="capsule-summary" @click="toggleFilter">
        <el-icon class="capsule-icon"><Filter /></el-icon>
        <span class="capsule-label">筛选</span>
        <span class="capsule-badge" v-if="activeFilterCount">{{ activeFilterCount }}</span>
        <span class="capsule-divider" aria-hidden="true"></span>
        <span class="capsule-status">
          <template v-if="keyword.trim()">{{ scopeLabel }}：{{ keyword }}</template>
          <template v-else>全部朝代 · 共 {{ points.length }} 处点位</template>
        </span>
        <el-icon class="capsule-caret" :class="{ 'is-up': filterOpen }"><ArrowDown /></el-icon>
      </button>

      <div class="capsule-actions" v-if="activeFilterCount">
        <el-button text size="small" @click.stop="clearAllFilters">重置</el-button>
      </div>

      <!-- 展开区：朝代胶囊 + 匹配结果，逐个「丝滑」进入 -->
      <transition name="capsule-expand">
        <div class="capsule-panel" v-if="filterOpen">
          <div class="capsule-section">
            <div class="section-title">
              <span>朝代分布</span>
              <span class="section-hint">点击筛选</span>
            </div>
            <div class="dynasty-chips">
              <button
                v-for="(item, idx) in dynastyStats"
                :key="item.name"
                type="button"
                class="dynasty-chip"
                :class="{ active: dynastyFilter === item.name }"
                :style="{ '--i': idx }"
                @click="toggleDynastyFilter(item.name)"
              >
                <span class="chip-name">{{ item.name }}</span>
                <span class="chip-count">{{ item.count }}</span>
              </button>
              <span v-if="!dynastyStats.length" class="deck-empty">暂无数据</span>
            </div>
          </div>

          <div class="capsule-section results-section">
            <div class="section-title">
              <span>匹配点位</span>
              <span class="section-hint">{{ quickPoints.length }} 处，点击定位</span>
            </div>
            <div class="quick-points">
              <button
                v-for="(point, idx) in quickPoints"
                :key="point.id"
                type="button"
                class="quick-point"
                :style="{ '--i': idx }"
                @click="selectPointFromFilter(point)"
              >
                <span class="qp-name">{{ point.hint }}</span>
                <span class="qp-meta">
                  {{ [point.dynasty, point.location].filter(Boolean).join(' · ') || point.regionName }}
                </span>
              </button>
              <span v-if="!quickPoints.length" class="deck-empty">没有匹配的点位</span>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <div class="stage">
      <div class="map-board v2-glass-strong">
        <div class="map-chart" ref="mapChartRef"></div>

        <!-- 点击节点后，卡片直接浮在地图上方，不再挤占右侧；标题栏可拖动改变位置 -->
        <transition name="float-card">
          <div
            class="map-float-card v2-glass-strong"
            v-if="activeRegionCode"
            :class="{ 'is-dragging': cardDragging, 'has-custom-pos': cardPos.x !== null }"
            :style="cardStyle"
            ref="floatCardRef"
          >
            <div
              class="float-card-header"
              @pointerdown="startCardDrag"
            >
              <div class="float-title">
                <h3>{{ activeRegionName }}</h3>
                <span class="float-count">{{ regionModels.length }} 个模型</span>
              </div>
              <div class="float-card-actions">
                <!-- 拖动提示：鼠标移到标题栏才知道能拖 -->
                <span class="drag-hint" aria-hidden="true">
                  <el-icon><Rank /></el-icon>
                </span>
                <el-button text circle size="small" @click="clearSelection">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </div>

            <div class="float-card-body" v-loading="regionLoading">
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

        <div class="compliance">
          <div>地图来源：{{ compliance.source }}</div>
          <div>审图号：{{ compliance.reviewNo }}（{{ compliance.reviewYear }}）</div>
          <div v-if="!compliance.configured" class="warn">提示：审图号未配置，此页面不可用于正式发布。</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Close, Filter, Rank } from '@element-plus/icons-vue'
import { generateMapInsight, getMapPoints, getRegionModels } from '@/api/map'
import { usePageSearchStore } from '@/stores/pageSearch'
import { useUiPreferences } from '@/composables/useUiPreferences'
import * as echarts from 'echarts'
import chinaGeoJson from '@/assets/maps/china-geo.json'
import defaultThumb from '@/assets/maps/default-thumb.svg'

const pageSearchStore = usePageSearchStore()
// 关键词与范围由顶栏搜索驱动，本页不再自带输入框
const keyword = computed(() => pageSearchStore.keyword)
const searchScope = computed(() => pageSearchStore.scope)
const points = ref([])
const filteredPoints = ref([])
// 顶部胶囊筛选条的展开状态，默认收起只留一行摘要
const filterOpen = ref(false)
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

/*
  节点卡片拖拽。
  卡片默认贴右上角（CSS 定位），一旦用户拖过就切成 left/top 绝对坐标，
  再拖回来会被 clamp 限制在容器内，永远不会有一半跑到地图外面去。
*/
const floatCardRef = ref(null)
const cardDragging = ref(false)
const cardPos = ref({ x: null, y: null })
const dragState = { pointerId: null, offsetX: 0, offsetY: 0 }

// 拖动时用 style 直接给 left/top，其余情况交给 CSS 的 right/top 默认值
const cardStyle = computed(() => {
  if (cardPos.value.x === null || cardPos.value.y === null) return {}
  return { left: `${cardPos.value.x}px`, top: `${cardPos.value.y}px`, right: 'auto' }
})

/** 把卡片位置限制在地图容器内，留 8px 边距避免贴边 */
const clampCardPosition = (x, y) => {
  const card = floatCardRef.value
  const board = mapChartRef.value?.parentElement
  if (!card || !board) return { x, y }
  const maxX = Math.max(0, board.clientWidth - card.offsetWidth - 8)
  const maxY = Math.max(0, board.clientHeight - card.offsetHeight - 8)
  return {
    x: Math.min(Math.max(8, x), maxX),
    y: Math.min(Math.max(8, y), maxY)
  }
}

const startCardDrag = (event) => {
  // 点在关闭按钮上时不要开始拖，否则点一下会连带位移
  if (event.target.closest('.el-button')) return
  if (event.button !== undefined && event.button !== 0) return
  const card = floatCardRef.value
  const board = mapChartRef.value?.parentElement
  if (!card || !board) return

  const cardRect = card.getBoundingClientRect()
  const boardRect = board.getBoundingClientRect()
  // 首次拖动时把 CSS 的 right 定位换算成 left，避免一开始跳位
  cardPos.value = { x: cardRect.left - boardRect.left, y: cardRect.top - boardRect.top }
  dragState.pointerId = event.pointerId
  dragState.offsetX = event.clientX - cardRect.left
  dragState.offsetY = event.clientY - cardRect.top
  cardDragging.value = true

  event.currentTarget.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', onCardDrag)
  window.addEventListener('pointerup', endCardDrag)
  window.addEventListener('pointercancel', endCardDrag)
}

const onCardDrag = (event) => {
  if (!cardDragging.value) return
  const board = mapChartRef.value?.parentElement
  if (!board) return
  const boardRect = board.getBoundingClientRect()
  const next = clampCardPosition(
    event.clientX - boardRect.left - dragState.offsetX,
    event.clientY - boardRect.top - dragState.offsetY
  )
  cardPos.value = next
}

const endCardDrag = () => {
  cardDragging.value = false
  window.removeEventListener('pointermove', onCardDrag)
  window.removeEventListener('pointerup', endCardDrag)
  window.removeEventListener('pointercancel', endCardDrag)
}

// 窗口尺寸变了卡片可能被挤出容器，重新收一次边界
const reclampCard = () => {
  if (cardPos.value.x === null) return
  cardPos.value = clampCardPosition(cardPos.value.x, cardPos.value.y)
}

/** 窄屏判断：点位文字标签在这个宽度下会严重重叠 */
const isNarrowScreen = () => typeof window !== 'undefined' && window.innerWidth <= 640

const onResize = () => {
  mapChart?.resize()
  reclampCard()
  // 跨过窄屏阈值时标签显隐要跟着变，光 resize 不会重算 label.show
  if (mapChart && lastNarrow !== isNarrowScreen()) {
    lastNarrow = isNarrowScreen()
    renderMap()
  }
}
let lastNarrow = isNarrowScreen()
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

/** 顶部胶囊的展开/收起 */
const toggleFilter = () => {
  filterOpen.value = !filterOpen.value
}

/** 胶囊上显示的数字：当前生效了几个筛选条件 */
const activeFilterCount = computed(() => {
  let n = 0
  if (keyword.value.trim()) n += 1
  if (dynastyFilter.value) n += 1
  if (activeRegionCode.value) n += 1
  return n
})

/**
 * 从筛选面板里点某个点位：定位到地图并收起面板。
 * 面板收起是为了让用户直接看到地图上的高亮，而不是还压着列表。
 */
const selectPointFromFilter = async (point) => {
  filterOpen.value = false
  await selectPoint(point)
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
  // 换了区域就把卡片放回默认位置，别让上一张卡片的位置带过来
  cardPos.value = { x: null, y: null }
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

/**
 * 从 CSS 变量读当前主题的地图配色。
 * ECharts 画在 canvas 上，没法继承 CSS，只能在渲染前把主题 token 取出来喂进去；
 * 切换主题时再调一次 renderMap 重绘即可。
 */
const readMapTheme = () => {
  const styles = getComputedStyle(document.documentElement)
  const read = (name, fallback) => (styles.getPropertyValue(name) || '').trim() || fallback
  return {
    area: read('--v2-map-area', 'rgba(20, 50, 84, 0.82)'),
    areaHover: read('--v2-map-area-hover', 'rgba(38, 100, 156, 0.92)'),
    border: read('--v2-map-border', 'rgba(101, 226, 255, 0.78)'),
    borderHover: read('--v2-map-border-hover', 'rgba(165, 240, 255, 0.95)'),
    glow: read('--v2-map-glow', 'rgba(77, 185, 255, 0.35)'),
    labelBg: read('--v2-map-label-bg', 'rgba(6, 14, 25, 0.78)'),
    labelText: read('--v2-map-label-text', '#f8f4ea'),
    link: read('--v2-map-link', 'rgba(108, 224, 255, 0.55)'),
    gold: read('--v2-gold', '#d2b06f'),
    cyan: read('--v2-cyan', '#5fc7b8')
  }
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

  const theme = readMapTheme()

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
        areaColor: theme.area,
        borderColor: theme.border,
        borderWidth: 1.3,
        shadowColor: theme.glow,
        shadowBlur: 10
      },
      emphasis: {
        itemStyle: {
          areaColor: theme.areaHover,
          borderColor: theme.borderHover,
          borderWidth: 1.6,
          shadowColor: theme.glow,
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
          color: theme.link,
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
          color: theme.cyan
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
          color: theme.gold,
          shadowBlur: 18,
          shadowColor: theme.gold
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
            color: theme.cyan,
            shadowBlur: 28,
            shadowColor: theme.cyan
          }
        },
        label: {
          /*
            窄屏下点位标签会挤成一团（375px 时几乎完全重叠），
            这时隐藏文字标签，只留发光点位；名称仍可通过点击卡片和 tooltip 查看。
          */
          show: !isNarrowScreen(),
          formatter: (params) => params?.data?.labelText || '',
          position: 'right',
          color: theme.labelText,
          fontSize: 12,
          backgroundColor: theme.labelBg,
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

// 切换深浅主题时 CSS 变量已换值，但 ECharts 画在 canvas 上不跟随，必须重绘
const { theme } = useUiPreferences()
watch(theme, () => {
  renderMap()
})

onUnmounted(() => {
  stopPolling()
  endCardDrag()
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
  gap: 12px;
  /*
    高度撑满可用空间，避免底部留白。
    .content 是 flex:1 的可滚动容器，这里用 flex 自身撑满而不是算 vh：
    既不依赖导航形态（侧边栏/顶栏都不会算错），也不会在 1080p 屏上因为
    写死 720px 而在下方空出一条。
  */
  flex: 1 1 auto;
  height: 100%;
  min-height: 480px;

  /* ===== 顶部筛选胶囊 ===== */
  .filter-capsule {
    flex: none;
    position: relative;
    border-radius: 28px;
    padding: 0;
    overflow: hidden;
    transition: border-radius 320ms cubic-bezier(.2,.8,.2,1);

    // 展开时圆角变小，视觉上从「一条」变成「一块」
    &.is-open {
      border-radius: 20px;
    }

    .capsule-summary {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 11px 18px;
      background: transparent;
      border: none;
      cursor: pointer;
      color: var(--v2-text-strong);
      font-size: 13px;
      text-align: left;

      .capsule-icon {
        font-size: 15px;
        color: var(--v2-gold);
        flex: none;
      }

      .capsule-label {
        font-weight: 700;
        letter-spacing: 0.4px;
        flex: none;
      }

      .capsule-badge {
        min-width: 18px;
        height: 18px;
        padding: 0 5px;
        border-radius: 999px;
        background: var(--v2-gold);
        color: var(--v2-accent-contrast);
        font-size: 11px;
        font-weight: 800;
        line-height: 18px;
        text-align: center;
        flex: none;
      }

      .capsule-divider {
        width: 1px;
        height: 14px;
        background: var(--v2-divider);
        flex: none;
      }

      .capsule-status {
        flex: 1;
        min-width: 0;
        /* 这行叠在半透明玻璃上，用正文色而不是弱化色，保证深色主题下也够清楚 */
        color: var(--v2-text-body);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .capsule-caret {
        flex: none;
        font-size: 13px;
        color: var(--v2-text-weak);
        transition: transform 300ms cubic-bezier(.2,.8,.2,1);

        &.is-up {
          transform: rotate(180deg);
        }
      }

      &:hover .capsule-caret {
        color: var(--v2-text-strong);
      }
    }

    .capsule-actions {
      position: absolute;
      top: 6px;
      right: 46px;
      z-index: 2;
    }

    .capsule-panel {
      padding: 0 18px 16px;
      display: flex;
      flex-direction: column;
      gap: 14px;
      max-height: 46vh;
      overflow-y: auto;

      &::-webkit-scrollbar {
        width: 6px;
      }

      &::-webkit-scrollbar-thumb {
        border-radius: 999px;
        background: var(--v2-chip-border);
      }
    }

    .capsule-section {
      display: flex;
      flex-direction: column;
      gap: 9px;
    }

    .section-title {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 10px;
      color: var(--v2-text-strong);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.3px;

      .section-hint {
        color: var(--v2-text-weak);
        font-size: 11px;
        font-weight: 400;
      }
    }

    .deck-empty {
      color: var(--v2-text-weak);
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
      color: var(--v2-text-body);
      font-size: 12px;
      background: var(--v2-surface-soft);
      border: 1px solid var(--v2-border-soft);
      transition: all 180ms ease;
      // 逐项错开入场，形成瀑布感
      animation: chip-in 320ms cubic-bezier(.2,.8,.2,1) both;
      animation-delay: calc(var(--i, 0) * 28ms);

      &:hover {
        border-color: var(--v2-chip-border);
        color: var(--v2-text-strong);
        transform: translateY(-1px);
      }

      &.active {
        color: var(--v2-accent-contrast);
        background: linear-gradient(180deg, var(--v2-gold), color-mix(in srgb, var(--v2-gold) 82%, #000));
        border-color: transparent;
        box-shadow: 0 6px 18px color-mix(in srgb, var(--v2-gold) 34%, transparent);
      }

      .chip-count {
        font-variant-numeric: tabular-nums;
        opacity: 0.75;
      }
    }

    .quick-points {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 8px;
    }

    .quick-point {
      display: flex;
      flex-direction: column;
      gap: 3px;
      padding: 9px 12px;
      text-align: left;
      border-radius: 12px;
      cursor: pointer;
      background: var(--v2-surface-soft);
      border: 1px solid var(--v2-border-soft);
      transition: all 200ms ease;
      animation: chip-in 340ms cubic-bezier(.2,.8,.2,1) both;
      animation-delay: calc(var(--i, 0) * 24ms);

      &:hover {
        transform: translateY(-2px);
        border-color: var(--v2-chip-border);
        box-shadow: var(--v2-shadow-soft);
      }

      .qp-name {
        color: var(--v2-text-strong);
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .qp-meta {
        color: var(--v2-text-weak);
        font-size: 11px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }

  /* ===== 地图区域：铺满剩余空间 ===== */
  .stage {
    flex: 1;
    min-height: 0;
    display: flex;
  }

  .map-board {
    position: relative;
    flex: 1;
    min-width: 0;
    border-radius: var(--v2-radius-xl);
    overflow: hidden;

    .map-chart {
      width: 100%;
      height: 100%;
      /* 地图底色跟随主题：深色是墨玉夜观，浅色是宣纸舆图，避免浅色界面里嵌一块黑底 */
      background: var(--v2-map-bg);
      transition: background 320ms ease;
    }

    &::before {
      content: '';
      position: absolute;
      inset: -1px;
      border-radius: inherit;
      pointer-events: none;
      border: 1px solid var(--v2-border-soft);
      box-shadow: inset 0 0 30px color-mix(in srgb, var(--v2-gold) 10%, transparent);
      z-index: 1;
    }

    .compliance {
      position: absolute;
      right: 14px;
      bottom: 12px;
      z-index: 3;
      font-size: 11px;
      line-height: 1.5;
      color: var(--v2-map-compliance);
      text-align: right;

      .warn {
        color: var(--v2-vermillion);
      }
    }
  }

  /* ===== 点击节点后浮在地图上方的卡片 ===== */
  .map-float-card {
    position: absolute;
    top: 16px;
    right: 16px;
    /* 卡片浮在地图右侧，窄屏时按视口收缩，避免顶出边界 */
    width: min(340px, calc(100% - 32px));
    max-height: calc(100% - 32px);
    display: flex;
    flex-direction: column;
    border-radius: 18px;
    z-index: 5;
    box-shadow: var(--v2-shadow);
    /* 用户拖动过之后位置交给 left/top，这里不参与过渡，否则拖起来会「粘手」 */
    transition: box-shadow 200ms ease;

    &.is-dragging {
      box-shadow: var(--v2-shadow), 0 0 0 2px var(--v2-gold);
      user-select: none;
    }

    &.has-custom-pos {
      right: auto;
    }

    .float-card-header {
      flex: none;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 10px;
      padding: 14px 14px 10px;
      border-bottom: 1px solid var(--v2-divider);
      /* 标题栏＝拖拽把手，让用户一眼看出这块可以拖 */
      cursor: grab;
      touch-action: none;

      &:active {
        cursor: grabbing;
      }

      .float-title {
        min-width: 0;

        h3 {
          margin: 0;
          color: var(--v2-text-strong);
          font-size: 15px;
          font-weight: 700;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .float-count {
          display: inline-block;
          margin-top: 3px;
          color: var(--v2-text-weak);
          font-size: 11px;
        }
      }

      .float-card-actions {
        display: flex;
        align-items: center;
        gap: 2px;
        flex: none;
      }

      .drag-hint {
        display: inline-flex;
        align-items: center;
        color: var(--v2-text-weak);
        opacity: 0.55;
        transition: opacity 180ms ease;
      }

      &:hover .drag-hint {
        opacity: 1;
        color: var(--v2-gold);
      }
    }

    .float-card-body {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      // 让卡片内部按自身宽度决定排版
      container-type: inline-size;

      &::-webkit-scrollbar {
        width: 6px;
      }

      &::-webkit-scrollbar-thumb {
        border-radius: 999px;
        background: var(--v2-chip-border);
      }
    }

    .model-card {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 10px;
      border-radius: 14px;
      border: 1px solid var(--v2-border-soft);
      background: var(--v2-surface-soft);
      transition: transform 280ms ease, box-shadow 280ms ease, border-color 280ms ease;

      &:hover {
        transform: translateY(-3px);
        border-color: var(--v2-chip-border);
        box-shadow: var(--v2-shadow-soft);
      }

      img {
        width: 100%;
        height: 120px;
        object-fit: cover;
        border-radius: 10px;
        border: 1px solid var(--v2-border-soft);
      }

      h4 {
        color: var(--v2-text-strong);
        font-size: 13px;
        margin: 0;
      }

      .sub {
        margin: 3px 0 5px;
        color: var(--v2-text-weak);
        font-size: 11px;
      }

      p {
        color: var(--v2-text-body);
        line-height: 1.6;
        font-size: 11px;
        white-space: pre-wrap;
        margin: 0;
      }

      .links {
        margin-top: 6px;
        display: flex;
        flex-direction: column;
        gap: 3px;

        a {
          color: var(--v2-gold);
          font-size: 11px;

          &:hover {
            text-decoration: underline;
          }
        }
      }
    }
  }
}

/* 胶囊展开：高度与透明度一起过渡，避免生硬跳变 */
.capsule-expand-enter-active,
.capsule-expand-leave-active {
  transition: opacity 260ms cubic-bezier(.2,.8,.2,1), transform 260ms cubic-bezier(.2,.8,.2,1);
  transform-origin: top center;
}

.capsule-expand-enter-from,
.capsule-expand-leave-to {
  opacity: 0;
  transform: translateY(-8px) scaleY(0.96);
}

/* 地图卡片：从右上角浮入 */
.float-card-enter-active,
.float-card-leave-active {
  transition: opacity 240ms cubic-bezier(.2,.8,.2,1), transform 240ms cubic-bezier(.2,.8,.2,1);
}

.float-card-enter-from,
.float-card-leave-to {
  opacity: 0;
  transform: translate3d(16px, -8px, 0) scale(0.96);
}

@keyframes chip-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 窄屏：卡片改到底部横向铺开，避免遮住地图中心 */
@media (max-width: 900px) {
  .map-explorer {
    min-height: 0;

    .map-board {
      min-height: 460px;
    }

    /*
      窄屏下卡片不再浮在地图上，而是排到地图下方参与文档流。
      这时候拖动没有意义，所以把定位和把手一起关掉，
      否则行内 left/top 会把卡片顶出容器。
    */
    .map-float-card {
      position: static !important;
      width: auto;
      max-height: none;
      margin-top: 12px;
      left: auto !important;
      top: auto !important;

      .float-card-header {
        cursor: default;

        .drag-hint {
          display: none;
        }
      }
    }

    .stage {
      flex-direction: column;
    }
  }
}
</style>

