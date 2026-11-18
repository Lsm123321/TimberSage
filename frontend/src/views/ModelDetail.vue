<template>
  <div class="model-detail-container">
    <div class="detail-header">
      <el-button @click="$router.back()">
        <el-icon><Back /></el-icon>
        {{ t('goBack') }}
      </el-button>
    </div>

    <div class="detail-content" v-if="model">
      <div ref="viewerSectionRef" class="viewer-section">
        <div ref="viewerCanvasWrapRef" class="viewer-canvas-wrap">
          <div
            v-if="modelSceneError || viewerBlockedAlertVisible || modelParseHintVisible"
            class="viewer-scene-alerts"
          >
            <el-alert
              v-if="modelSceneError"
              type="error"
              :closable="false"
              show-icon
              class="viewer-scene-alerts__item"
              :title="modelSceneError"
            />
            <el-alert
              v-else-if="viewerBlockedAlertVisible"
              type="info"
              :closable="false"
              show-icon
              class="viewer-scene-alerts__item"
              :title="viewerBlockedReason"
            />
            <el-alert
              v-else-if="modelParseHintVisible"
              type="warning"
              :closable="false"
              show-icon
              class="viewer-scene-alerts__item"
              title="模型正在解析网格，若长时间无法点击构件，请检查控制台或确认模型是否合并为单网格。"
            />
          </div>
          <svg v-if="flyLineD" class="part-fly-line-svg" xmlns="http://www.w3.org/2000/svg">
            <path :d="flyLineD" fill="none" stroke="#ea580c" stroke-width="2" stroke-linecap="round" stroke-dasharray="7 5" opacity="0.88" />
          </svg>
          <ThreeScene
            ref="threeSceneRef"
            class="viewer-three"
            :model-url="modelFullUrl"
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

          <!-- AI 语音导览：播放条（章节卡片式视觉） -->
          <transition name="viewer-ai-dock-fade">
            <div v-if="aiTourActive" class="ai-tour-bar">
              <!-- 顶部：章节进度点 + 章节 x/y -->
              <div class="ai-tour-bar__head">
                <div class="ai-tour-bar__chapters">
                  <span
                    v-for="(ch, i) in aiTour.chapters.value"
                    :key="i"
                    class="ai-tour-bar__dot"
                    :class="{
                      'ai-tour-bar__dot--active': i === aiTour.currentIndex.value,
                      'ai-tour-bar__dot--done': i < aiTour.currentIndex.value
                    }"
                    :title="ch.chapter"
                  />
                </div>
                <span class="ai-tour-bar__count">
                  {{ aiTour.currentIndex.value + 1 }} / {{ aiTour.chapters.value.length }}
                </span>
              </div>

              <!-- 章节标题 + 动作徽标 -->
              <div class="ai-tour-bar__chapter-row">
                <span class="ai-tour-bar__chapter-name">
                  {{ aiTour.chapters.value[aiTour.currentIndex.value]?.chapter || '' }}
                </span>
                <span v-if="actionBadge" class="ai-tour-bar__action" :data-action="actionBadge.key">
                  {{ actionBadge.label }}
                </span>
                <span v-if="aiTour.usingFallback.value" class="ai-tour-bar__fallback-tag">离线脚本</span>
              </div>

              <!-- 旁白字幕：打字机逐字浮现 -->
              <p class="ai-tour-bar__caption">
                <span
                  v-for="(ch, i) in captionChars"
                  :key="i"
                  class="ai-tour-bar__char"
                  :style="{ animationDelay: `${i * 28}ms` }"
                >{{ ch }}</span>
              </p>

              <!-- 底部：控制按钮 + 音色选择 -->
              <div class="ai-tour-bar__controls">
                <button type="button" class="ai-tour-bar__btn" :title="aiTour.status.value === 'paused' ? '继续' : '暂停'" @click="toggleAiTourPause">
                  {{ aiTour.status.value === 'paused' ? '▶' : '⏸' }}
                </button>
                <button type="button" class="ai-tour-bar__btn" title="下一章" @click="aiTour.next()">⏭</button>
                <select v-model="aiTourVoiceId" class="ai-tour-bar__voice" title="切换音色">
                  <option v-for="v in voiceOptions" :key="v.id" :value="v.id">{{ v.name }}</option>
                </select>
                <button type="button" class="ai-tour-bar__btn ai-tour-bar__btn--stop" title="退出导览" @click="aiTour.stop()">⏹</button>
              </div>
            </div>
          </transition>
        </div>

        <div class="viewer-toolbar">
          <!-- 统一玻璃胶囊工具条：视图操作 | 季节场景 两区分隔 -->
          <div class="viewer-toolbar__group">
            <el-tooltip content="回到初始视角" placement="top">
              <button type="button" class="tool-btn" @click="resetCamera">
                <el-icon><Refresh /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="全屏查看" placement="top">
              <button type="button" class="tool-btn" @click="toggleFullscreen">
                <el-icon><FullScreen /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="沿径向分离各网格，便于观察相邻构件" placement="top">
              <button
                type="button"
                class="tool-btn"
                :class="{ 'tool-btn--active': exploded }"
                :disabled="!meshPartCount"
                @click="toggleExploded"
              >
                <el-icon><Expand /></el-icon>
              </button>
            </el-tooltip>
          </div>

          <span class="viewer-toolbar__divider" />

          <div class="viewer-toolbar__group">
            <button
              v-for="opt in sceneOptions"
              :key="opt.key"
              type="button"
              class="scene-chip"
              :class="{ 'scene-chip--active': currentScene === opt.key }"
              :title="`切换到${opt.label}场景`"
              @click="applyScene(opt.key)"
            >
              <span class="scene-chip__icon">{{ opt.icon }}</span>
            </button>
          </div>

          <span class="viewer-toolbar__divider" />

          <!-- AI 语音导览：融入工具栏的醒目按钮（琥珀渐变高亮，与玻璃胶囊一体） -->
          <button
            v-if="aiTour.status.value === 'idle' || aiTour.status.value === 'done'"
            type="button"
            class="tool-btn tool-btn--tour"
            :disabled="aiTourLoading"
            @click="startAiTour"
          >
            <el-icon><Microphone /></el-icon>
            <span class="tool-btn--tour__label">{{ aiTourLoading ? '生成中…' : 'AI 导览' }}</span>
          </button>
        </div>
      </div>

      <div class="info-section">
        <el-card class="model-info-card">
          <template #header>
            <div class="card-header">
              <h2>{{ model.modelName }}</h2>
              <div class="action-buttons">
                <el-tooltip :content="model.isFavorite ? t('cancelFavorite') : t('favorite')" placement="top">
                <el-button
                  :type="model.isFavorite ? 'warning' : 'default'"
                  @click="handleFavorite"
                  circle
                >
                  <el-icon><Star /></el-icon>
                </el-button>
                </el-tooltip>
                <el-tooltip :content="model.isLiked ? t('cancelLike') : t('like')" placement="top">
                <el-button
                  :type="model.isLiked ? 'danger' : 'default'"
                  @click="handleLike"
                  circle
                >
                  <el-icon><CaretTop /></el-icon>
                </el-button>
                </el-tooltip>
                <el-tooltip :content="t('modelCorrection')" placement="top">
                <el-button
                  :disabled="!token"
                  @click="openCorrectionDialog"
                  circle
                >
                  <el-icon><EditPen /></el-icon>
                </el-button>
                </el-tooltip>
                <el-button type="primary" @click="handleDownload">
                  <el-icon><Download /></el-icon>
                  {{ t('downloadModel') }}
                </el-button>
              </div>
            </div>
          </template>

          <div class="model-details">
            <div class="detail-item" v-if="model.dynasty">
              <span class="label">{{ t('dynasty') }}:</span>
              <span class="value">{{ model.dynasty }}</span>
            </div>
            <div class="detail-item" v-if="model.location">
              <span class="label">{{ t('location') }}:</span>
              <span class="value">{{ model.location }}</span>
            </div>
            <div class="detail-item" v-if="model.categoryName">
              <span class="label">{{ t('category') }}:</span>
              <el-tag size="small">{{ model.categoryName }}</el-tag>
            </div>
            <div class="detail-item" v-if="model.tags">
              <span class="label">{{ t('tags') }}:</span>
              <div class="tags">
                <el-tag
                  v-for="tag in parseTags(model.tags)"
                  :key="tag"
                  size="small"
                  type="info"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>
            <div class="detail-item" v-if="model.description">
              <span class="label">{{ t('description') }}:</span>
              <span class="value value--multiline">{{ model.description }}</span>
            </div>
          </div>

          <div class="stats-row">
            <div class="stat-item">
              <el-icon><View /></el-icon>
              <span>{{ model.viewCount || 0 }} {{ t('views') }}</span>
            </div>
            <div class="stat-item">
              <el-icon><Star /></el-icon>
              <span>{{ model.favoriteCount || 0 }} {{ t('favoritesCount') }}</span>
            </div>
            <div class="stat-item">
              <el-icon><CaretTop /></el-icon>
              <span>{{ model.likeCount || 0 }} {{ t('likes') }}</span>
            </div>
            <div class="stat-item">
              <el-icon><Download /></el-icon>
              <span>{{ model.downloadCount || 0 }} {{ t('downloads') }}</span>
            </div>
          </div>
        </el-card>

        <el-dialog
          v-model="correctionVisible"
          class="v2-unified-dialog"
          modal-class="v2-unified-overlay"
          :title="t('modelCorrection')"
          width="560px"
        >
          <el-form label-width="92px">
            <el-form-item :label="t('correctionErrorType')" required>
              <el-select v-model="correctionType" :placeholder="t('correctionErrorTypePlaceholder')" style="width: 100%">
                <el-option :label="t('correctionTypeText')" value="文字/介绍错误" />
                <el-option :label="t('correctionTypeInfo')" value="属性信息错误" />
                <el-option :label="t('correctionTypeModel')" value="模型内容问题" />
                <el-option :label="t('correctionTypeOther')" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('correctionDescription')" required>
              <el-input
                v-model="correctionDesc"
                type="textarea"
                :rows="4"
                :placeholder="t('correctionDescriptionPlaceholder')"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="correctionVisible = false">{{ t('cancel') }}</el-button>
            <el-button type="primary" :loading="correctionSubmitting" @click="submitCorrection">
              {{ t('correctionSubmit') }}
            </el-button>
          </template>
        </el-dialog>

        <!--
          纠错记录：只展示管理员已采纳的条目。
          待审核的提交不会出现在这里，只在自己的「我的纠错提交」里可见，
          这样未经核实的内容不会先被别人看到。
        -->
        <div class="correction-records" v-if="publishedFeedbacks.length > 0 || myFeedbacks.length > 0">
          <div class="records-head">
            <h3>纠错记录</h3>
            <span class="records-hint">仅在管理员审核通过后公开展示</span>
          </div>

          <div class="record-group" v-if="myFeedbacks.length > 0">
            <div class="group-title">我的提交</div>
            <div
              v-for="item in myFeedbacks"
              :key="`mine-${item.id}`"
              class="record-item is-mine"
            >
              <div class="record-top">
                <span class="record-type">{{ item.errorType }}</span>
                <span class="record-status" :class="`status-${item.status}`">{{ item.statusText }}</span>
              </div>
              <p class="record-desc">{{ item.description }}</p>
              <div class="record-remark" v-if="item.adminRemark">
                <strong>管理员备注：</strong>{{ item.adminRemark }}
              </div>
            </div>
          </div>

          <div class="record-group" v-if="publishedFeedbacks.length > 0">
            <div class="group-title">已采纳的纠错</div>
            <div
              v-for="item in publishedFeedbacks"
              :key="`pub-${item.id}`"
              class="record-item"
            >
              <div class="record-top">
                <span class="record-type">{{ item.errorType }}</span>
                <span class="record-author">{{ item.username }}</span>
              </div>
              <p class="record-desc">{{ item.description }}</p>
              <div class="record-remark" v-if="item.adminRemark">
                <strong>处理说明：</strong>{{ item.adminRemark }}
              </div>
            </div>
          </div>
        </div>

        <MaterialPanel
          ref="materialPanelRef"
          :model-id="modelId"
          :is-logged-in="!!token"
          @material-change="handleMaterialChange"
          @wireframe-change="handleWireframeChange"
          @save="handleSaveMaterial"
        />

        <CommentSection :model-id="modelId" />
      </div>
    </div>

    <ChatFloat v-if="model" :model-id="modelId" />

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="50"><Loading /></el-icon>
      <p>{{ t('loading') }}</p>
    </div>

    <div v-if="!loading && !model" class="error-state">
      <el-empty :description="t('modelLoadFailed')" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useModelStore } from '@/stores/model'
