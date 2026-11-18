<template>
  <div class="viewer-container">
    <div class="viewer-header">
      <h2>{{ modelName }} - 3D模型展示</h2>
      <div class="viewer-actions">
        <el-button @click="resetCamera">重置视角</el-button>
        <el-button @click="toggleWireframe">{{ wireframe ? '实体模式' : '线框模式' }}</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </div>

    <div class="viewer-content" v-loading="loading">
      <div class="canvas-container">
        <div ref="viewerCanvasWrapRef" class="viewer-canvas-inner">
          <svg v-if="flyLineD" class="part-fly-line-svg" xmlns="http://www.w3.org/2000/svg">
            <path :d="flyLineD" fill="none" stroke="#ea580c" stroke-width="2" stroke-linecap="round" stroke-dasharray="7 5" opacity="0.88" />
          </svg>
          <ThreeScene
            ref="threeSceneRef"
            class="three-fill"
            :model-url="modelUrl"
            :default-camera-position="defaultCameraPosition"
            :default-camera-target="defaultCameraTarget"
            @loaded="handleModelLoaded"
            @parts="onPartsReady"
            @error="handleModelError"
            @part-click="onPartCanvasClick"
          />
          <transition name="el-fade-in-linear">
            <div
              v-if="selectedPartSummary"
              ref="partFloatCardRef"
              class="part-anchor-float"
              :class="{ 'part-anchor-float--dragging': partFloatDragging }"
              :style="partFloatStyle"
            >
              <button type="button" class="part-anchor-float__close" aria-label="关闭" @click.stop="clearPartSelection">×</button>
              <div
                class="part-anchor-float__drag-handle"
                title="拖动移动浮窗"
                @mousedown.prevent="onPartFloatDragMouseDown"
              >
                <span class="part-anchor-float__drag-grip" aria-hidden="true" />
                <span class="part-anchor-float__drag-label">拖动</span>
              </div>
              <div
                class="part-anchor-float__head part-anchor-float__drag-zone"
                title="拖动移动浮窗"
                @mousedown.prevent="onPartFloatDragMouseDown"
              >
                <div class="part-anchor-float__title">{{ partFloatDisplayTitle }}</div>
                <div class="part-anchor-float__tech">{{ selectedPartSummary.name }}</div>
              </div>
              <p v-if="partBlurbLoading" class="part-anchor-float__desc part-anchor-float__desc--muted">正在结合模型与构件信息生成介绍…</p>
              <template v-else>
                <div v-if="partBlurbMeta?.generatedAt && partBlurbText" class="part-anchor-float__meta-row">
                  <span class="part-anchor-float__meta-label">生成时间</span>
                  <time class="part-anchor-float__meta-value" :datetime="partBlurbMeta.generatedAt">{{ partBlurbFormattedTime }}</time>
                </div>
                <div v-if="partBlurbSources.length && partBlurbText" class="part-anchor-float__sources">
                  <div class="part-anchor-float__sources-head">
                    <span class="part-anchor-float__sources-title">依据与参考</span>
                    <span class="part-anchor-float__sources-note">可追溯字段（非外链文献）</span>
                  </div>
                  <ul class="part-anchor-float__sources-list">
                    <li v-for="(s, idx) in partBlurbSources" :key="idx">
                      <span class="part-anchor-float__src-label">{{ s.label }}</span>
                      <span class="part-anchor-float__src-detail">{{ s.detail }}</span>
                    </li>
                  </ul>
                </div>
                <p class="part-anchor-float__desc">{{ partBlurbText || partFallbackIntro }}</p>
                <p v-if="partBlurbDisclaimerLine" class="part-anchor-float__disclaimer">{{ partBlurbDisclaimerLine }}</p>
              </template>
              <div
                class="part-anchor-float__drag-handle part-anchor-float__drag-handle--footer"
                title="底部拖动（顶部被挡时可从此处移动）"
                @mousedown.prevent="onPartFloatDragMouseDown"
              >
                <span class="part-anchor-float__drag-grip" aria-hidden="true" />
                <span class="part-anchor-float__drag-label">拖动位置</span>
              </div>
            </div>
          </transition>

          <transition name="viewer-ai-dock-fade">
            <div
              v-if="aiRetrieveDockVisible"
              class="viewer-ai-retrieve-dock viewer-ai-retrieve-dock--hud"
              :style="aiRetrieveDockStyle"
              role="status"
              aria-live="polite"
            >
              <span class="viewer-ai-retrieve-dock__scan" aria-hidden="true" />
              <span class="viewer-ai-retrieve-dock__glow" aria-hidden="true" />
              <span class="viewer-ai-retrieve-dock__corner viewer-ai-retrieve-dock__corner--tl" aria-hidden="true" />
              <span class="viewer-ai-retrieve-dock__corner viewer-ai-retrieve-dock__corner--br" aria-hidden="true" />
              <p class="viewer-ai-retrieve-dock__title">{{ PART_AI_RETRIEVE_TITLE }}</p>
              <el-progress
                class="viewer-ai-retrieve-dock__progress"
                :percentage="Math.min(100, Math.round(aiRetrieveProgress))"
                :stroke-width="10"
                striped
                striped-flow
                :color="aiRetrieveProgressColors"
              />
            </div>
          </transition>

          <div class="viewer-model-toolbar">
            <el-tooltip content="沿径向分离各网格，便于观察相邻构件" placement="top">
              <el-button
                :type="exploded ? 'primary' : 'default'"
                :disabled="!meshPartCount"
                @click="toggleExploded"
              >
                <el-icon><Expand /></el-icon>
                {{ exploded ? '收回爆炸' : '爆炸展开' }}
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </div>

      <div class="control-panel">
        <el-card class="control-card">
          <template #header>
            <span>模型控制</span>
          </template>
          <div class="control-item">
            <el-button @click="toggleWireframe" :type="wireframe ? 'primary' : 'default'">
              {{ wireframe ? '切换实体模式' : '切换线框模式' }}
            </el-button>
          </div>

          <div class="control-item">
            <div class="scene-switch-label">季节场景</div>
            <div class="scene-switch-group">
              <button
                v-for="opt in sceneOptions"
                :key="opt.key"
                type="button"
                class="scene-switch-btn"
                :class="{ 'scene-switch-btn--active': currentScene === opt.key }"
                @click="applyScene(opt.key)"
              >
                <!-- 选中项用流光边框强调（vue-bits StarBorder） -->
                <StarBorder
                  v-if="currentScene === opt.key"
                  as="span"
                  color="#f59e0b"
                  speed="5s"
                  custom-class="scene-star"
                >
                  <span class="scene-switch-btn__inner">
                    <span class="scene-switch-btn__icon">{{ opt.icon }}</span>
                    <span>{{ opt.label }}</span>
                  </span>
                </StarBorder>
                <span v-else class="scene-switch-btn__inner">
                  <span class="scene-switch-btn__icon">{{ opt.icon }}</span>
                  <span>{{ opt.label }}</span>
                </span>
              </button>
            </div>
          </div>
        </el-card>

        <el-card class="info-card">
          <template #header>
            <span>建筑信息</span>
          </template>
          <SpotlightCard class="building-spotlight" spotlight-color="rgba(245, 158, 11, 0.18)">
            <p><strong>名称：</strong>{{ modelName }}</p>
            <p><strong>朝代：</strong>{{ dynasty }}</p>
            <p><strong>位置：</strong>{{ location }}</p>
          </SpotlightCard>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {
  PART_FLOAT_ANCHOR_GAP,
  PART_FLOAT_VIEW_PAD,
  choosePartFloatPlacement
} from '@/composables/usePartFloatPlacement'
import { usePartFloatDrag } from '@/composables/usePartFloatDrag'
import {
  PART_FLOAT_AI_DISCLAIMER_FALLBACK,
  PART_FLOAT_FALLBACK_HINT,
  formatPartBlurbGeneratedAt,
  normalizePartBlurbPayload
} from '@/composables/usePartBlurbFloat'
import { labelModelParts, fetchPartBlurb } from '@/api/modelParts'
import { useAiRetrieveDockLayout } from '@/composables/useAiRetrieveDockLayout'
import {
  PART_AI_RETRIEVE_TITLE,
  usePartAiRetrieveDock
} from '@/composables/usePartAiRetrieveDock'
import { getPublicModelFileUrl } from '@/api/publicModel'
import { useRoute } from 'vue-router'
import { useModelStore } from '@/stores/model'
import ThreeScene from '@/components/ThreeScene.vue'
import StarBorder from '@/components/StarBorder.vue'
import SpotlightCard from '@/components/SpotlightCard.vue'
import { ElMessage } from 'element-plus'
import { Expand } from '@element-plus/icons-vue'

