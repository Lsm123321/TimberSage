<template>
  <div class="buildings-container">
    <div class="page-header">
      <h1>{{ t('ancientBuildingList') }}</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleCreate">{{ t('addBuilding') }}</el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        :placeholder="t('searchBuildingName')"
        style="width: 300px"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="selectedDynasty" :placeholder="t('selectDynasty')" clearable style="width: 150px" @change="handleSearch">
        <el-option :label="t('allDynasty')" value="" />
        <el-option :label="t('dynastyTang')" value="唐" />
        <el-option :label="t('dynastySong')" value="宋" />
        <el-option :label="t('dynastyYuan')" value="元" />
        <el-option :label="t('dynastyMing')" value="明" />
        <el-option :label="t('dynastyQing')" value="清" />
      </el-select>
    </div>

    <el-card class="ai-guide-card" shadow="hover">
      <template #header>
        <div class="ai-guide-header">
          <div>
            <h3>{{ t('aiGuideAssistant') }}</h3>
            <p>{{ t('aiGuideAssistantDesc') }}</p>
          </div>
        </div>
      </template>

      <div class="ai-guide-body">
        <div class="ai-guide-config">
          <el-select v-model="aiGuideMode" size="small" style="width: 180px">
            <el-option :label="t('modeGuide')" value="guide" />
            <el-option :label="t('modeAcademic')" value="academic" />
            <el-option :label="t('modeDefense')" value="defense" />
          </el-select>
          <el-select v-model="aiAudience" size="small" style="width: 180px">
            <el-option :label="t('audienceVisitor')" value="visitor" />
            <el-option :label="t('audienceStudent')" value="student" />
            <el-option :label="t('audienceJudge')" value="judge" />
          </el-select>
        </div>
        <el-input
          v-model="aiQuestion"
          type="textarea"
          :rows="2"
          resize="none"
          :placeholder="t('aiQuestionPlaceholder')"
          @keyup.enter.ctrl="askAiGuide()"
        />

        <div class="ai-guide-actions">
          <div class="quick-prompts">
            <el-tag
              v-for="prompt in aiQuickPrompts"
              :key="prompt"
              class="quick-prompt-tag"
              @click="fillAiQuestion(prompt)"
            >
              {{ prompt }}
            </el-tag>
          </div>
          <div class="action-buttons">
            <el-button
              :loading="aiLoading"
              @click="generateThreePartScript"
            >
              {{ t('oneClickThreePartScript') }}
            </el-button>
            <el-button
              type="primary"
              :loading="aiLoading"
              :disabled="!aiQuestion.trim()"
              @click="askAiGuide()"
            >
              {{ t('generateGuideSuggestion') }}
            </el-button>
          </div>
        </div>

        <div class="ai-guide-answer" v-if="aiAnswer">
          <div class="answer-header" @click="toggleAnswerCollapsed">
            <div class="answer-title">
              {{ t('aiGuideSuggestion') }}
              <span class="answer-scope">{{ answerScopeLabel }}</span>
            </div>
            <el-button size="small" text class="collapse-toggle">
              {{ answerCollapsed ? t('expandSuggestion') : t('collapseSuggestion') }}
              <el-icon class="collapse-icon" :class="{ 'is-collapsed': answerCollapsed }"><ArrowDown /></el-icon>
            </el-button>
          </div>
          <div class="answer-body" v-show="!answerCollapsed">
            <div class="answer-content">{{ aiAnswer }}</div>
            <div class="answer-highlight-tip" v-if="highlightedBuildingIds.length > 0">
              {{ t('autoHighlightedPrefix') }} {{ highlightedBuildingIds.length }} {{ t('autoHighlightedSuffix') }}
            </div>
            <div class="answer-related" v-if="buildings.length > 0">
              <span>{{ t('recommendContinueView') }}：</span>
              <el-button
                v-for="item in buildings.slice(0, 3)"
                :key="item.id"
                size="small"
                plain
                @click="goToDetail(item.id)"
              >
                {{ item.name }}
              </el-button>
            </div>
            <div class="route-actions" v-if="highlightedBuildingIds.length > 0">
              <el-button size="small" type="primary" plain :loading="routeLoading" @click="generateRouteFromHighlights">
                {{ t('generateRouteFromHighlights') }}
              </el-button>
              <el-button size="small" plain :loading="insightLoading" @click="generateDefenseInsights">
                {{ t('oneClickDefenseHighlights') }}
              </el-button>
              <el-button size="small" plain :loading="compareLoading" @click="generateComparison">
                {{ t('generateBuildingComparison') }}
              </el-button>
            </div>
            <div class="route-plan" v-if="routePlan.length > 0">
              <div class="route-title">{{ t('recommendedRoute') }}</div>
              <div class="route-station" v-for="(station, index) in routePlan" :key="index">
                <div class="station-index">{{ t('stationPrefix') }}{{ index + 1 }}{{ t('stationSuffix') }}</div>
                <div class="station-content">
                  <div class="station-name">{{ station.name }}</div>
                  <div class="station-reason">{{ station.reason }}</div>
                </div>
                <el-button size="small" @click="goToDetail(station.id)">{{ t('goTo') }}</el-button>
              </div>
            </div>
            <div class="insight-cards" v-if="defenseInsights.length > 0">
              <div class="route-title">{{ t('defenseHighlightCards') }}</div>
              <div class="insight-card" v-for="(item, index) in defenseInsights" :key="index">
                <div class="insight-title">{{ t('highlightLabel') }} {{ index + 1 }}：{{ item.title }}</div>
                <div class="insight-desc">{{ item.description }}</div>
              </div>
            </div>
            <div class="comparison-panel" v-if="comparisonResult">
              <div class="route-title">{{ t('buildingComparisonConclusion') }}</div>
              <div class="comparison-content">{{ comparisonResult }}</div>
            </div>
            <div class="task-panel" v-if="aiTasks.length > 0">
              <div class="route-title">{{ t('aiGuideTaskList') }}</div>
              <div class="task-item" v-for="task in aiTasks" :key="task.id">
                <el-checkbox v-model="task.done">{{ task.content }}</el-checkbox>
              </div>
              <div class="task-progress">
                {{ t('progressLabel') }}：{{ completedTaskCount }}/{{ aiTasks.length }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="6" v-for="building in buildings" :key="building.id">
        <div
          class="building-card"
          :class="{ 'ai-highlight': highlightedBuildingIds.includes(building.id) }"
          @click="goToDetail(building.id)"
        >
          <div class="card-image">
            <img :src="building.coverImage || defaultImage" :alt="building.name" />
          </div>
          <div class="card-content">
            <h3>{{ building.name }}</h3>
            <p class="dynasty">{{ building.dynasty }}</p>
            <p class="location">{{ building.location }}</p>
            <div class="card-stats">
              <span><el-icon><View /></el-icon> {{ building.viewCount }}</span>
              <span><el-icon><Star /></el-icon> {{ building.favoriteCount }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 36, 48]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <el-empty v-if="!loading && buildings.length === 0" :description="t('noBuildingData')" />

    <el-dialog
      v-model="createDialogVisible"
      class="model-upload-dialog"
      modal-class="model-upload-overlay"
      :title="t('addBuildingUpload')"
      width="760px"
    >
      <el-form :model="createForm" label-width="120px">
        <el-form-item :label="t('modelName')" required>
          <el-input v-model="createForm.modelName" :placeholder="t('enterModelName')" />
        </el-form-item>
        <el-form-item :label="t('modelCategory')">
          <el-select v-model="createForm.categoryId" :placeholder="t('selectCategoryOptional')" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dynasty')">
          <el-input v-model="createForm.dynasty" :placeholder="t('exampleDynasty')" />
        </el-form-item>
        <el-form-item :label="t('location')">
          <el-input v-model="createForm.location" :placeholder="t('exampleLocation')" />
        </el-form-item>
        <el-form-item :label="t('modelDescription')">
          <el-input v-model="createForm.description" type="textarea" :rows="3" :placeholder="t('modelDescriptionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('tagKeywords')">
          <div class="tag-editor">
            <div class="tag-list" v-if="createForm.tags.length > 0">
              <el-tag
                v-for="(tag, index) in createForm.tags"
                :key="`${tag}-${index}`"
                closable
                @close="removeTag(index)"
              >
                {{ tag }}
              </el-tag>
            </div>
            <el-input v-model="tagInput" :placeholder="t('tagInputPlaceholder')" @keyup.enter="addTag" />
            <div class="upload-tip">{{ t('multipleTagsOptional') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="t('downloadPermission')">
          <el-radio-group v-model="createForm.downloadPermission">
            <el-radio :value="1">{{ t('allowDownload') }}</el-radio>
            <el-radio :value="0">{{ t('denyDownload') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('publishStatus')">
          <el-tag class="publish-status-tag" type="warning" effect="light">
            {{ t('uploadPendingReview') }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="t('modelFile')" required>
          <el-upload
            class="upload-field"
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleModelFileChange"
            :on-remove="handleModelFileRemove"
            accept=".glb"
          >
            <el-button class="upload-trigger" type="primary" plain>{{ t('selectModelFile') }}</el-button>
          </el-upload>
          <div class="upload-tip">{{ t('modelFileTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('thumbnail')">
          <el-upload
            class="upload-field"
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleThumbnailChange"
            :on-remove="handleThumbnailRemove"
            accept=".png,.jpg,.jpeg,.webp"
          >
            <el-button class="upload-trigger" type="primary" plain>{{ t('selectThumbnailOptional') }}</el-button>
          </el-upload>
          <div class="upload-tip">{{ t('thumbnailTip') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">{{ t('upload') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useBuildingStore } from '@/stores/building'
import { useAuthStore } from '@/stores/auth'
import { Search, View, Star, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { streamChat } from '@/api/chat'
import { useUiPreferences } from '@/composables/useUiPreferences'

const router = useRouter()
const buildingStore = useBuildingStore()
const authStore = useAuthStore()
const { t } = useUiPreferences()

const loading = ref(false)
const buildings = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const searchKeyword = ref('')
const selectedDynasty = ref('')
const defaultImage = 'https://via.placeholder.com/300x200?text=No+Image'
const aiQuestion = ref('')
const aiAnswer = ref('')
const aiLoading = ref(false)
const aiSessionId = ref(`buildings-${Date.now()}`)
const highlightedBuildingIds = ref([])
const routePlan = ref([])
const routeLoading = ref(false)
const insightLoading = ref(false)
const compareLoading = ref(false)
const defenseInsights = ref([])
const comparisonResult = ref('')
const aiTasks = ref([])
const aiGuideMode = ref('guide')
const aiAudience = ref('visitor')
const aiQuickPrompts = computed(() => ([
  t('aiPromptQuickTour'),
  t('aiPromptMortise'),
  t('aiPromptLearningOrder'),
  t('aiPromptDynastyCompare')
]))
const completedTaskCount = computed(() => aiTasks.value.filter(item => item.done).length)
// 建议区折叠开关：生成完默认展开，用户可手动折叠，状态随导览缓存一起持久化
const answerCollapsed = ref(false)
// 标题旁的作用域标签：明确这轮建议针对的是哪批建筑
const answerScopeLabel = computed(() => {
  const parts = []
  if (aiContext.value.dynasty !== '未筛选朝代') parts.push(aiContext.value.dynasty)
  if (aiContext.value.keyword !== '无关键词') parts.push(`“${aiContext.value.keyword}”`)
  parts.push(`${aiContext.value.count} ${t('scopeBuildingsUnit')}`)
  return parts.join(' · ')
})

const toggleAnswerCollapsed = () => {
  answerCollapsed.value = !answerCollapsed.value
  persistAiGuideState()
}
const AI_GUIDE_CACHE_KEY = 'buildings-ai-guide-cache-v1'

const createDialogVisible = ref(false)
const creating = ref(false)
const categoryOptions = ref([])
const tagInput = ref('')
const modelFile = ref(null)
const thumbnailFile = ref(null)
const createForm = ref({
  modelName: '',
  categoryId: null,
  dynasty: '',
  location: '',
  description: '',
  tags: [],
  downloadPermission: 1
})

onMounted(() => {
  loadBuildings()
  restoreAiGuideState()
})

const loadBuildings = async () => {
  loading.value = true
  try {
    const data = await buildingStore.fetchBuildings(
      currentPage.value,
      pageSize.value,
      searchKeyword.value,
      selectedDynasty.value
    )
    buildings.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error('[Buildings] 加载建筑列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadBuildings()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  loadBuildings()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadBuildings()
}

const goToDetail = (id) => {
  router.push(`/buildings/${id}`)
}

const loadCategories = async () => {
  try {
    const response = await request.get('/categories')
    categoryOptions.value = response.data?.data || []
  } catch (error) {
    console.error('[Buildings] 加载分类失败', error)
    categoryOptions.value = []
  }
}

const resetCreateForm = () => {
  createForm.value = {
    modelName: '',
    categoryId: null,
    dynasty: '',
    location: '',
    description: '',
    tags: [],
    downloadPermission: 1
  }
  tagInput.value = ''
  modelFile.value = null
  thumbnailFile.value = null
}

const ensureUserInfoLoaded = async () => {
  if (!authStore.userInfo && authStore.token) {
    await authStore.fetchUserInfo()
  }
}

const handleCreate = async () => {
  if (!authStore.token) {
    router.push('/login')
    return
  }
  await ensureUserInfoLoaded()

  const role = authStore.userInfo?.role
  if (role === 'contributor' || role === 'admin') {
    await loadCategories()
    resetCreateForm()
    createDialogVisible.value = true
    return
  }
  ElMessage.info(t('applyContributorFirst'))
}

const addTag = () => {
  const value = tagInput.value.trim()
  if (!value) return
  if (createForm.value.tags.includes(value)) {
    ElMessage.warning(t('tagAlreadyExists'))
    return
  }
  createForm.value.tags.push(value)
  tagInput.value = ''
}

const removeTag = (index) => {
  createForm.value.tags.splice(index, 1)
}

const handleModelFileChange = (file) => {
  const fileName = (file.name || '').toLowerCase()
  if (!fileName.endsWith('.glb')) {
    ElMessage.warning(t('modelFileTip'))
    modelFile.value = null
    return false
  }
  modelFile.value = file.raw
  return true
}

const handleModelFileRemove = () => {
  modelFile.value = null
}

const handleThumbnailChange = (file) => {
  const fileName = (file.name || '').toLowerCase()
  const isSupported = fileName.endsWith('.png') || fileName.endsWith('.jpg')
    || fileName.endsWith('.jpeg') || fileName.endsWith('.webp')
  if (!isSupported) {
    ElMessage.warning(t('thumbnailTip'))
    thumbnailFile.value = null
    return false
  }
  thumbnailFile.value = file.raw
  return true
}

const handleThumbnailRemove = () => {
  thumbnailFile.value = null
}

const submitCreate = async () => {
  if (!createForm.value.modelName.trim()) {
    ElMessage.warning(t('enterModelName'))
    return
  }
  if (!modelFile.value) {
    ElMessage.warning(t('modelFileTip'))
    return
  }

  const formData = new FormData()
  formData.append('modelFile', modelFile.value)
  formData.append('modelName', createForm.value.modelName.trim())
  if (createForm.value.categoryId) formData.append('categoryId', String(createForm.value.categoryId))
  if (createForm.value.dynasty) formData.append('dynasty', createForm.value.dynasty)
  if (createForm.value.location) formData.append('location', createForm.value.location)
  if (createForm.value.description?.trim()) formData.append('description', createForm.value.description.trim())
  if (createForm.value.tags.length > 0) formData.append('tags', JSON.stringify(createForm.value.tags))
  formData.append('downloadPermission', String(createForm.value.downloadPermission))
  if (thumbnailFile.value) formData.append('thumbnailFile', thumbnailFile.value)

  creating.value = true
  try {
    const response = await request.post('/models', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const payload = response.data?.data ?? response.data
    if (payload?.status === 0) {
      ElMessage.success(t('modelUploadSuccessPending'))
    } else {
      ElMessage.success(t('modelUploadSuccess'))
    }
    createDialogVisible.value = false
    currentPage.value = 1
    await loadBuildings()
  } catch (error) {
    console.error('[Buildings] 模型上传失败', error)
    const backendMessage = error?.response?.data?.message
      || error?.response?.data?.error
      || error?.message
    ElMessage.error(backendMessage || t('modelUploadFailed'))
  } finally {
    creating.value = false
  }
}

const aiContext = computed(() => {
  // 给 AI 的建筑清单带上朝代和地点，建议才能贴着列表内容给
  const detail = buildings.value
    .slice(0, 8)
    .map(item => [item.name, item.dynasty, item.location].filter(Boolean).join('｜'))
    .filter(Boolean)
  return {
    keyword: searchKeyword.value || '无关键词',
    dynasty: selectedDynasty.value || '未筛选朝代',
    count: buildings.value.length,
    names: buildings.value.slice(0, 8).map(item => item.name).filter(Boolean),
    detail
  }
})

const fillAiQuestion = (prompt) => {
  aiQuestion.value = prompt
}

const updateHighlightedBuildings = (answerText) => {
  // 看回答里提到了哪些建筑名就高亮哪些，只是个 UI 提示，别当真检索
  if (!answerText || !Array.isArray(buildings.value)) {
    highlightedBuildingIds.value = []
    return
  }
  const hitIds = buildings.value
    .filter(item => item?.name && answerText.includes(item.name))
    .map(item => item.id)
  highlightedBuildingIds.value = hitIds
}

const getModePrompt = () => {
  const modeMap = {
    guide: '请使用通俗、生动、故事化表达，强调观众体验。',
    academic: '请使用结构化、术语准确的表达，突出结构逻辑和历史脉络。',
    defense: '请使用答辩风格表达，突出创新点、可验证性和展示价值。'
  }
  const audienceMap = {
    visitor: '受众是普通游客，避免过多专业术语。',
    student: '受众是建筑专业学生，可适当使用专业术语并解释。',
    judge: '受众是评委老师，请突出方法论、差异化和落地性。'
  }
  return `${modeMap[aiGuideMode.value]}${audienceMap[aiAudience.value]}`
}

const buildAiTasks = () => {
  // 给用户一个"接下来看什么"的清单，最多 3 条，不追求覆盖全部
  const targets = buildings.value.filter(item => highlightedBuildingIds.value.includes(item.id)).slice(0, 3)
  aiTasks.value = targets.map((item, index) => ({
    id: `${item.id}-${index}`,
    done: false,
    content: `第${index + 1}站观察「${item.name}」：记录1个结构亮点与1个文化信息`
  }))
}

const askAiGuide = async (overrideQuestion = '') => {
  if (!aiQuestion.value.trim() || aiLoading.value) return
  if (!authStore.token) {
    ElMessage.warning(t('loginFirst'))
    router.push('/login')
    return
  }

  aiLoading.value = true
  aiAnswer.value = ''
  highlightedBuildingIds.value = []
  routePlan.value = []
  defenseInsights.value = []
  comparisonResult.value = ''
  aiTasks.value = []
  answerCollapsed.value = false
  const question = (overrideQuestion || aiQuestion.value).trim()
  // 明确告诉 AI：本轮建议的对象是"当前筛选出的建筑列表"，而不是某个具体模型。
  // 之前把列表第一个建筑的 id 当 modelId 传给后端，后端会把它当"当前浏览的模型"
  // 注进 system prompt，导致所有回答都围着第一个建筑转——这就是"对象搞错"的根源。
  const contextQuestion = [
    '你是古建筑导览助手。本轮任务：针对【当前筛选出的建筑列表】给出观览建议，不是针对某一个模型做讲解。',
    '回答请覆盖列表整体，需要点名建筑时只能从下面的列表里选，禁止引入列表之外的建筑。',
    getModePrompt(),
    `当前筛选关键词：${aiContext.value.keyword}。`,
    `当前朝代筛选：${aiContext.value.dynasty}。`,
    `当前列表模型数量：${aiContext.value.count}。`,
    `当前列表建筑清单（名称｜朝代｜地点）：${aiContext.value.detail || '无'}。`,
    `用户问题：${question}`
  ].join('\n')

  try {
    await streamChat({
      question: contextQuestion,
      // 不传 modelId：导览建议面向的是列表，不是单个模型；传了反而污染后端上下文
      modelId: null,
      sessionId: aiSessionId.value,
      onMessage: (chunk) => {
        aiAnswer.value += chunk
      }
    })
    updateHighlightedBuildings(aiAnswer.value)
    buildAiTasks()
    persistAiGuideState()
  } catch (error) {
    console.error('[Buildings] 导览生成失败', error)
    ElMessage.error(t('aiGuideGenerateFailed'))
  } finally {
    aiLoading.value = false
  }
}

const generateThreePartScript = async () => {
  if (aiLoading.value) return
  const scriptPrompt = [
    '请严格按照以下三段结构输出导览脚本：',
    '【30秒介绍】',
    '【构造亮点】',
    '【推荐浏览顺序】',
    '每一段控制在3-5行，语言面向普通观众，避免学术腔。'
  ].join('\n')
  aiQuestion.value = scriptPrompt
  await askAiGuide(scriptPrompt)
}

const generateRouteFromHighlights = async () => {
  if (routeLoading.value) return
  const highlighted = buildings.value.filter(item => highlightedBuildingIds.value.includes(item.id)).slice(0, 6)
  if (highlighted.length === 0) {
    ElMessage.warning(t('noHighlightForRoute'))
    return
  }

  routeLoading.value = true
  try {
    const context = highlighted.map(item => item.name).join('、')
    let routeAnswer = ''
    await streamChat({
      question: [
        '请基于以下建筑名称，生成3站导览路线。',
        '输出要求：每行一个站点，格式为“建筑名｜推荐理由（20字以内）”。',
        `建筑：${context}`
      ].join('\n'),
      modelId: highlighted[0]?.id || null,
      sessionId: `${aiSessionId.value}-route`,
      onMessage: (chunk) => {
        routeAnswer += chunk
      }
    })

    const lines = routeAnswer.split('\n').map(item => item.trim()).filter(Boolean)
    const parsed = []
    for (const line of lines) {
      const normalized = line.replace(/^[-*]\s*/, '')
      const parts = normalized.split('｜')
      if (parts.length < 2) continue
      const name = parts[0].replace(/^第?\d+站[:：]?\s*/, '').trim()
      const reason = parts.slice(1).join('｜').trim()
      const target = highlighted.find(item => item.name === name) || highlighted.find(item => name.includes(item.name))
      if (target) {
        parsed.push({ id: target.id, name: target.name, reason: reason || '推荐重点浏览' })
      }
      if (parsed.length >= 3) break
    }

    if (parsed.length === 0) {
      routePlan.value = highlighted.slice(0, 3).map((item, index) => ({
        id: item.id,
        name: item.name,
        reason: index === 0 ? '先看整体风格建立认知' : (index === 1 ? '再看关键结构细节' : '最后对比总结差异')
      }))
    } else {
      routePlan.value = parsed
    }
    persistAiGuideState()
  } catch (error) {
    console.error('[Buildings] 路线生成失败', error)
    ElMessage.error(t('routeGenerateFailed'))
  } finally {
    routeLoading.value = false
  }
}

const generateDefenseInsights = async () => {
  if (insightLoading.value || aiLoading.value) return
  const highlighted = buildings.value.filter(item => highlightedBuildingIds.value.includes(item.id)).slice(0, 5)
  if (highlighted.length === 0) {
    ElMessage.warning(t('generateGuideFirst'))
    return
  }
  insightLoading.value = true
  try {
    const names = highlighted.map(item => item.name).join('、')
    let insightText = ''
    await streamChat({
      question: [
        '请基于以下建筑生成3条答辩亮点卡。',
        '输出格式：每行“标题｜说明（40字以内）”。',
        `建筑：${names}`
      ].join('\n'),
      modelId: highlighted[0]?.id || null,
      sessionId: `${aiSessionId.value}-insight`,
      onMessage: (chunk) => {
        insightText += chunk
      }
    })
    const parsed = insightText
      .split('\n')
      .map(line => line.trim().replace(/^[-*]\s*/, ''))
      .filter(Boolean)
      .map(line => {
        const [title, ...rest] = line.split('｜')
        return { title: (title || '').trim(), description: rest.join('｜').trim() }
      })
      .filter(item => item.title && item.description)
      .slice(0, 3)
    defenseInsights.value = parsed.length > 0 ? parsed : highlighted.slice(0, 3).map((item, index) => ({
      title: `${item.name}展示亮点`,
      description: index === 0 ? '可用于开场建立建筑认知' : (index === 1 ? '可用于结构特征对比说明' : '可用于创新价值总结')
    }))
    persistAiGuideState()
  } catch (error) {
    console.error('[Buildings] 答辩亮点生成失败', error)
    ElMessage.error(t('defenseHighlightGenerateFailed'))
  } finally {
    insightLoading.value = false
  }
}

const generateComparison = async () => {
  if (compareLoading.value || aiLoading.value) return
  const candidates = buildings.value.filter(item => highlightedBuildingIds.value.includes(item.id))
  const selected = (candidates.length >= 2 ? candidates : buildings.value).slice(0, 2)
  if (selected.length < 2) {
    ElMessage.warning(t('insufficientComparisonBuildings'))
    return
  }
  compareLoading.value = true
  try {
    let text = ''
    await streamChat({
      question: [
        '请对以下两座建筑给出对比结论。',
        '输出要求：1段话，包含“共同点、差异点、展示建议”，总字数80字以内。',
        `建筑A：${selected[0].name}`,
        `建筑B：${selected[1].name}`
      ].join('\n'),
      modelId: selected[0]?.id || null,
      sessionId: `${aiSessionId.value}-compare`,
      onMessage: (chunk) => {
        text += chunk
      }
    })
    comparisonResult.value = text.trim()
    persistAiGuideState()
  } catch (error) {
    console.error('[Buildings] 建筑对比生成失败', error)
    ElMessage.error(t('comparisonGenerateFailed'))
  } finally {
    compareLoading.value = false
  }
}

const persistAiGuideState = () => {
  try {
    const snapshot = {
      aiQuestion: aiQuestion.value,
      aiAnswer: aiAnswer.value,
      aiGuideMode: aiGuideMode.value,
      aiAudience: aiAudience.value,
      highlightedBuildingIds: highlightedBuildingIds.value,
      routePlan: routePlan.value,
      defenseInsights: defenseInsights.value,
      comparisonResult: comparisonResult.value,
      aiTasks: aiTasks.value,
      answerCollapsed: answerCollapsed.value
    }
    sessionStorage.setItem(AI_GUIDE_CACHE_KEY, JSON.stringify(snapshot))
  } catch (error) {
    console.warn('[Buildings] 保存导览状态失败', error)
  }
}

const restoreAiGuideState = () => {
  try {
    const raw = sessionStorage.getItem(AI_GUIDE_CACHE_KEY)
    if (!raw) return
    const snapshot = JSON.parse(raw)
    aiQuestion.value = snapshot.aiQuestion || ''
    aiAnswer.value = snapshot.aiAnswer || ''
    aiGuideMode.value = snapshot.aiGuideMode || 'guide'
    aiAudience.value = snapshot.aiAudience || 'visitor'
    highlightedBuildingIds.value = Array.isArray(snapshot.highlightedBuildingIds) ? snapshot.highlightedBuildingIds : []
    routePlan.value = Array.isArray(snapshot.routePlan) ? snapshot.routePlan : []
    defenseInsights.value = Array.isArray(snapshot.defenseInsights) ? snapshot.defenseInsights : []
    comparisonResult.value = snapshot.comparisonResult || ''
    aiTasks.value = Array.isArray(snapshot.aiTasks) ? snapshot.aiTasks : []
    answerCollapsed.value = Boolean(snapshot.answerCollapsed)
  } catch (error) {
    console.warn('[Buildings] 恢复导览状态失败', error)
  }
}

watch(
  [aiQuestion, aiAnswer, aiGuideMode, aiAudience, highlightedBuildingIds, routePlan, defenseInsights, comparisonResult, aiTasks],
  () => {
    persistAiGuideState()
  },
  { deep: true }
)
</script>

<style lang="scss" scoped>
.buildings-container {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h1 {
      font-size: 24px;
      color: #333;
    }
  }

  .search-bar {
    display: flex;
    gap: 15px;
    margin-bottom: 20px;
    background: white;
    padding: 20px;
    border-radius: 10px;

    :deep(.el-input__inner),
    :deep(.el-select__selected-item),
    :deep(.el-select__placeholder),
    :deep(.el-input__wrapper input) {
      color: #374151 !important;
    }

    :deep(.el-input__inner::placeholder),
    :deep(.el-textarea__inner::placeholder) {
      color: #9ca3af !important;
    }
  }

  .ai-guide-card {
    margin-bottom: 20px;

    .ai-guide-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      h3 {
        margin: 0 0 6px;
        font-size: 18px;
        color: #333;
      }

      p {
        margin: 0;
        color: #999;
        font-size: 13px;
      }
    }

    .ai-guide-body {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .ai-guide-config {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }

    .ai-guide-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
    }

    .action-buttons {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }

    .quick-prompts {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .quick-prompt-tag {
      cursor: pointer;
    }

    .ai-guide-answer {
      border: 1px solid #ebeef5;
      border-radius: 8px;
      padding: 12px;
      background: #fafcff;
    }

    .answer-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
      user-select: none;
    }

    .answer-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    // 标题旁的作用域标签：让用户一眼看到这轮建议针对哪些筛选结果
    .answer-scope {
      font-size: 12px;
      font-weight: 400;
      color: #909399;
      background: #f0f4f8;
      border-radius: 4px;
      padding: 2px 8px;
    }

    .collapse-toggle {
      padding: 4px 8px;

      .collapse-icon {
        margin-left: 4px;
        transition: transform 0.25s ease;

        &.is-collapsed {
          transform: rotate(-90deg);
        }
      }
    }

    .answer-body {
      margin-top: 10px;
    }

    .answer-content {
      white-space: pre-wrap;
      color: #606266;
      line-height: 1.7;
      margin-bottom: 10px;
    }

    .answer-related {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      color: #909399;
      font-size: 13px;
    }

    .answer-highlight-tip {
      margin-bottom: 10px;
      color: #67c23a;
      font-size: 13px;
    }

    .route-actions {
      margin-top: 10px;
      margin-bottom: 10px;
    }

    .route-plan {
      margin-top: 8px;
      border-top: 1px dashed #dcdfe6;
      padding-top: 10px;
    }

    .route-title {
      font-size: 14px;
      color: #303133;
      font-weight: 600;
      margin-bottom: 8px;
    }

    .route-station {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 0;
    }

    .station-index {
      color: #409eff;
      font-weight: 600;
      min-width: 52px;
    }

    .station-content {
      flex: 1;
    }

    .station-name {
      color: #303133;
      font-size: 13px;
      margin-bottom: 2px;
    }

    .station-reason {
      color: #909399;
      font-size: 12px;
    }

    .insight-cards,
    .comparison-panel,
    .task-panel {
      margin-top: 10px;
      border-top: 1px dashed #dcdfe6;
      padding-top: 10px;
    }

    .insight-card {
      padding: 8px 0;
    }

    .insight-title {
      color: #303133;
      font-size: 13px;
      font-weight: 600;
      margin-bottom: 4px;
    }

    .insight-desc,
    .comparison-content {
      color: #606266;
      font-size: 13px;
      line-height: 1.6;
      white-space: pre-wrap;
    }

    .task-item {
      padding: 4px 0;
    }

    .task-progress {
      margin-top: 8px;
      color: #909399;
      font-size: 12px;
    }
  }

  .building-card {
    background: white;
    border-radius: 10px;
    overflow: hidden;
    cursor: pointer;
    transition: transform 0.3s, box-shadow 0.3s;
    margin-bottom: 20px;

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
    }

    &.ai-highlight {
      border: 2px solid #67c23a;
      box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.15);
    }

    .card-image {
      height: 180px;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .card-content {
      padding: 15px;

      h3 {
        font-size: 16px;
        color: #333;
        margin-bottom: 8px;
      }

      .dynasty {
        color: #667eea;
        font-size: 14px;
        margin-bottom: 5px;
      }

      .location {
        color: #999;
        font-size: 12px;
        margin-bottom: 10px;
      }

      .card-stats {
        display: flex;
        gap: 15px;
        color: #999;
        font-size: 12px;

        span {
          display: flex;
          align-items: center;
          gap: 3px;
        }
      }
    }
  }

  .pagination {
    display: flex;
    justify-content: center;
    margin-top: 30px;
  }

  .tag-editor {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 10px;

    .tag-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  }

  .upload-field {
    width: 100%;
  }

  .upload-tip {
    margin-top: 6px;
    font-size: 12px;
    color: #999;
  }

  :deep(.publish-status-tag) {
    display: inline-flex;
    align-items: center;
    height: 32px;
    padding: 0 14px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.2px;
    color: #7c2d12;
    background: #fff7ed;
    border: 1px solid #fed7aa;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
  }
}
</style>