import ThreeScene from '@/components/ThreeScene.vue'
import MaterialPanel from '@/components/MaterialPanel.vue'
import CommentSection from '@/components/CommentSection.vue'
import ChatFloat from '@/components/ChatFloat.vue'
import { ElMessage } from 'element-plus'
import { Back, Refresh, FullScreen, Star, CaretTop, Download, View, Loading, EditPen, Expand, Microphone } from '@element-plus/icons-vue'
import { useUiPreferences } from '@/composables/useUiPreferences'
import { openLoginGuide } from '@/composables/useLoginGuide'
import request from '@/utils/request'
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
import { getSceneOptions } from '@/composables/useSceneEnvironment'
import { useAiTour } from '@/composables/useAiTour'
import { getVoiceOptions, onVoicesChanged } from '@/composables/useSpeech'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const modelStore = useModelStore()
const { t } = useUiPreferences()

const modelId = computed(() => parseInt(route.params.id))
const token = computed(() => authStore.token)
const model = ref(null)
const loading = ref(true)
const modelUrl = ref('')
const threeSceneRef = ref(null)
// 季节场景切换，和 ModelViewer 页共用同一套预设
const sceneOptions = getSceneOptions()
const currentScene = ref('default')
const applyScene = (key) => {
  currentScene.value = key
  threeSceneRef.value?.setEnvironment?.(key)
}