const route = useRoute()
const modelStore = useModelStore()

const loading = ref(true)
const model = ref(null)
const modelUrl = ref('')
const threeSceneRef = ref(null)
const wireframe = ref(false)
// 季节场景切换，选项直接取预设
import { getSceneOptions } from '@/composables/useSceneEnvironment'
const sceneOptions = getSceneOptions()
const currentScene = ref('default')
const applyScene = (key) => {
  currentScene.value = key
  threeSceneRef.value?.setEnvironment?.(key)
}
const exploded = ref(false)
const meshPartCount = ref(0)
const partAiLabelCache = new Map()
const selectedPartTechnicalName = ref('')
const selectedPartAiLabel = ref('')
// 连点不同构件时的序号，只有最后一次选择的返回才被认账
let selectionSeq = 0
const token = computed(() => window.localStorage.getItem('token') || '')

const modelId = computed(() => Number(route.params.id))

const viewerCanvasWrapRef = ref(null)
const partFloatCardRef = ref(null)

const {
  visible: aiRetrieveDockVisible,
  percentage: aiRetrieveProgress,
  runWithProgress: runAiRetrieveWithProgress
} = usePartAiRetrieveDock()

const aiRetrieveProgressColors = [
  { color: '#38bdf8', percentage: 35 },
  { color: '#22d3ee', percentage: 72 },
  { color: '#818cf8', percentage: 100 }
]

