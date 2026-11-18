<template>
  <div class="kb-graph">
    <!-- 顶部工具条：视图参数都在这里，不藏进弹窗 -->
    <div class="graph-toolbar">
      <div class="tool-left">
        <el-input
          v-model="searchKey"
          placeholder="搜索模型名…"
          clearable
          class="graph-search"
          @keyup.enter="focusByName"
          @clear="clearFocus"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select
          v-model="filterCategory"
          placeholder="全部分类"
          clearable
          class="graph-cat"
          @change="reload"
        >
          <el-option
            v-for="c in categoryOptions"
            :key="c.name"
            :label="c.name"
            :value="c.name"
          />
        </el-select>
      </div>

      <div class="tool-right">
        <el-radio-group v-model="layoutMode" size="small" @change="applyLayout">
          <el-radio-button label="force">力导向</el-radio-button>
          <el-radio-button label="circular">环形</el-radio-button>
        </el-radio-group>

        <el-tooltip content="展开/收起全部模型的知识类型节点" placement="bottom">
          <el-switch
            v-model="showTypes"
            active-text="显示知识节点"
            @change="reload"
          />
        </el-tooltip>

        <el-button size="small" @click="resetView">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 图例：色块 + 分类名，点击可单独聚焦该分类 -->
    <div class="graph-legend" v-if="categories.length">
      <button
        v-for="c in categories"
        :key="c.name"
        class="legend-item"
        :class="{ dimmed: filterCategory && filterCategory !== c.name }"
        @click="toggleCategory(c.name)"
      >
        <span class="legend-dot" :style="{ background: c.color }"></span>
        <span class="legend-name">{{ c.name }}</span>
      </button>
    </div>

    <!-- 画布容器。用 div 而不是 canvas，ECharts 自己会插 canvas 进去 -->
    <div class="graph-canvas-wrap">
      <div ref="chartRef" class="graph-canvas"></div>

      <!-- 首屏加载态：避免一片空白让人以为坏了 -->
      <div v-if="loading" class="graph-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在铺陈知识谱系…</span>
      </div>

      <!-- 悬停提示：ECharts 自带的 tooltip 在中文长名下会溢出，这里自己控制 -->
      <div
        v-if="hoverNode"
        class="graph-tip"
        :style="{ left: tipPos.x + 'px', top: tipPos.y + 'px' }"
      >
        <div class="tip-title">{{ hoverNode.name }}</div>
        <div class="tip-meta" v-if="hoverNode.nodeType === 'model'">
          {{ hoverNode.entryCount }} 条知识
          <span v-if="hoverNode.hitCount"> · 命中 {{ hoverNode.hitCount }} 次</span>
        </div>
        <div class="tip-meta" v-else-if="hoverNode.nodeType === 'category'">
          {{ hoverNode.value }} 个模型
        </div>
        <div class="tip-meta" v-else-if="hoverNode.questionType">
          {{ hoverNode.entryCount || 0 }} 条 · 命中 {{ hoverNode.hitCount || 0 }}
        </div>
      </div>

      <!-- 空态 -->
      <div v-if="!loading && nodes.length === 0" class="graph-empty">
        <el-icon><Share /></el-icon>
        <p>当前条件下没有可展示的知识</p>
      </div>
    </div>

    <!-- 右下角统计：让运营一眼看到体量 -->
    <div class="graph-footer">
      <span>节点 <b>{{ visibleNodes.length }}</b><template v-if="visibleNodes.length !== nodes.length">/{{ nodes.length }}</template></span>
      <span>连线 <b>{{ visibleLinks.length }}</b></span>
      <span v-if="stats.models">模型 <b>{{ stats.models }}</b></span>
      <span v-if="stats.entries">知识 <b>{{ stats.entries }}</b></span>
      <span v-if="stats.totalHits">累计命中 <b>{{ stats.totalHits }}</b></span>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Loading, Share } from '@element-plus/icons-vue'
import { getKnowledgeGraph, expandGraphNode } from '@/api/knowledge'
import { useUiPreferences } from '@/composables/useUiPreferences'

const emit = defineEmits(['open-node'])

const chartRef = ref(null)
// 用 shallowRef 存 ECharts 实例：它内部状态庞大且不该被 Vue 追踪，
// 用 ref 会触发深度代理，图谱拖拽时会明显掉帧。
const chart = shallowRef(null)
let resizeObserver = null

const loading = ref(false)
const nodes = ref([])
const links = ref([])
const categories = ref([])
const stats = ref({})