// ---------- AI 语音导览 ----------
const aiTour = useAiTour(threeSceneRef)
const aiTourVoiceId = ref('tongtong')
const voiceOptions = ref(getVoiceOptions())
// 把管理员克隆的音色也并进下拉
import { loadClonedVoices } from '@/composables/useSpeech'
loadClonedVoices().then(cloned => {
  if (cloned && cloned.length) {
    voiceOptions.value = getVoiceOptions()
  }
})
// 系统音色是异步加载的，有变化就刷一遍
onVoicesChanged(() => { voiceOptions.value = getVoiceOptions() })
const aiTourLoading = computed(() => aiTour.status.value === 'loading')
const aiTourActive = computed(() =>
  ['playing', 'paused'].includes(aiTour.status.value)
)
const startAiTour = () => {
  if (!authStore.token) {
    ElMessage.warning('请先登录后使用 AI 语音导览')
    return
  }
  aiTour.setModelName(model.value?.modelName)
  aiTour.setVoiceId(aiTourVoiceId.value)
  aiTour.start(modelId.value)
}
const toggleAiTourPause = () => {
  if (aiTour.status.value === 'paused') {
    aiTour.resume()
  } else {
    aiTour.pause()
  }
}
// 音色下拉切换后立即生效，下一章就用新音色读
watch(aiTourVoiceId, (id) => aiTour.setVoiceId(id))

// 当前章的动作徽标（explode/assemble/zoomIn/zoomOut/orbit）
const ACTION_BADGES = {
  explode: { key: 'explode', label: '分解演示' },
  assemble: { key: 'assemble', label: '恢复组装' },
  zoomIn: { key: 'zoomIn', label: '特写放大' },
  zoomOut: { key: 'zoomOut', label: '拉远全景' },
  orbit: { key: 'orbit', label: '环绕展示' }
}
const actionBadge = computed(() => ACTION_BADGES[aiTour.currentAction.value] || null)

// 字幕打字机：章节文案变化时逐字浮现（每字延迟 28ms 由 CSS animation 处理）
const captionChars = computed(() => (aiTour.currentNarration.value || '').split(''))
const materialPanelRef = ref(null)