const { aiRetrieveDockStyle, syncAiRetrieveDockLayout } = useAiRetrieveDockLayout({
  visible: aiRetrieveDockVisible,
  wrapRef: viewerCanvasWrapRef,
  floatCardRef: partFloatCardRef
})
const selectedPartId = ref(null)
const partFloatStyle = ref({})
const flyLineD = ref('')
const partBlurbText = ref('')
const partBlurbLoading = ref(false)
/** @type {import('vue').Ref<{ generatedAt: string, sources: { label: string, detail: string }[], disclaimer: string } | null>} */
const partBlurbMeta = ref(null)
// 构件说明按 id 缓存，同一个构件不重复请求
const partBlurbCache = new Map()
let partFloatRafId = 0
// 快速切换构件时先缓一缓再发请求，别点一下就打一次接口
let partBlurbDebounceTimer = null

const {
  dragOffset: partFloatDragOffset,
  dragging: partFloatDragging,
  resetDragOffset: resetPartFloatDragOffset,
  onDragMouseDown: onPartFloatDragMouseDown
} = usePartFloatDrag(() => syncPartFloatPosition())

const selectedPartSummary = computed(() => {
  if (!selectedPartId.value) return null
  return {
    id: selectedPartId.value,
    name: selectedPartTechnicalName.value,
    aiLabel: selectedPartAiLabel.value
  }
})

const partFloatDisplayTitle = computed(() => {
  const row = selectedPartSummary.value
  if (!row) return ''
  const cn = row.aiLabel?.trim()
  return cn || row.name || '构件'
})

const partFallbackIntro = computed(() => {
  const row = selectedPartSummary.value
  if (!row) return ''
  const cn = row.aiLabel?.trim()
  const label = cn || row.name || '该构件'
  return `「${label}」为当前模型中的一个独立网格。可旋转视角查看；使用画布底部「爆炸展开」便于观察相邻关系。登录后点击构件将按需自动检索称谓并生成简要说明。`
})

const partBlurbFormattedTime = computed(() =>
  formatPartBlurbGeneratedAt(partBlurbMeta.value?.generatedAt)
)

const partBlurbSources = computed(() => partBlurbMeta.value?.sources || [])

const partBlurbDisclaimerLine = computed(() => {
  if (partBlurbLoading.value) return ''
  if (partBlurbText.value) {
    return partBlurbMeta.value?.disclaimer?.trim() || PART_FLOAT_AI_DISCLAIMER_FALLBACK
  }
  return PART_FLOAT_FALLBACK_HINT
})

async function ensureSinglePartAiLabel(partId, technicalName) {
  // 没登录就别请求了，接口本身要登录还限流
  if (!token.value || !partId) return
  // 同一构件只取一次，后面直接读缓存
  if (partAiLabelCache.has(partId)) {
    selectedPartAiLabel.value = partAiLabelCache.get(partId)
    return
  }
  try {
    // 请求比较慢，包一层进度 HUD 免得用户以为卡死了
    await runAiRetrieveWithProgress(async () => {
      const res = await labelModelParts(modelId.value, [
        { id: partId, technicalName: technicalName || '未命名' }
      ])
      const map = res.data?.data || {}
      const label = map[partId] ?? ''
      partAiLabelCache.set(partId, label)
      selectedPartAiLabel.value = label
    })
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '自动检索失败，请稍后重试'
    ElMessage.error(msg)
    throw e
  }
}

// 画一条从构件锚点连到浮窗的曲线（SVG path），每次浮窗挪位置都要重算
const updateFlyLine = () => {
  if (!selectedPartId.value || !viewerCanvasWrapRef.value || !partFloatCardRef.value) {
    flyLineD.value = ''
    return
  }
  const p = threeSceneRef.value?.getPartScreenPosition?.(selectedPartId.value)
  const sceneEl = threeSceneRef.value?.getSceneContainerEl?.()
  if (!p || !sceneEl) {
    flyLineD.value = ''
    return
  }
  const wrap = viewerCanvasWrapRef.value.getBoundingClientRect()
  const sr = sceneEl.getBoundingClientRect()
  const ax = sr.left - wrap.left + p.x
  const ay = sr.top - wrap.top + p.y
  const cr = partFloatCardRef.value.getBoundingClientRect()
  const cx = cr.left + cr.width * 0.5 - wrap.left
  const cy = cr.top - wrap.top
  const mx = (ax + cx) / 2
  const my = Math.min(ay, cy) - 28
  flyLineD.value = `M ${ax.toFixed(1)} ${ay.toFixed(1)} Q ${mx.toFixed(1)} ${my.toFixed(1)} ${cx.toFixed(1)} ${cy.toFixed(1)}`
}