const categoryOptions = ref([])
const searchKey = ref('')
const filterCategory = ref(null)
const showTypes = ref(false)
const layoutMode = ref('force')

const hoverNode = ref(null)
const tipPos = ref({ x: 0, y: 0 })

// 已展开的模型，避免重复请求
const expandedModels = new Set()

/** 把后端数据转成 ECharts 需要的结构 */
/**
 * 从主题变量读画布配色。
 * ECharts 画在 canvas 上不继承 CSS，节点/标签/混色底都得手动喂进去。
 */
function readGraphTheme() {
  const styles = getComputedStyle(document.documentElement)
  const read = (name, fallback) => (styles.getPropertyValue(name) || '').trim() || fallback
  return {
    // 混色基准：深色往墨底混、浅色往宣纸底混，节点才会自然淡下去
    base: read('--v2-map-area', 'rgba(253, 250, 244, 0.9)'),
    baseRgb: read('--v2-graph-base-rgb', '253, 250, 244'),
    label: read('--v2-text-body', '#5d5145'),
    labelOnNode: read('--v2-accent-contrast', '#fdfaf4'),
    hot: read('--v2-gold', '#c9a227'),
    shadow: read('--v2-map-glow', 'rgba(120, 95, 65, 0.16)')
  }
}

function toOption() {
  const theme = readGraphTheme()
  // 用 visible* 而不是原始数据：分类筛选在前端过滤，不重新请求后端
  const modelCount = visibleNodes.value.filter((n) => n.nodeType === 'model').length
  // 节点一多标签必然互相压住，只给分类节点显示常驻标签，其余靠悬停看
  const dense = visibleNodes.value.length > 150

  const nodeData = visibleNodes.value.map((n, idx) => {
    const cat = categories.value[n.category]
    const color = cat?.color || '#8f8880'
    const isCat = n.nodeType === 'category'
    const isType = n.nodeType === 'questionType'

    // 分类节点太大就会吃掉邻居空间，"未分类"有 30 个模型，必须限制上限
    let size = n.symbolSize || 20
    if (isCat) size = Math.min(62, 40 + (n.value || 0) * 0.55)

    // 常驻标签策略：分类必显；模型只在节点少时显示，密集时靠悬停看。
    // 66 个模型全显标签在 1400x630 的画布上必然互相压住，读不出来的标签等于没有。
    const showLabel = isCat || (!dense && !isType)

    return {
      id: n.name,
      name: n.name,
      symbolSize: size,
      category: n.category,
      value: n.value,
      symbol: isType ? 'diamond' : 'circle',
      itemStyle: {
        // 分类实色，模型和类型用混色后淡一档，形成层次
        color: isCat ? color : blendWithBase(color, isType ? 0.42 : 0.18, theme.baseRgb),
        borderColor: n.hot ? theme.hot : color,
        borderWidth: n.hot ? 2.5 : isCat ? 1.5 : 1.1,
        shadowBlur: isCat ? 10 : 0,
        shadowColor: theme.shadow
      },
      label: {
        show: showLabel,
        fontSize: isCat ? 12 : 10.5,
        position: isCat ? 'inside' : 'right',
        distance: isCat ? 0 : 5,
        // 分类标签放圆内用反色，模型标签放右侧用正文色
        color: isCat ? theme.labelOnNode : theme.label,
        fontWeight: isCat ? 600 : 400,
        // 用换行截断而不是省略号：横排长名更容易压到邻居
        formatter: (p) => wrapName(p.name, isCat ? 5 : 8)
      },
      raw: n
    }
  })

  const linkData = visibleLinks.value.map((l) => {
    const isCatLink = categories.value.some((c) => c.name === l.source)
    return {
      source: l.source,
      target: l.target,
      value: l.value,
      lineStyle: {
        width: isCatLink ? 1.2 : 0.8,
        opacity: isCatLink ? 0.3 : 0.22
      }
    }
  })

  // 节点越多斥力要越大，否则会叠成一坨；
  // edgeLength 同时要拉长，给标签留出间距
  const denseForce = modelCount > 40
  return {
    backgroundColor: 'transparent',
    animationDuration: 1200,
    animationEasingUpdate: 'cubicOut',
    tooltip: { show: false },
    series: [
      {
        type: 'graph',
        // 力导向是 ECharts 最成熟的能力。之前试过自己算固定坐标（layout:'none'），
        // 结果始终和 ECharts 的坐标变换打架——节点不是挤在角落就是越界。
        // 放弃自绘，把布局交给它，只调参数。
        layout: layoutMode.value === 'circular' ? 'circular' : 'force',
        roam: true,
        draggable: true,
        force: {
          // 力导向是 ECharts 最成熟的能力，调好这两个参数就能自然分簇：
          // 斥力决定铺开程度，重力把整体拉回中心防止飘散。
          repulsion: dense ? 420 : 300,
          edgeLength: dense ? [50, 100] : [65, 125],
          gravity: 0.12,
          friction: 0.16,
          layoutAnimation: true
        },
        circular: { rotateLabel: true },
        categories: categories.value.map((c) => ({ name: c.name })),
        data: nodeData,
        links: linkData,
        emphasis: {
          focus: 'adjacency',
          scale: 1.08,
          itemStyle: { borderWidth: 3 },
          lineStyle: { width: 2.2, opacity: 0.8 },
          label: { show: true, fontSize: 12, fontWeight: 600 }
        },
        blur: {
          itemStyle: { opacity: 0.1 },
          lineStyle: { opacity: 0.03 },
          label: { show: false }
        },
        lineStyle: { color: 'source', curveness: 0.12 }
      }
    ]
  }
}
/** 长名折行，比如「重檐歇山顶大雄宝殿」→「重檐歇山顶\n大雄宝殿」 */
function wrapName(name, max) {
  if (!name) return ''
  if (name.length <= max) return name
  return name.slice(0, max) + '\n' + (name.length > max * 2 ? name.slice(max, max * 2) + '…' : name.slice(max))
}