const correctionVisible = ref(false)
const correctionType = ref('')
const correctionDesc = ref('')
// 纠错记录：已采纳的公开记录 + 本人的全部提交（含待审核）
const publishedFeedbacks = ref([])
const myFeedbacks = ref([])

/**
 * 纠错记录有两条来源，权限不同：
 * 1) 公开列表只拿 status=1（管理员已采纳），其他人才能看到；
 * 2) 本人的提交单独拉一次，待审核状态只有自己可见。
 */
const loadCorrectionRecords = async () => {
  try {
    const pub = await request.get(`/models/${modelId.value}/feedback`, { params: { page: 1, pageSize: 20 } })
    publishedFeedbacks.value = pub.data?.content || []
  } catch (error) {
    // 列表拉不到不影响页面主体，静默处理
    publishedFeedbacks.value = []
  }

  if (!token.value) {
    myFeedbacks.value = []
    return
  }
  try {
    const mine = await request.get(`/models/${modelId.value}/feedback/mine`, { params: { page: 1, pageSize: 20 } })
    myFeedbacks.value = mine.data?.content || []
  } catch (error) {
    myFeedbacks.value = []
  }
}
const correctionSubmitting = ref(false)

const exploded = ref(false)
// 模型里的网格数，只用来判断爆炸展开能不能用，不在侧边栏列部件
const meshPartCount = ref(0)
const partAiLabelCache = new Map()
// 拿不到模型文件地址时的提示（没登录或没权限）
const viewerBlockedReason = ref('')
const modelSceneError = ref('')
// 画布点选的构件，称谓等需要时再请求
const selectedPartTechnicalName = ref('')
const selectedPartAiLabel = ref('')
// 连点不同构件时的序号，只有最后一次选择的返回才被认账
let selectionSeq = 0

const viewerSectionRef = ref(null)
const viewerCanvasWrapRef = ref(null)
const partFloatCardRef = ref(null)

const {
  visible: aiRetrieveDockVisible,
  percentage: aiRetrieveProgress,
  runWithProgress: runAiRetrieveWithProgress
} = usePartAiRetrieveDock()

// 检索进度的冷色渐变，和登录页 HUD 保持一致
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
// 快速切换构件时先缓一缓再发请求
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
  return `「${label}」为当前模型中的一个独立网格。可旋转视角查看；使用画布底部「爆炸展开」便于观察相邻关系。登录后点击构件将按需自动检索称谓并生成简要说明（无需预先加载全部构件）。`
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
  // 没登录就别请求了，接口本身要登录
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

  // 只有爆炸展开了才去解析构件，平时点选没必要触发这一串请求
  if (!exploded.value) return

  if (!token.value) return

  // 连点不同构件时只认最后一次的返回，之前的直接作废
  const seq = ++selectionSeq
  const tech = selectedPartTechnicalName.value

  ;(async () => {
    try {
      await ensureSinglePartAiLabel(id, tech)
    } catch (e) {
      console.warn('[ModelDetail] 构件称谓生成失败', e)
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
        console.warn('[ModelDetail] 构件说明生成失败', e)
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

  // 爆炸展开刚开启时，若已有选中构件，则立刻触发一次解析
  const seq = ++selectionSeq
  const tech = selectedPartTechnicalName.value

  ;(async () => {
    try {
      await ensureSinglePartAiLabel(id, tech)
    } catch (e) {
      console.warn('[ModelDetail] 构件称谓生成失败', e)
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
        console.warn('[ModelDetail] 构件说明生成失败', e)
        partBlurbText.value = ''
      } finally {
        partBlurbLoading.value = false
        nextTick(() => updateFlyLine())
      }
    }, 180)
  })()
})

const scheduleThreeResize = () => {
  nextTick(() => {
    requestAnimationFrame(() => {
      threeSceneRef.value?.resize?.()
      requestAnimationFrame(() => {
        threeSceneRef.value?.resize?.()
      })
    })
  })
}

const onViewerFullscreenChange = () => {
  scheduleThreeResize()
  nextTick(() => {
    syncAiRetrieveDockLayout()
    updateFlyLine()
  })
}

onUnmounted(() => {
  stopPartFloatLoop()
  if (partBlurbDebounceTimer) clearTimeout(partBlurbDebounceTimer)
  document.removeEventListener('fullscreenchange', onViewerFullscreenChange)
})

const modelFullUrl = computed(() => {
  if (modelUrl.value) return modelUrl.value
  if (model.value?.modelFilePath?.startsWith?.('http')) return model.value.modelFilePath
  return ''
})

const viewerBlockedAlertVisible = computed(
  () => !!viewerBlockedReason.value && meshPartCount.value === 0 && !modelSceneError.value
)

const modelParseHintVisible = computed(
  () =>
    !!modelFullUrl.value &&
    meshPartCount.value === 0 &&
    !modelSceneError.value &&
    !viewerBlockedReason.value
)

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
    return fallback
  } catch {
    return fallback
  }
}

const defaultCameraPosition = computed(() => normalizeCameraVector(model.value?.defaultCameraPosition, { x: 0, y: 2, z: 5 }))

const defaultCameraTarget = computed(() => normalizeCameraVector(model.value?.defaultCameraTarget, { x: 0, y: 0, z: 0 }))

onMounted(async () => {
  document.addEventListener('fullscreenchange', onViewerFullscreenChange)
  await loadModelDetail()
  // 纠错记录独立加载，失败也不该拖住模型主体的渲染
  await loadCorrectionRecords()
})