// 每帧把浮窗贴到构件的屏幕坐标上（模型转动时跟着走），找不到锚点就退回画布顶部居中
function syncPartFloatPosition() {
  if (!selectedPartId.value) {
    flyLineD.value = ''
    return
  }
  const ox = partFloatDragOffset.value.x
  const oy = partFloatDragOffset.value.y
  const p = threeSceneRef.value?.getPartScreenPosition?.(selectedPartId.value)
  if (!p) {
    partFloatStyle.value = {
      left: `calc(50% + ${ox}px)`,
      top: `${Math.max(PART_FLOAT_VIEW_PAD, 16 + oy)}px`,
      transform: 'translateX(-50%)',
      opacity: '0.92'
    }
    nextTick(() => {
      updateFlyLine()
      syncAiRetrieveDockLayout()
    })
    return
  }
  const wrap = viewerCanvasWrapRef.value?.getBoundingClientRect()
  const wrapH = wrap?.height ?? 0
  const rawCardH = partFloatCardRef.value?.offsetHeight ?? 0
  const cardH = Math.max(rawCardH > 40 ? rawCardH : 260, 180)
  const placement = choosePartFloatPlacement(p.y, wrapH, cardH)

  if (placement === 'below') {
    partFloatStyle.value = {
      left: `${Math.round(p.x + ox)}px`,
      top: `${Math.round(p.y + oy + PART_FLOAT_ANCHOR_GAP)}px`,
      transform: 'translateX(-50%)',
      opacity: p.visible ? '1' : '0.45'
    }
  } else {
    partFloatStyle.value = {
      left: `${Math.round(p.x + ox)}px`,
      top: `${Math.round(p.y + oy)}px`,
      transform: `translate(-50%, calc(-100% - ${PART_FLOAT_ANCHOR_GAP}px))`,
      opacity: p.visible ? '1' : '0.45'
    }
  }
  nextTick(() => {
    updateFlyLine()
    syncAiRetrieveDockLayout()
  })
}

const startPartFloatLoop = () => {
  cancelAnimationFrame(partFloatRafId)
  const loop = () => {
    syncPartFloatPosition()
    partFloatRafId = requestAnimationFrame(loop)
  }
  syncPartFloatPosition()
  partFloatRafId = requestAnimationFrame(loop)
}

const stopPartFloatLoop = () => {
  cancelAnimationFrame(partFloatRafId)
  partFloatRafId = 0
}

const clearPartSelection = () => {
  if (partBlurbDebounceTimer) {
    clearTimeout(partBlurbDebounceTimer)
    partBlurbDebounceTimer = null
  }
  stopPartFloatLoop()
  selectedPartId.value = null
  partFloatStyle.value = {}
  flyLineD.value = ''
  partBlurbText.value = ''
  partBlurbMeta.value = null
  partBlurbLoading.value = false
  resetPartFloatDragOffset()
  selectedPartTechnicalName.value = ''
  selectedPartAiLabel.value = ''
  threeSceneRef.value?.clearPartHighlight?.()
}

watch(selectedPartId, (id) => {
  stopPartFloatLoop()
  if (partBlurbDebounceTimer) {
    clearTimeout(partBlurbDebounceTimer)
    partBlurbDebounceTimer = null
  }
  resetPartFloatDragOffset()
  partBlurbText.value = ''
  partBlurbMeta.value = null
  partBlurbLoading.value = false
  if (!id) {
    partFloatStyle.value = {}
    flyLineD.value = ''
    selectedPartTechnicalName.value = ''
    selectedPartAiLabel.value = ''
    return
  }
  nextTick(() => {
    syncPartFloatPosition()
    startPartFloatLoop()
  })

  /*
    去掉原来「只有爆炸展开才解析构件」的门槛。
    实际使用里爆炸状态的判定会受动画、模型重载、组件重建影响，
    偶尔出现"点了没反应"，用户会以为功能坏了。
    现在只要点中构件就一律弹卡片，卡片本身就是常显的信息面板，不依赖任何展开/收起状态。
  */
  if (!token.value) return

  // 连点不同构件时只认最后一次的返回，之前的直接作废
  const seq = ++selectionSeq
  const tech = selectedPartTechnicalName.value

  ;(async () => {
    try {
      await ensureSinglePartAiLabel(id, tech)
    } catch (e) {
      console.warn('[ModelViewer] 构件称谓生成失败', e)
      return
    }
    if (seq !== selectionSeq || selectedPartId.value !== id) return

    if (partBlurbCache.has(id)) {
      const cached = partBlurbCache.get(id)
      if (typeof cached === 'string') {
        partBlurbText.value = cached
        partBlurbMeta.value = {
          generatedAt: '',
          sources: [],
          disclaimer: PART_FLOAT_AI_DISCLAIMER_FALLBACK
        }
      } else if (cached && typeof cached === 'object') {
        partBlurbText.value = cached.blurb || ''
        partBlurbMeta.value = {
          generatedAt: cached.generatedAt || '',
          sources: Array.isArray(cached.sources) ? cached.sources : [],
          disclaimer: cached.disclaimer || PART_FLOAT_AI_DISCLAIMER_FALLBACK
        }
      }
      nextTick(() => updateFlyLine())
      return
    }

    partBlurbLoading.value = true
    partBlurbDebounceTimer = setTimeout(async () => {
      partBlurbDebounceTimer = null
      if (seq !== selectionSeq || selectedPartId.value !== id) {
        partBlurbLoading.value = false
        return
      }
      try {
        const res = await fetchPartBlurb(modelId.value, {
          technicalName: tech || '未命名',
          aiLabel: selectedPartAiLabel.value || ''
        })
        const norm = normalizePartBlurbPayload(res.data?.data)
        if (norm) {
          partBlurbCache.set(id, norm)
          partBlurbText.value = norm.blurb
          partBlurbMeta.value = {
            generatedAt: norm.generatedAt,
            sources: norm.sources,
            disclaimer: norm.disclaimer || PART_FLOAT_AI_DISCLAIMER_FALLBACK
          }
        }
      } catch (e) {
        console.warn('[ModelViewer] 构件说明生成失败', e)
        partBlurbText.value = ''
      } finally {
        partBlurbLoading.value = false
        nextTick(() => updateFlyLine())
      }
    }, 320)
  })()
})