/** 把分类色和主题底色混合，让模型节点比分类节点淡，形成层次 */
function blendWithBase(hex, ratio, baseRgb) {
  const base = (baseRgb || '253, 250, 244')
    .split(',')
    .map((v) => parseInt(v.trim(), 10))
  const c = hex.replace('#', '')
  const rgb = [parseInt(c.slice(0, 2), 16), parseInt(c.slice(2, 4), 16), parseInt(c.slice(4, 6), 16)]
  const out = rgb.map((v, i) => {
    const target = Number.isFinite(base[i]) ? base[i] : v
    return Math.round(v + (target - v) * ratio)
  })
  return `rgb(${out.join(',')})`
}

function shorten(text, max) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '…' : text
}

/** 让画布占满可视区剩余高度，图谱需要大面积才铺得开 */
function resizeCanvas() {
  if (!chart.value || !chartRef.value) return
  chart.value.resize()
}

function render() {
  if (!chart.value) return
  chart.value.setOption(toOption(), true)
  // 不做任何事后缩放：setOption 的 center/zoom 和 graphRoam 都会打乱 force 布局，
  // 让节点挤成一团。布局收敛后 ECharts 自己会处理视野。
}



/**
 * 分类筛选。
 *
 * 图例点击只在前端过滤，不重新请求后端：
 * 全量数据本来就已经在内存里（72 个节点），重新拉一遍既慢又会打乱布局。
 * 直接按分类索引过滤节点和连线，视觉上"其他分类淡出"就够了。
 */
const visibleNodes = computed(() => {
  if (!filterCategory.value) return nodes.value
  const keep = new Set()
  const catIdx = categories.value.findIndex((c) => c.name === filterCategory.value)
  nodes.value.forEach((n) => {
    if (n.category === catIdx) keep.add(n.name)
  })
  // 分类节点本身也要保留
  nodes.value.forEach((n) => {
    if (n.nodeType === 'category' && n.name === filterCategory.value) keep.add(n.name)
  })
  return nodes.value.filter((n) => keep.has(n.name))
})

const visibleLinks = computed(() => {
  if (!filterCategory.value) return links.value
  const names = new Set(visibleNodes.value.map((n) => n.name))
  return links.value.filter((l) => names.has(l.source) && names.has(l.target))
})

/** 拉取图谱数据 */
async function reload() {
  loading.value = true
  try {
    const res = await getKnowledgeGraph({
      // 分类筛选改在前端做（见 visibleNodes），这里永远拉全量，
      // 这样切分类时不用重新请求，也不会因为重新布局导致节点乱跳
      withTypes: showTypes.value
    })
    const d = res.data?.data || {}
    nodes.value = d.nodes || []
    links.value = d.links || []
    categories.value = d.categories || []
    stats.value = d.stats || {}
    expandedModels.clear()
    if (showTypes.value) {
      // 全展开时记录所有模型，避免点击时重复拉
      nodes.value.filter((n) => n.nodeType === 'model' || n.nodeType === 'questionType')
    }
    if (!categoryOptions.value.length && categories.value.length) {
      categoryOptions.value = categories.value.map((c) => ({ name: c.name }))
    }
    render()
  } catch (e) {
    // 拦截器已弹错误，这里只清空避免残留旧图
    nodes.value = []
    links.value = []
    render()
  } finally {
    loading.value = false
  }
}