const loadModelDetail = async () => {
  loading.value = true
  viewerBlockedReason.value = ''
  modelSceneError.value = ''
  try {
    const data = await modelStore.fetchModelById(modelId.value)
    model.value = data
    modelUrl.value = ''

    if (token.value) {
      try {
        const downloadInfo = await modelStore.getModelFileUrl(modelId.value)
        modelUrl.value = downloadInfo?.downloadUrl || ''
      } catch (e) {
        console.warn('[ModelDetail] 获取下载链接失败', e)
        modelUrl.value = ''
        viewerBlockedReason.value =
          e?.response?.data?.message || '无法获取模型文件地址（权限或未发布）'
      }
    } else {
      try {
        const res = await getPublicModelFileUrl(modelId.value)
        const wrap = res.data?.data ?? res.data
        modelUrl.value = wrap?.url || ''
        if (!modelUrl.value) {
          viewerBlockedReason.value = '当前模型需登录后加载，或仅对已开放下载的模型提供匿名预览'
        }
      } catch (e) {
        modelUrl.value = ''
        const msg = e?.response?.data?.message
        if (e?.response?.status === 403 && msg) {
          viewerBlockedReason.value = msg
        } else {
          viewerBlockedReason.value = '请登录后加载完整模型；匿名仅支持「允许所有人下载」的已发布模型在线预览'
        }
      }
    }
  } catch (error) {
    console.error('[ModelDetail] 加载模型详情失败', error)
    ElMessage.error(t('modelLoadFailed'))
  } finally {
    loading.value = false
  }
}

const parseTags = (tagsStr) => {
  if (!tagsStr) return []
  try {
    return typeof tagsStr === 'string' ? JSON.parse(tagsStr) : tagsStr
  } catch {
    return []
  }
}

const onPartsReady = (list) => {
  partBlurbCache.clear()
  partAiLabelCache.clear()
  meshPartCount.value = (list || []).length
  exploded.value = false
  clearPartSelection()
}

const toggleExploded = () => {
  if (!threeSceneRef.value?.setExploded) return
  exploded.value = !exploded.value
  threeSceneRef.value.setExploded(exploded.value)
}

const onPartCanvasClick = (payload) => {
  if (!payload || !payload.id) {
    clearPartSelection()
    return
  }
  // 只有爆炸展开时才允许点选构件，正常观赏模式误触了很烦
  if (!exploded.value) return
  selectedPartId.value = payload.id
  selectedPartTechnicalName.value = (payload.name && String(payload.name).trim()) || ''
  selectedPartAiLabel.value = partAiLabelCache.has(payload.id)
    ? partAiLabelCache.get(payload.id)
    : ''
  threeSceneRef.value?.highlightPart?.(payload.id)
}

const handleModelLoaded = () => {
  modelSceneError.value = ''
  const list = threeSceneRef.value?.getPartsList?.()
  if (list?.length) {
    onPartsReady(list)
  } else {
    meshPartCount.value = 0
  }
}

const handleModelError = (error) => {
  console.error('[ModelDetail] 模型加载失败', error)
  modelSceneError.value =
    '模型文件加载失败：可能是格式不支持、跨域(CORS)、或预签名地址过期。请刷新页面或联系管理员。'
  ElMessage.error(t('modelLoadFailed'))
}

const resetCamera = () => {
  if (threeSceneRef.value) {
    threeSceneRef.value.resetCamera?.()
  }
}