watch(exploded, (v) => {
  if (!v) return
  const id = selectedPartId.value
  if (!id) return
  if (!token.value) return

  const seq = ++selectionSeq
  const tech = selectedPartTechnicalName.value

  ;(async () => {
    try {
      await ensureSinglePartAiLabel(id, tech)
    } catch (e) {
      console.warn('[ModelViewer] 构件称谓生成失败', e)
      return
    }
    if (seq !== selectionSeq || selectedPartId.value !== id) return

    if (partBlurbCache.has(id)) {
      const cached = partBlurbCache.get(id)
      if (typeof cached === 'string') {
        partBlurbText.value = cached
        partBlurbMeta.value = {
          generatedAt: '',
          sources: [],
          disclaimer: PART_FLOAT_AI_DISCLAIMER_FALLBACK
        }
      } else if (cached && typeof cached === 'object') {
        partBlurbText.value = cached.blurb || ''
        partBlurbMeta.value = {
          generatedAt: cached.generatedAt || '',
          sources: Array.isArray(cached.sources) ? cached.sources : [],
          disclaimer: cached.disclaimer || PART_FLOAT_AI_DISCLAIMER_FALLBACK
        }
      }
      nextTick(() => updateFlyLine())
      return
    }

    partBlurbLoading.value = true
    if (partBlurbDebounceTimer) {
      clearTimeout(partBlurbDebounceTimer)
      partBlurbDebounceTimer = null
    }
    partBlurbDebounceTimer = setTimeout(async () => {
      partBlurbDebounceTimer = null
      if (seq !== selectionSeq || selectedPartId.value !== id) {
        partBlurbLoading.value = false
        return
      }
      try {
        const res = await fetchPartBlurb(modelId.value, {
          technicalName: tech || '未命名',
          aiLabel: selectedPartAiLabel.value || ''
        })
        const norm = normalizePartBlurbPayload(res.data?.data)
        if (norm) {
          partBlurbCache.set(id, norm)
          partBlurbText.value = norm.blurb
          partBlurbMeta.value = {
            generatedAt: norm.generatedAt,
            sources: norm.sources,
            disclaimer: norm.disclaimer || PART_FLOAT_AI_DISCLAIMER_FALLBACK
          }
        }
      } catch (e) {
        console.warn('[ModelViewer] 构件说明生成失败', e)
        partBlurbText.value = ''
      } finally {
        partBlurbLoading.value = false
        nextTick(() => updateFlyLine())
      }
    }, 180)
  })()
})

onUnmounted(() => {
  stopPartFloatLoop()
  if (partBlurbDebounceTimer) clearTimeout(partBlurbDebounceTimer)
})
const modelName = computed(() => model.value?.modelName || '模型')
const dynasty = computed(() => model.value?.dynasty || '暂无')
const location = computed(() => model.value?.location || '暂无')

onMounted(() => {
  loadViewerData()
})

const normalizeCameraVector = (input, fallback) => {
  if (!input) return fallback
  try {
    const parsed = typeof input === 'string' ? JSON.parse(input) : input
    if (Array.isArray(parsed) && parsed.length >= 3) {
      const [x, y, z] = parsed
      if ([x, y, z].every(v => typeof v === 'number' && Number.isFinite(v))) {
        return { x, y, z }
      }
    }
    if (parsed && typeof parsed === 'object') {
      const x = Number(parsed.x)
      const y = Number(parsed.y)
      const z = Number(parsed.z)
      if ([x, y, z].every(v => Number.isFinite(v))) {
        return { x, y, z }
      }
    }
  } catch {
    // fallback below
  }
  return fallback
}

const defaultCameraPosition = computed(() =>
  normalizeCameraVector(model.value?.defaultCameraPosition, { x: 0, y: 2, z: 5 })
)

const defaultCameraTarget = computed(() =>
  normalizeCameraVector(model.value?.defaultCameraTarget, { x: 0, y: 0, z: 0 })
)