function applyLayout() {
  render()
}

function resetView() {
  render()
  searchKey.value = ''
  filterCategory.value = null
  clearFocus()
}

function toggleCategory(name) {
  filterCategory.value = filterCategory.value === name ? null : name
  // 只重绘，不重新请求：数据已在内存，重新拉会打乱布局
  render()
}

/** 按名字搜索并聚焦。找到就把视图移过去，找不到给提示 */
function focusByName() {
  const kw = searchKey.value.trim()
  if (!kw) return
  const target = nodes.value.find(
    (n) => n.name.includes(kw) && n.nodeType !== 'questionType'
  )
  if (!target) {
    ElMessage.warning(`没有找到包含「${kw}」的模型`)
    return
  }
  highlightNode(target.name)
}

function highlightNode(name) {
  if (!chart.value) return
  chart.value.dispatchAction({ type: 'downplay', seriesIndex: 0 })
  chart.value.dispatchAction({ type: 'highlight', seriesIndex: 0, name })
}

function clearFocus() {
  if (!chart.value) return
  chart.value.dispatchAction({ type: 'downplay', seriesIndex: 0 })
}

/** 点模型节点：没展开就拉它的知识类型挂上去 */
async function handleNodeClick(node) {
  const raw = node?.data?.raw
  if (!raw) return

  if (raw.nodeType === 'category') {
    // 点分类：筛选到该分类
    filterCategory.value = filterCategory.value === raw.name ? null : raw.name
    render()
    return
  }

  if (raw.nodeType === 'questionType') {
    emit('open-node', { modelId: raw.modelId, questionType: raw.questionType })
    return
  }

  // 模型节点：展开 / 收起它的知识
  if (showTypes.value) {
    // 全展开模式下不再单独展开，直接跳转到该模型的筛选视图
    emit('open-node', { modelId: raw.modelId, questionType: null })
    return
  }

  if (expandedModels.has(raw.modelId)) {
    // 收起：移除该模型的知识节点
    const prefix = raw.name + ' · '
    const removeNames = new Set(
      nodes.value.filter((n) => n.nodeType === 'questionType' && n.name.startsWith(prefix))
        .map((n) => n.name)
    )
    if (removeNames.size) {
      nodes.value = nodes.value.filter((n) => !removeNames.has(n.name))
      links.value = links.value.filter((l) => !removeNames.has(l.target))
      expandedModels.delete(raw.modelId)
      render()
    }
    return
  }

  try {
    const res = await expandGraphNode(raw.modelId)
    const d = res.data?.data || {}
    const newNodes = (d.nodes || []).map((n) => ({ ...n, category: raw.category }))
    const newLinks = d.links || []
    if (!newNodes.length) {
      ElMessage.info('该模型暂无已通过的知识')
      return
    }
    nodes.value = [...nodes.value, ...newNodes]
    links.value = [...links.value, ...newLinks]
    expandedModels.add(raw.modelId)
    render()
    // 展开后高亮新节点，让人看到发生了什么
    setTimeout(() => {
      newNodes.forEach((n) => {
        try { chart.value?.dispatchAction({ type: 'highlight', seriesIndex: 0, name: n.name }) } catch (e) { /* 忽略 */ }
      })
    }, 120)
  } catch (e) {
    // 拦截器已提示
  }
}

/** 自绘 tooltip：跟随鼠标，避开右下角 */
function bindEvents() {
  if (!chart.value) return

  chart.value.on('click', (p) => {
    if (p.dataType === 'node') handleNodeClick(p)
  })

  chart.value.on('mouseover', (p) => {
    if (p.dataType === 'node' && p.data?.raw) {
      hoverNode.value = p.data.raw
      tipPos.value = { x: p.event?.offsetX ?? 0, y: p.event?.offsetY ?? 0 }
    }
  })

  chart.value.on('mousemove', (p) => {
    if (hoverNode.value) {
      tipPos.value = { x: p.event?.offsetX ?? 0, y: p.event?.offsetY ?? 0 }
    }
  })

  chart.value.on('mouseout', () => {
    hoverNode.value = null
  })
}