const toggleFullscreen = () => {
  const viewer = viewerSectionRef.value
  if (!viewer) return
  if (!document.fullscreenElement) {
    viewer.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

const handleFavorite = async () => {
  if (!token.value) {
    openLoginGuide('收藏')
    return
  }

  try {
    if (model.value.isFavorite) {
      await modelStore.removeFavorite(modelId.value)
      model.value.isFavorite = false
      model.value.favoriteCount = Math.max(0, Number(model.value.favoriteCount || 0) - 1)
      ElMessage.success(t('favoriteRemoveSuccess'))
    } else {
      await modelStore.addFavorite(modelId.value)
      model.value.isFavorite = true
      model.value.favoriteCount = Number(model.value.favoriteCount || 0) + 1
      ElMessage.success(t('favoriteAddSuccess'))
    }
  } catch (error) {
    ElMessage.error(t('actionFailed'))
  }
}

const handleLike = async () => {
  if (!token.value) {
    openLoginGuide('点赞')
    return
  }

  try {
    const result = await modelStore.toggleLike(modelId.value)
    if (!result?.success) {
      ElMessage.error(result?.message || t('actionFailed'))
      return
    }

    // 优先用 store 里的最新数据回填；store 没有时就本地翻转一下，保证 UI 一定有反馈
    const updated = modelStore.currentModel
    if (updated && updated.id === modelId.value) {
      model.value.isLiked = !!updated.isLiked
      const c = Number(updated.likeCount)
      model.value.likeCount = Number.isFinite(c) ? c : (model.value.likeCount || 0)
    } else {
      const prevLiked = !!model.value.isLiked
      model.value.isLiked = !prevLiked
      const prevCount = Number(model.value.likeCount)
      model.value.likeCount = (Number.isFinite(prevCount) ? prevCount : 0) + (model.value.isLiked ? 1 : -1)
    }

    ElMessage.success(model.value.isLiked ? t('likeSuccess') : t('likeRemoved'))
  } catch (error) {
    console.error('[ModelDetail] 点赞失败', error)
    ElMessage.error(t('actionFailed'))
  }
}

const handleDownload = async () => {
  try {
    const data = await modelStore.getDownloadUrl(modelId.value)
    const fileUrl = data.downloadUrl
    const fileName = data.fileName || `${model.value?.modelName || 'model'}.glb`
    // 走 blob 再触发下载，直接开 URL 会闪一个空白页
    const resp = await fetch(fileUrl)
    if (!resp.ok) {
      throw new Error('下载文件失败')
    }
    const blob = await resp.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(blobUrl)
    ElMessage.success(t('downloadStarted'))
  } catch (error) {
    if (error.message?.includes('登录')) {
      openLoginGuide('下载')
    } else {
      ElMessage.error(t('getDownloadFailed'))
    }
  }
}

const openCorrectionDialog = () => {
  if (!token.value) {
    openLoginGuide('纠错')
    return
  }
  correctionType.value = ''
  correctionDesc.value = ''
  correctionVisible.value = true
}

const submitCorrection = async () => {
  if (!token.value) {
    openLoginGuide('纠错')
    return
  }
  if (!correctionType.value) {
    ElMessage.warning(t('correctionErrorTypeRequired'))
    return
  }
  if (!correctionDesc.value.trim()) {
    ElMessage.warning(t('correctionDescriptionRequired'))
    return
  }

  correctionSubmitting.value = true
  try {
    await request.post('/model-feedback', {
      modelId: modelId.value,
      errorType: correctionType.value,
      description: correctionDesc.value.trim()
    })
    ElMessage.success(t('correctionSubmitSuccess'))
    correctionVisible.value = false
    // 提交后刷新自己的记录：此时是待审核，只有本人能看到
    await loadCorrectionRecords()
  } catch (error) {
    console.error('[ModelDetail] 提交纠错失败', error)
    ElMessage.error(t('submitFailed'))
  } finally {
    correctionSubmitting.value = false
  }
}

const handleMaterialChange = ({ property, value }) => {
  if (property === 'color') {
    threeSceneRef.value?.setMaterialColor(value)
  } else {
    threeSceneRef.value?.setMaterialProperty(property, value)
  }
}

const handleWireframeChange = (enabled) => {
  threeSceneRef.value?.setWireframe(enabled)
}

const handleSaveMaterial = async ({ presetName, getParams }) => {
  if (!token.value) {
    openLoginGuide('修改资料')
    return
  }

  const materialParams = getParams()

  try {
    await fetch('/api/material-presets', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token.value}`
      },
      body: JSON.stringify({
        modelId: modelId.value,
        presetName,
        materialParams: JSON.stringify(materialParams)
      })
    })
    ElMessage.success(t('saveSuccess'))
  } catch (error) {
    console.error('[ModelDetail] 保存材质方案失败', error)
    ElMessage.error(t('saveFailed'))
  }
}
</script>

<style lang="scss" scoped>
.model-detail-container {
  min-width: 0;

  .detail-header {
    margin-bottom: 20px;
  }

  .detail-content {
    display: flex;
    gap: 20px;
    min-width: 0;

    .viewer-section {
      flex: 1;
      min-width: 0;
      position: relative;
      background: #f5f5f5;
      border-radius: 10px;
      /* 允许构件浮窗超出画布区域绘制，避免顶部抓手被裁切无法拖动 */
      overflow: visible;
      min-height: 600px;
      display: flex;
      flex-direction: column;

      .viewer-scene-alerts {
        position: absolute;
        top: 10px;
        left: 12px;
        right: 12px;
        z-index: 1180;
        display: flex;
        flex-direction: column;
        gap: 8px;
        pointer-events: none;

        :deep(.viewer-scene-alerts__item) {
          pointer-events: auto;
        }
      }

      .viewer-canvas-wrap {
        position: relative;
        flex: 1;
        min-width: 0;
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

      :deep(.viewer-three) {
        flex: 1;
        min-height: 0;
        width: 100%;
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
        pointer-events: auto;
        text-align: left;

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
            color: #64748b;
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

      // 统一玻璃胶囊工具条：视图操作 + 季节场景 一条居中，竖线分隔
      .viewer-toolbar {
        position: absolute;
        bottom: 20px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1100;
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 5px 10px;
        background: rgba(255, 255, 255, 0.86);
        backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.5);
        border-radius: 24px;
        box-shadow: 0 6px 22px rgba(15, 23, 42, 0.16);
      }

      .viewer-toolbar__group {
        display: flex;
        align-items: center;
        gap: 2px;
      }

      .viewer-toolbar__divider {
        width: 1px;
        height: 22px;
        margin: 0 6px;
        background: linear-gradient(to bottom, transparent, rgba(15, 23, 42, 0.18), transparent);
      }

      .tool-btn {
        width: 36px;
        height: 36px;
        display: flex;
        align-items: center;
        justify-content: center;
        border: none;
        border-radius: 50%;
        background: transparent;
        color: #475569;
        font-size: 17px;
        cursor: pointer;
        transition: background 0.22s ease, color 0.22s ease, transform 0.22s ease;

        &:hover:not(:disabled) {
          background: rgba(15, 23, 42, 0.07);
          color: #0f172a;
          transform: translateY(-1px);
        }

        &:disabled {
          opacity: 0.35;
          cursor: not-allowed;
        }

        // 爆炸展开激活态
        &--active {
          background: rgba(245, 158, 11, 0.16);
          color: #b45309;
        }
      }

      .scene-chip {
        width: 34px;
        height: 34px;
        display: flex;
        align-items: center;
        justify-content: center;
        border: 1px solid transparent;
        border-radius: 50%;
        background: transparent;
        font-size: 17px;
        cursor: pointer;
        position: relative;
        transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.25s ease, box-shadow 0.25s ease;

        &:hover {
          background: rgba(245, 158, 11, 0.12);
          transform: translateY(-3px) scale(1.12);
        }

        // 选中：暖金光环 + 底部小圆点指示
        &--active {
          background: rgba(245, 158, 11, 0.18);
          box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.55), 0 0 14px rgba(245, 158, 11, 0.35);

          &::after {
            content: '';
            position: absolute;
            bottom: 1px;
            left: 50%;
            transform: translateX(-50%);
            width: 4px;
            height: 4px;
            border-radius: 50%;
            background: #f59e0b;
          }
        }
      }

      // ---------- AI 语音导览 ----------
      // Magnet 包裹层的绝对定位（vue-bits Magnet 默认 inline-block，包一层定位）
      // 工具栏内的导览按钮：琥珀渐变醒目态，与玻璃胶囊同高一体
      .tool-btn--tour {
        width: auto;
        padding: 0 14px;
        gap: 6px;
        border-radius: 18px;
        /*
          琥珀底 #f59e0b 配白字只有 2.2:1，按钮上的字会糊。
          改用深墨字后对比度到 6:1 以上，琥珀色依旧是视觉焦点。
        */
        background: linear-gradient(135deg, #f5b13c, #ea8a2c);
        color: #3a2408;
        font-size: 13px;
        font-weight: 700;
        box-shadow: 0 2px 10px rgba(234, 138, 44, 0.34);

        &:hover:not(:disabled) {
          background: linear-gradient(135deg, #ffc65c, #f59b3d);
          color: #3a2408;
          transform: translateY(-1px);
          box-shadow: 0 4px 14px rgba(234, 138, 44, 0.44);
        }

        &:disabled {
          opacity: 0.7;
          cursor: wait;
        }

        &__label {
          white-space: nowrap;
        }
      }

      // 播放条：底部悬浮（避开下方工具栏），含进度点/字幕/控制
      .ai-tour-bar {
        position: absolute;
        bottom: 96px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1160;
        width: min(600px, calc(100% - 40px));
        padding: 14px 18px 12px;
        // 顶部暖金微光 + 深色玻璃底，像影院字幕条
        background: linear-gradient(180deg, rgba(30, 41, 59, 0.94) 0%, rgba(15, 23, 42, 0.94) 100%);
        backdrop-filter: blur(12px);
        border: 1px solid rgba(245, 158, 11, 0.22);
        border-radius: 16px;
        color: #e2e8f0;
        box-shadow: 0 12px 36px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.06);

        &__head {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 8px;
        }

        &__chapters {
          display: flex;
          align-items: center;
          gap: 7px;
        }

        &__dot {
          width: 7px;
          height: 7px;
          border-radius: 50%;
          background: rgba(148, 163, 184, 0.35);
          transition: all 0.35s ease;

          // 已讲完：暗金实心
          &--done {
            background: rgba(245, 158, 11, 0.45);
          }

          // 当前：亮金 + 呼吸光晕
          &--active {
            background: #f59e0b;
            box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.22), 0 0 10px rgba(245, 158, 11, 0.5);
            animation: tour-dot-pulse 2s ease-in-out infinite;
          }
        }

        &__count {
          font-size: 11px;
          color: #94a3b8;
          font-variant-numeric: tabular-nums;
        }

        &__chapter-row {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 6px;
        }

        &__chapter-name {
          font-size: 15px;
          font-weight: 700;
          color: #fbbf24;
          letter-spacing: 0.5px;
        }

        // 动作徽标：分解/放大/环绕等当前镜头语言提示
        &__action {
          padding: 2px 9px;
          border-radius: 10px;
          font-size: 11px;
          font-weight: 600;
          letter-spacing: 1px;
          border: 1px solid rgba(245, 158, 11, 0.4);
          background: rgba(245, 158, 11, 0.14);
          color: #fbbf24;

          &[data-action='explode'],
          &[data-action='assemble'] {
            border-color: rgba(56, 189, 248, 0.4);
            background: rgba(56, 189, 248, 0.14);
            color: #7dd3fc;
          }
        }

        &__fallback-tag {
          padding: 1px 6px;
          border-radius: 8px;
          background: rgba(148, 163, 184, 0.2);
          font-size: 11px;
          font-weight: 400;
          color: #94a3b8;
        }

        &__caption {
          margin: 0 0 12px;
          font-size: 14px;
          line-height: 1.75;
          color: #f1f5f9;
          min-height: 25px;
          max-height: 75px;
          overflow-y: auto;
          text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
        }

        // 打字机逐字浮现
        &__char {
          display: inline-block;
          opacity: 0;
          animation: tour-char-in 0.28s ease-out forwards;
        }

        &__controls {
          display: flex;
          align-items: center;
          gap: 8px;
        }

        &__btn {
          width: 34px;
          height: 34px;
          display: flex;
          align-items: center;
          justify-content: center;
          border: 1px solid rgba(148, 163, 184, 0.3);
          border-radius: 50%;
          background: rgba(255, 255, 255, 0.08);
          color: #e2e8f0;
          font-size: 13px;
          cursor: pointer;
          transition: all 0.2s ease;

          &:hover {
            background: rgba(255, 255, 255, 0.18);
            transform: translateY(-1px);
          }

          &--stop:hover {
            background: rgba(239, 68, 68, 0.5);
            border-color: rgba(239, 68, 68, 0.6);
          }
        }

        &__voice {
          flex: 1;
          margin-left: auto;
          max-width: 190px;
          padding: 5px 8px;
          border: 1px solid rgba(148, 163, 184, 0.3);
          border-radius: 8px;
          background: rgba(15, 23, 42, 0.8);
          color: #e2e8f0;
          font-size: 12px;
          outline: none;
          cursor: pointer;

          option {
            background: #0f172a;
            color: #e2e8f0;
          }
        }
      }

      @keyframes tour-dot-pulse {
        0%, 100% { box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.22), 0 0 10px rgba(245, 158, 11, 0.5); }
        50% { box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.12), 0 0 16px rgba(245, 158, 11, 0.7); }
      }

      @keyframes tour-char-in {
        from { opacity: 0; transform: translateY(4px); filter: blur(2px); }
        to { opacity: 1; transform: translateY(0); filter: blur(0); }
      }
    }

    .info-section {
      width: 380px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      gap: 20px;

      /*
        纠错记录区。
        只放两类内容：本人提交（可能待审核）和管理员已采纳的公开条目，
        视觉上用左侧色条区分，避免用户把"还没审核"当成"官方结论"。
      */
      .correction-records {
        padding: 16px;
        border-radius: var(--v2-radius-lg);
        border: 1px solid var(--v2-border-soft);
        background: var(--v2-surface-strong);

        .records-head {
          display: flex;
          align-items: baseline;
          justify-content: space-between;
          gap: 10px;
          margin-bottom: 12px;

          h3 {
            margin: 0;
            font-size: 15px;
            font-weight: 700;
            color: var(--v2-text-strong);
          }

          .records-hint {
            font-size: 11px;
            color: var(--v2-text-weak);
          }
        }

        .record-group + .record-group {
          margin-top: 14px;
          padding-top: 12px;
          border-top: 1px dashed var(--v2-divider);
        }

        .group-title {
          font-size: 12px;
          font-weight: 700;
          color: var(--v2-text-weak);
          margin-bottom: 8px;
          letter-spacing: 0.3px;
        }

        .record-item {
          padding: 10px 12px;
          border-radius: 10px;
          background: var(--v2-surface-soft);
          border-left: 3px solid var(--v2-gold);

          & + .record-item {
            margin-top: 8px;
          }

          /* 自己的待审核提交用虚边提示"还没公开" */
          &.is-mine {
            border-left-color: var(--v2-warn-border);
            border-left-style: dashed;
          }

          .record-top {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            margin-bottom: 6px;
          }

          .record-type {
            font-size: 12px;
            font-weight: 600;
            color: var(--v2-chip-text);
          }

          .record-author {
            font-size: 11px;
            color: var(--v2-text-weak);
          }

          .record-status {
            font-size: 11px;
            padding: 1px 8px;
            border-radius: 999px;
            border: 1px solid var(--v2-info-border);
            background: var(--v2-info-bg);
            color: var(--v2-info-text);

            &.status-0 {
              border-color: var(--v2-warn-border);
              background: var(--v2-warn-bg);
              color: var(--v2-warn-text);
            }

            &.status-1 {
              border-color: var(--v2-ok-border);
              background: var(--v2-ok-bg);
              color: var(--v2-ok-text);
            }

            &.status-2 {
              border-color: var(--v2-danger-border);
              background: var(--v2-danger-bg);
              color: var(--v2-danger-text);
            }
          }

          .record-desc {
            margin: 0;
            font-size: 12.5px;
            line-height: 1.65;
            color: var(--v2-text-body);
            white-space: pre-wrap;
          }

          .record-remark {
            margin-top: 6px;
            padding-top: 6px;
            border-top: 1px dashed var(--v2-divider);
            font-size: 12px;
            line-height: 1.6;
            color: var(--v2-text-weak);

            strong {
              color: var(--v2-text-body);
            }
          }
        }
      }

      .model-info-card {
        :deep(.el-card__header) {
          background: var(--v2-chip-bg);
          border-bottom: 1px solid var(--v2-border-soft);
        }

        :deep(.el-card__body) {
          background: var(--v2-surface-strong);
        }

        .card-header {
          display: flex;
          justify-content: space-between;
          align-items: center;

          h2 {
            font-size: 20px;
            color: var(--v2-text-strong);
            margin: 0;
          }

          .action-buttons {
            display: flex;
            gap: 10px;
          }
        }

        .model-details {
          .detail-item {
            display: flex;
            align-items: flex-start;
            margin-bottom: 15px;

            .label {
              width: 70px;
              color: var(--v2-text-weak);
              font-size: 14px;
            }

            .value {
              color: var(--v2-text-body);
              font-size: 14px;
            }

            .value--multiline {
              white-space: pre-wrap;
              line-height: 1.55;
              opacity: 0.95;
              flex: 1;
              min-width: 0;
            }

            .tags {
              display: flex;
              flex-wrap: wrap;
              gap: 5px;
            }
          }
        }

        .stats-row {
          display: grid;
          grid-template-columns: repeat(4, minmax(0, 1fr));
          padding-top: 20px;
          border-top: 1px solid var(--v2-divider);
          margin-top: 20px;
          column-gap: 8px;

          .stat-item {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            color: var(--v2-text-weak);
            font-size: 14px;
            white-space: nowrap;

            .el-icon {
              font-size: 15px;
              line-height: 1;
            }

            span {
              line-height: 1;
            }
          }
        }
      }

    }
  }

  .loading-state,
  .error-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 100px 0;

    p {
      margin-top: 20px;
      color: var(--v2-text-weak);
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