const loadViewerData = async () => {
  loading.value = true
  modelUrl.value = ''
  try {
    model.value = await modelStore.fetchModelById(modelId.value)
    const tk = window.localStorage.getItem('token')
    if (tk) {
      try {
        const downloadInfo = await modelStore.getModelFileUrl(modelId.value)
        modelUrl.value = downloadInfo?.downloadUrl || ''
      } catch (e) {
        console.warn('[ModelViewer] 获取下载链接失败', e)
      }
    }
    if (!modelUrl.value) {
      try {
        const res = await getPublicModelFileUrl(modelId.value)
        const wrap = res.data?.data ?? res.data
        modelUrl.value = wrap?.url || ''
      } catch (e) {
        console.warn('[ModelViewer] 获取公开文件地址失败', e)
      }
    }
    if (!modelUrl.value) {
      ElMessage.warning('未获取到模型文件地址（请登录并确认下载权限，或为「允许所有人下载」的模型）')
    }
  } catch (error) {
    console.error('[ModelViewer] 加载模型失败', error)
    ElMessage.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

const resetCamera = () => {
  if (threeSceneRef.value?.resetCamera) {
    threeSceneRef.value.resetCamera()
    ElMessage.success('视角已重置')
    return
  }
  // 兜底：极端情况下仍可通过重载恢复
  loadViewerData()
  ElMessage.success('视角已重置')
}

const toggleWireframe = () => {
  wireframe.value = !wireframe.value
  if (threeSceneRef.value?.setWireframe) {
    threeSceneRef.value.setWireframe(wireframe.value)
  }
}

const toggleExploded = () => {
  if (!threeSceneRef.value?.setExploded) return
  exploded.value = !exploded.value
  threeSceneRef.value.setExploded(exploded.value)
}

const onPartsReady = (list) => {
  partBlurbCache.clear()
  partAiLabelCache.clear()
  meshPartCount.value = (list || []).length
  exploded.value = false
  clearPartSelection()
}

const onPartCanvasClick = (payload) => {
  if (!payload || !payload.id) {
    clearPartSelection()
    return
  }
  /*
    不再要求"必须先爆炸展开"才响应点击。
    之前的门槛是为了防误触，但实战里经常出现点了没反应（爆炸状态被判丢），
    现在点中构件就选中并弹出常显卡片，误触可以直接点 × 关掉。
  */
  selectedPartId.value = payload.id
  selectedPartTechnicalName.value = (payload.name && String(payload.name).trim()) || ''
  selectedPartAiLabel.value = partAiLabelCache.has(payload.id)
    ? partAiLabelCache.get(payload.id)
    : ''
  threeSceneRef.value?.highlightPart?.(payload.id)
}

const handleModelLoaded = () => {
  if (wireframe.value && threeSceneRef.value?.setWireframe) {
    threeSceneRef.value.setWireframe(true)
  }
  const list = threeSceneRef.value?.getPartsList?.()
  if (list?.length) {
    onPartsReady(list)
  } else {
    meshPartCount.value = 0
  }
}

const handleModelError = (error) => {
  console.error('[ModelViewer] 模型渲染失败', error)
  ElMessage.error('模型渲染失败，请检查模型文件格式')
}
</script>

<style lang="scss" scoped>
.viewer-container {
  height: calc(100vh - 100px);

  .viewer-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: white;
    padding: 15px 20px;
    border-radius: 10px;
    margin-bottom: 20px;

    h2 {
      font-size: 18px;
      color: #333;
    }

    .viewer-actions {
      display: flex;
      gap: 10px;
    }
  }

  .viewer-content {
    display: flex;
    gap: 20px;
    height: calc(100% - 80px);

    .canvas-container {
      flex: 1;
      min-height: 0;
      background: white;
      border-radius: 10px;
      /* 与模型详情页一致，避免浮窗顶部被裁切 */
      overflow: visible;
      display: flex;
      flex-direction: column;
      position: relative;

      .viewer-canvas-inner {
        position: relative;
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;

        .viewer-ai-retrieve-dock--hud {
          position: absolute;
          width: min(300px, calc(100% - 24px));
          min-width: 256px;
          padding: 14px 16px 16px;
          border-radius: 14px;
          pointer-events: none;
          overflow: hidden;
          border: 1px solid rgba(124, 211, 255, 0.42);
          background:
            linear-gradient(145deg, rgba(12, 28, 52, 0.94) 0%, rgba(8, 18, 36, 0.9) 100%),
            radial-gradient(circle at 18% 22%, rgba(96, 200, 255, 0.16), transparent 45%);
          box-shadow:
            0 20px 50px rgba(4, 10, 24, 0.55),
            0 0 40px rgba(56, 189, 248, 0.12),
            inset 0 0 0 1px rgba(186, 240, 255, 0.1);
          backdrop-filter: blur(12px) saturate(1.2);
          -webkit-backdrop-filter: blur(12px) saturate(1.2);
          animation: ai-dock-hud-in 0.4s cubic-bezier(0.2, 0.9, 0.2, 1) both;
        }

        .viewer-ai-retrieve-dock__glow {
          position: absolute;
          inset: -40%;
          background: radial-gradient(
            circle at 50% 50%,
            rgba(56, 189, 248, 0.12),
            transparent 55%
          );
          pointer-events: none;
          animation: ai-dock-aurora 5s ease-in-out infinite alternate;
        }

        .viewer-ai-retrieve-dock__scan {
          position: absolute;
          inset: 0;
          background: linear-gradient(
            180deg,
            rgba(120, 220, 255, 0) 0%,
            rgba(120, 220, 255, 0.12) 50%,
            rgba(120, 220, 255, 0) 100%
          );
          transform: translateY(-100%);
          animation: ai-dock-scan 1.8s ease-in-out infinite;
          pointer-events: none;
        }

        .viewer-ai-retrieve-dock__corner {
          position: absolute;
          width: 18px;
          height: 18px;
          border: 2px solid rgba(165, 230, 255, 0.55);
          pointer-events: none;
          box-shadow: 0 0 12px rgba(56, 189, 248, 0.35);

          &--tl {
            top: 6px;
            left: 6px;
            border-right: none;
            border-bottom: none;
            border-radius: 3px 0 0 0;
          }

          &--br {
            bottom: 6px;
            right: 6px;
            border-left: none;
            border-top: none;
            border-radius: 0 0 3px 0;
          }
        }

        .viewer-ai-retrieve-dock__title {
          position: relative;
          margin: 0 0 12px;
          font-size: 12px;
          line-height: 1.5;
          letter-spacing: 0.04em;
          color: #d4f1ff;
          font-weight: 600;
          text-shadow:
            0 0 20px rgba(110, 210, 255, 0.45),
            0 0 1px rgba(0, 0, 0, 0.8);
        }

        :deep(.viewer-ai-retrieve-dock__progress) {
          position: relative;

          .el-progress-bar__outer {
            background: rgba(4, 12, 28, 0.55) !important;
            border-radius: 999px;
            box-shadow: inset 0 0 0 1px rgba(120, 200, 255, 0.2);
          }

          .el-progress__text {
            color: #a5e8ff !important;
            font-size: 12px !important;
            font-weight: 700;
            text-shadow: 0 0 12px rgba(56, 189, 248, 0.5);
            min-width: 2.2em;
          }
        }
      }

      .part-fly-line-svg {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        z-index: 1150;
        pointer-events: none;
        overflow: visible;
      }

      .three-fill {
        flex: 1;
        min-height: 500px;
      }

      .viewer-model-toolbar {
        position: absolute;
        bottom: 18px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1100;
        pointer-events: none;

        :deep(.el-button) {
          pointer-events: auto;
        }
      }

      .part-anchor-float {
        position: absolute;
        z-index: 1200;
        min-width: 220px;
        max-width: 340px;
        padding: 12px 32px 12px 12px;
        border-radius: 10px;
        background: rgba(255, 255, 255, 0.97);
        box-shadow: 0 8px 28px rgba(15, 23, 42, 0.2), 0 0 0 1px rgba(148, 163, 184, 0.4);

        .part-anchor-float__close {
          position: absolute;
          top: 6px;
          right: 8px;
          border: none;
          background: transparent;
          font-size: 20px;
          line-height: 1;
          cursor: pointer;
          color: #64748b;
          padding: 0 4px;
          z-index: 2;

          &:hover {
            color: #0f172a;
          }
        }

        &.part-anchor-float--dragging {
          cursor: grabbing;
          user-select: none;

          .part-anchor-float__drag-handle,
          .part-anchor-float__drag-zone {
            cursor: grabbing;
          }
        }

        .part-anchor-float__drag-zone {
          cursor: grab;
          user-select: none;
          touch-action: none;
          border-radius: 8px;

          &:hover {
            background: rgba(241, 245, 249, 0.85);
          }

          &:active {
            cursor: grabbing;
          }
        }

        .part-anchor-float__head {
          margin: 0 20px 8px 0;
          padding: 4px 8px 6px;
          margin-left: -2px;

          .part-anchor-float__title {
            margin-bottom: 4px;
          }

          .part-anchor-float__tech {
            margin-bottom: 0;
          }
        }

        .part-anchor-float__drag-handle {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          margin: 0 20px 8px 0;
          padding: 6px 8px;
          border-radius: 8px;
          cursor: grab;
          user-select: none;
          touch-action: none;
          color: #94a3b8;

          &:hover {
            background: rgba(241, 245, 249, 0.95);
            color: #64748b;
          }

          &:active {
            cursor: grabbing;
          }
        }

        .part-anchor-float__drag-grip {
          flex-shrink: 0;
          width: 18px;
          height: 11px;
          background: repeating-linear-gradient(
            to bottom,
            currentColor 0,
            currentColor 2px,
            transparent 2px,
            transparent 5px
          );
          border-radius: 1px;
          opacity: 0.85;
        }

        .part-anchor-float__drag-label {
          font-size: 11px;
          font-weight: 600;
          letter-spacing: 0.06em;
        }

        .part-anchor-float__drag-handle--footer {
          margin-top: 6px;
          margin-bottom: -4px;
          padding-top: 10px;
          border-top: 1px solid rgba(148, 163, 184, 0.3);
        }

        .part-anchor-float__title {
          font-size: 15px;
          font-weight: 600;
          color: #0f172a;
        }

        .part-anchor-float__tech {
          font-size: 12px;
          color: #64748b;
          word-break: break-all;
        }

        .part-anchor-float__meta-row {
          display: flex;
          align-items: baseline;
          flex-wrap: wrap;
          gap: 6px 10px;
          margin-bottom: 8px;
          padding-bottom: 8px;
          border-bottom: 1px solid rgba(148, 163, 184, 0.35);
          font-size: 11px;
          color: #475569;
        }

        .part-anchor-float__meta-label {
          font-weight: 600;
          color: #64748b;
          letter-spacing: 0.02em;
        }

        .part-anchor-float__meta-value {
          font-variant-numeric: tabular-nums;
          color: #0f172a;
        }

        .part-anchor-float__sources {
          margin-bottom: 10px;
          padding: 8px 10px;
          border-radius: 8px;
          background: rgba(241, 245, 249, 0.95);
          border: 1px solid rgba(148, 163, 184, 0.35);
        }

        .part-anchor-float__sources-head {
          display: flex;
          flex-direction: column;
          gap: 2px;
          margin-bottom: 6px;
        }

        .part-anchor-float__sources-title {
          font-size: 11px;
          font-weight: 600;
          color: #334155;
          letter-spacing: 0.04em;
          text-transform: uppercase;
        }

        .part-anchor-float__sources-note {
          font-size: 10px;
          color: #94a3b8;
          line-height: 1.35;
        }

        .part-anchor-float__sources-list {
          margin: 0;
          padding-left: 1rem;
          font-size: 11px;
          line-height: 1.45;
          color: #475569;

          li {
            margin-bottom: 4px;

            &:last-child {
              margin-bottom: 0;
            }
          }
        }

        .part-anchor-float__src-label {
          font-weight: 600;
          color: #334155;
          margin-right: 6px;
        }

        .part-anchor-float__src-detail {
          word-break: break-word;
        }

        .part-anchor-float__desc {
          margin: 0;
          font-size: 13px;
          line-height: 1.55;
          color: #334155;

          &--muted {
            color: #909399;
            font-style: italic;
          }
        }

        .part-anchor-float__disclaimer {
          margin: 10px 0 0;
          padding-top: 8px;
          border-top: 1px dashed rgba(148, 163, 184, 0.45);
          font-size: 10px;
          line-height: 1.5;
          color: #64748b;
        }
      }
    }

    .control-panel {
      width: 320px;
      display: flex;
      flex-direction: column;
      gap: 20px;

      .control-item {
        margin-bottom: 15px;

        span {
          display: block;
          margin-bottom: 10px;
          color: #666;
          font-size: 14px;
        }
      }

      // 季节场景切换按钮组：2x2 网格，选中项流光边框高亮（vue-bits StarBorder）
      .scene-switch-label {
        margin-bottom: 8px;
        color: #666;
        font-size: 14px;
      }

      .scene-switch-group {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px;
      }

      .scene-switch-btn {
        padding: 0;
        border: 1px solid #dcdfe6;
        border-radius: 12px;
        background: #fff;
        color: #606266;
        font-size: 13px;
        cursor: pointer;
        overflow: hidden;
        transition: border-color 0.25s ease, box-shadow 0.25s ease, transform 0.25s ease;

        &:hover {
          border-color: #c0c4cc;
          color: #303133;
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
        }

        &--active {
          border-color: transparent;

          &:hover {
            color: #b45309;
          }
        }

        &__inner {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          width: 100%;
          padding: 9px 4px;
        }

        &--active &__inner {
          color: #b45309;
          font-weight: 600;
        }

        &__icon {
          font-size: 15px;
        }
      }

      // StarBorder 组件内边距覆写（默认 64x24 太大）
      .scene-star :deep(> div:last-child),
      .scene-star > div:last-child {
        padding: 9px 8px !important;
        background: #fffbeb !important;
        border-color: #fcd34d !important;
        border-radius: 12px !important;
        font-size: 13px !important;
        width: 100%;
      }
      .scene-star {
        width: 100%;
        border-radius: 12px !important;
      }

      // 建筑信息卡的聚光效果容器（vue-bits SpotlightCard）
      .building-spotlight {
        padding: 12px 16px !important;
        border-radius: 10px !important;
        border: 1px solid #ebeef5 !important;

        p {
          margin-bottom: 10px;
          font-size: 14px;
          color: #666;

          strong {
            color: #333;
          }
        }
      }

      .info-card {
        p {
          margin-bottom: 10px;
          font-size: 14px;
          color: #666;

          strong {
            color: #333;
          }
        }
      }
    }
  }
}

.viewer-ai-dock-fade-enter-active,
.viewer-ai-dock-fade-leave-active {
  transition: opacity 0.32s ease;
}

.viewer-ai-dock-fade-enter-from,
.viewer-ai-dock-fade-leave-to {
  opacity: 0;
}

@keyframes ai-dock-hud-in {
  from {
    opacity: 0;
    filter: blur(5px);
  }

  to {
    opacity: 1;
    filter: blur(0);
  }
}

@keyframes ai-dock-aurora {
  0% {
    transform: translate3d(-3%, -2%, 0) scale(1);
    opacity: 0.75;
  }

  100% {
    transform: translate3d(2%, 3%, 0) scale(1.08);
    opacity: 0.55;
  }
}

@keyframes ai-dock-scan {
  0% {
    transform: translateY(-100%);
  }

  100% {
    transform: translateY(100%);
  }
}
</style>