onMounted(() => {
  // 被 v-if 切换进来时，挂载瞬间容器可能还没进入布局，
  // 直接 init 会得到一个 0x0 的画布（ECharts 不报错，但什么都画不出来）。
  // 用 nextTick + 尺寸校验兜住这个时序。
  nextTick(() => {
    const el = chartRef.value
    if (!el) {
      console.warn('[KnowledgeGraph] 容器未就绪，跳过初始化')
      return
    }
    chart.value = echarts.init(el)
    bindEvents()
    resizeObserver = new ResizeObserver(() => {
      if (resizeTimer) clearTimeout(resizeTimer)
      resizeTimer = setTimeout(() => {
        chart.value?.resize()
        render()
      }, 180)
    })
    resizeObserver.observe(el)
    if (el.clientWidth > 0 && el.clientHeight > 0) {
      reload()
    } else {
      // 尺寸还是 0，等下一帧再来（通常一两帧内就会有值）
      requestAnimationFrame(() => reload())
    }
  })
})

let resizeTimer = null

// 切换深浅主题时 CSS 变量变了，但 ECharts 画布不会自动跟随，重绘一次
const { theme: uiTheme } = useUiPreferences()
watch(uiTheme, () => {
  if (chart.value) render()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  // 必须 dispose，否则每次切视图都会泄漏一个 ECharts 实例
  chart.value?.dispose()
  chart.value = null
})

// 外部（如顶部的"图谱视图"切换）可以让它刷新
defineExpose({ reload })
</script>

<style scoped>
.kb-graph {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

/* 工具条：左侧搜索筛选，右侧视图控制 */
.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.tool-left,
.tool-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.graph-search {
  width: 200px;
}

.graph-cat {
  width: 140px;
}

/* 图例：小圆点 + 名称，点击聚焦 */
.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border-radius: 999px;
  border: 1px solid var(--v2-border-soft);
  background: var(--v2-surface);
  cursor: pointer;
  font-size: 12px;
  color: var(--v2-text-body);
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

.legend-item:hover {
  border-color: var(--v2-chip-border);
  transform: translateY(-1px);
}

.legend-item.dimmed {
  opacity: 0.4;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* 画布：宣纸质感底，用极淡的网格线暗示"谱系" */
.graph-canvas-wrap {
  position: relative;
  flex: 1;
  min-height: 560px;
  height: calc(100vh - 250px);
  border-radius: 16px;
  border: 1px solid var(--v2-border-soft);
  overflow: hidden;
  /* 画布底色跟随主题：深色是墨底细网格，浅色是宣纸 */
  background-color: var(--v2-surface);
  background-image:
    radial-gradient(circle at 18% 22%, color-mix(in srgb, var(--v2-gold) 8%, transparent), transparent 45%),
    radial-gradient(circle at 82% 78%, color-mix(in srgb, var(--v2-cyan) 8%, transparent), transparent 45%),
    repeating-linear-gradient(
      0deg,
      var(--v2-divider) 0px,
      var(--v2-divider) 1px,
      transparent 1px,
      transparent 28px
    ),
    repeating-linear-gradient(
      90deg,
      var(--v2-divider) 0px,
      var(--v2-divider) 1px,
      transparent 1px,
      transparent 28px
    );
}

.graph-canvas {
  width: 100%;
  height: 100%;
  min-height: 560px;
}

.graph-loading,
.graph-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--v2-text-weak);
  font-size: 13px;
  background: var(--v2-surface);
  backdrop-filter: blur(2px);
}

.graph-empty {
  pointer-events: none;
}

.graph-loading .el-icon {
  font-size: 22px;
  color: var(--v2-gold);
}

.graph-empty .el-icon {
  font-size: 30px;
  color: var(--v2-border-soft);
}

/* 自绘 tooltip */
.graph-tip {
  position: absolute;
  transform: translate(14px, -50%);
  pointer-events: none;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--v2-surface-strong);
  border: 1px solid var(--v2-chip-border);
  box-shadow: var(--v2-shadow-soft);
  max-width: 260px;
  z-index: 10;
}

.tip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--v2-text-strong);
  line-height: 1.4;
}

.tip-meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--v2-text-weak);
  font-variant-numeric: tabular-nums;
}

/* 底部统计条 */
.graph-footer {
  display: flex;
  gap: 18px;
  padding: 2px 4px;
  font-size: 12px;
  color: var(--v2-text-weak);
}

.graph-footer b {
  color: var(--v2-text-strong);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
