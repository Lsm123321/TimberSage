<template>
  <!--
    embedded 模式：作为独立页面的主体使用，去掉悬浮按钮、常驻展开、窗口铺满容器。
    这样导航里的「AI 知识问答」和详情页的悬浮问答复用同一套逻辑与样式。
  -->
  <div class="chat-float-container" :class="{ expanded: isExpanded, 'is-embedded': embedded }">
    <div class="chat-toggle" v-if="!embedded" @click="toggleChat">
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="9">
        <el-icon v-if="!isExpanded"><ChatRound /></el-icon>
        <el-icon v-else><Close /></el-icon>
      </el-badge>
    </div>

    <div class="chat-window" v-show="embedded || isExpanded">
      <div class="chat-header" :class="{ 'is-embedded': embedded }">
        <div class="chat-title-group">
          <div class="chat-title-icon" v-if="embedded">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="chat-title-text">
            <h3>智能知识问答</h3>
            <p v-if="embedded">基于古建筑知识库，支持多轮追问与来源溯源</p>
          </div>
        </div>
        <el-tooltip content="清空对话" placement="bottom">
          <el-button text @click="clearChat">
            <el-icon><Delete /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <div class="preset-questions" v-if="presetQuestions.length > 0">
        <div class="preset-title">常见问题</div>
        <div class="preset-list">
          <el-tag
            v-for="question in presetQuestions"
            :key="question.id"
            class="preset-tag"
            @click="sendPresetQuestion(question.questionText)"
          >
            {{ question.questionText }}
          </el-tag>
        </div>
      </div>

      <div class="chat-messages" ref="messagesContainer">
        <!--
          欢迎态：没有对话时给一块引导，告诉用户能问什么、答案从哪来。
          这些信息原来放在问答页左侧栏，既然那栏和预设问题重复被删了，
          就收到这里，新用户进页面不会只看到一个空框。
        -->
        <div class="chat-welcome" v-if="messages.length === 0 && !isLoading">
          <div class="welcome-badge">
            <el-icon><MagicStick /></el-icon>
          </div>
          <h4>从一句话开始了解古建筑</h4>
          <p>
            支持追问建筑构造、朝代特征与榫卯做法；<br />
            回答会标注参考来源，方便你回到原文核对。
          </p>
          <div class="welcome-points">
            <span class="point">构造做法</span>
            <span class="point">朝代对比</span>
            <span class="point">榫卯类型</span>
            <span class="point">桥梁形制</span>
          </div>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-item"
          :class="msg.type"
        >
          <div class="message-avatar">
            <el-avatar :size="32" :src="msg.type === 'ai' ? aiAvatar : userAvatar">
              {{ msg.type === 'ai' ? 'AI' : 'U' }}
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-text" v-if="msg.type === 'user'">{{ msg.content }}</div>
            <div
              class="message-text ai-message"
              :class="{ streaming: !msg.isComplete }"
              v-else
              v-html="formatMessage(msg.content)"
            ></div>
            <div
              class="message-references"
              :class="{ 'has-toggle': hasReferenceToggle(msg) }"
              v-if="msg.type === 'ai' && msg.isComplete && (getMessageReferences(msg).length > 0 || getMessageEvidences(msg).length > 0)"
            >
              <div class="ref-title">
                参考来源 · {{ formatTime(msg.sourceTimestamp || msg.timestamp) }}
              </div>
              <el-button
                size="small"
                class="ref-toggle-btn"
                v-if="hasReferenceToggle(msg)"
                @click="toggleReferenceExpand(msg)"
              >
                来源 {{ getVisibleReferences(msg).length }}/{{ getMessageReferences(msg).length }}
                · {{ msg.showAllReferences ? '收起' : '展开' }}
              </el-button>
              <a
                v-for="(ref, refIndex) in getVisibleReferences(msg)"
                :key="`${index}-${refIndex}`"
                class="ref-link"
                :href="ref"
                target="_blank"
                rel="noopener noreferrer"
              >
                {{ simplifyUrl(ref) }}
              </a>
              <span
                v-for="(evidence, evidenceIndex) in getMessageEvidences(msg)"
                :key="`evidence-${index}-${evidenceIndex}`"
                class="ref-link ref-text"
              >
                {{ evidence }}
              </span>
            </div>
            <!--
              知识库来源标签：命中审核过的档案时显示。
              这是建立信任的关键——用户知道答案是查出来的，不是模型现编的。
              展示被问次数，让人看到"这条知识已经被验证过很多次"。
            -->
            <div
              class="message-kb-source"
              v-if="msg.type === 'ai' && msg.isComplete && msg.kbSource"
            >
              <el-icon class="kb-icon"><Collection /></el-icon>
              <span class="kb-text">
                来自知识库
                <template v-if="msg.kbSource.questionType">
                  · {{ getKbTypeLabel(msg.kbSource.questionType) }}
                </template>
              </span>
              <span class="kb-hit" v-if="msg.kbSource.hitCount > 1">
                已被 {{ msg.kbSource.hitCount }} 人问过
              </span>
            </div>
            <div
              class="message-confidence"
              :class="`level-${msg.confidence || 'medium'}`"
              v-if="msg.type === 'ai' && msg.isComplete"
            >
              置信度：{{ getConfidenceLabel(msg.confidence) }}
            </div>
            <!-- 越界/通识拦截：后端明确表示答不了，给出可问的方向，而不是让模型硬编 -->
            <div
              class="message-fallback"
              v-if="msg.type === 'ai' && msg.isComplete && msg.fallbackReason"
            >
              <div class="fallback-title">
                <el-icon><WarningFilled /></el-icon>
                <span>{{ getFallbackTitle(msg.fallbackReason) }}</span>
              </div>
              <div class="fallback-desc">{{ getFallbackDesc(msg.fallbackReason) }}</div>
              <div class="fallback-suggest" v-if="msg.suggestedQuestions.length > 0">
                <span class="suggest-label">可以这样问：</span>
                <button
                  v-for="(q, qi) in msg.suggestedQuestions.slice(0, 3)"
                  :key="qi"
                  type="button"
                  class="suggest-item"
                  @click="askSuggested(q)"
                >
                  {{ q }}
                </button>
              </div>
            </div>
            <div
              class="message-evidence-warning"
              v-if="msg.type === 'ai' && msg.isComplete && msg.noEvidence === true"
            >
              暂未检测到可验证依据，请谨慎采纳结论。
            </div>
            <div
              class="message-no-references"
              v-if="msg.type === 'ai' && msg.isComplete && !msg.kbSource && msg.noEvidence !== true && getMessageReferences(msg).length === 0 && getMessageEvidences(msg).length === 0"
            >
              {{ t('noExternalSourceDetected') }}
            </div>
            <div class="message-feedback" v-if="msg.type === 'ai' && msg.showFeedback">
              <el-button
                size="small"
                :type="msg.helpful === true ? 'success' : 'default'"
                @click="submitFeedback(msg, true)"
              >
                <el-icon><Top /></el-icon>
                有用
              </el-button>
              <el-button
                size="small"
                :type="msg.helpful === false ? 'danger' : 'default'"
                @click="submitFeedback(msg, false)"
              >
                <el-icon><Bottom /></el-icon>
                无用
              </el-button>
            </div>
            <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
          </div>
        </div>

        <div v-if="isLoading" class="message-item ai">
          <div class="message-avatar">
            <el-avatar :size="32" :src="aiAvatar">AI</el-avatar>
          </div>
          <div class="message-content">
            <div class="message-text ai-message typing">
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          placeholder="输入问题，按回车发送..."
          @keyup.enter="sendMessage"
          :disabled="isLoading"
        >
          <template #append>
            <el-button @click="sendMessage" :disabled="isLoading || !inputMessage">
              <el-icon><Promotion /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { ChatRound, Close, Delete, Top, Bottom, Promotion, MagicStick, Collection, WarningFilled } from '@element-plus/icons-vue'
import { getPresetQuestions, streamChat } from '@/api/chat'
import { useUiPreferences } from '@/composables/useUiPreferences'

const props = defineProps({
  modelId: {
    type: Number,
    default: null
  },
  // 作为独立页面主体嵌入时置 true：不渲染悬浮球、窗口常驻铺满
  embedded: {
    type: Boolean,
    default: false
  },
  /*
    预设问题的展示场景，和后台「预设问题 → 展示位置」一一对应：
    chat = AI 问答页，model = 模型详情页悬浮问答。
    不传时按组件形态推断（embedded 视为 chat，否则 model），
    这样老调用方不用改也不会串场。
  */
  scene: {
    type: String,
    default: ''
  }
})

const authStore = useAuthStore()
const { t } = useUiPreferences()

// 嵌入模式默认就是展开状态，否则页面进来是一片空白
const isExpanded = ref(props.embedded)
const isLoading = ref(false)
const inputMessage = ref('')
const messages = ref([])
const unreadCount = ref(0)
const presetQuestions = ref([])
const messagesContainer = ref(null)
const sessionId = ref(Date.now().toString())

const aiAvatar = ''
const userAvatar = computed(() => authStore.userInfo?.avatar || '')
const isLoggedIn = computed(() => !!authStore.token)

onMounted(() => {
  loadPresetQuestions()
})

const toggleChat = () => {
  isExpanded.value = !isExpanded.value
  if (isExpanded.value) {
    unreadCount.value = 0
    nextTick(() => {
      scrollToBottom()
    })
  }
}

const loadPresetQuestions = async () => {
  try {
    /*
      按场景取问题：
      - scene=chat（AI 问答页）只取全局问题，不掺模型专属问题；
      - scene=model（模型详情页悬浮）取该模型专属 + 全局问题。
      未显式传 scene 时按组件形态推断，保证旧调用不出错。
    */
    const scene = props.scene || (props.embedded ? 'chat' : 'model')
    const response = await getPresetQuestions(props.modelId, scene)
    const data = response?.data
    // 兼容两种返回：直接数组 / ApiResponse 包装
    presetQuestions.value = Array.isArray(data) ? data : (data?.data || [])
  } catch (error) {
    console.error('[ChatFloat] 加载预设问题失败', error)
    presetQuestions.value = []
  }
}

watch(
  () => props.modelId,
  () => {
    loadPresetQuestions()
  }
)

const sendPresetQuestion = (question) => {
  inputMessage.value = question
  sendMessage()
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return

  /*
    AI 问答属于"只读"能力，游客也能用——这也是项目对外展示的一部分。
    只有登录用户才需要把本次对话写进个人中心历史，所以 record 跟着登录态走。
  */
  const question = inputMessage.value.trim()
  // 在用户原问题后面追加几条“输出约束”，主要目的是：
  // - 让回答更克制，尽量把“依据/不确定性”说清楚
  // - 方便前端从文本末尾提取“参考依据：”这一段作为展示
  const guardedQuestion = [
    question,
    '',
    '请严格遵守以下回答规则：',
    '1) 仅在有明确依据时给出结论，无法确认请明确写“暂无可靠依据”。',
    '2) 回答末尾单独输出“参考依据：”小节；若有来源请给出可访问链接，若没有请写“无外部可验证链接”。',
    '3) 涉及时间信息时请写完整日期时间（YYYY-MM-DD HH:mm:ss）。'
  ].join('\n')
  inputMessage.value = ''

  messages.value.push({
    type: 'user',
    content: question,
    timestamp: new Date(),
    showFeedback: false,
    helpful: null
  })

  scrollToBottom()

  isLoading.value = true
  messages.value.push({
    type: 'ai',
    content: '',
    timestamp: new Date(),
    sourceTimestamp: new Date(),
    confidence: 'medium',
    references: [],
    evidenceItems: [],
    noEvidence: null,
    showAllReferences: false,
    showFeedback: false,
    helpful: null,
    isComplete: false,
    // 知识库来源：非空表示这条回答直接来自审核过的档案，不是大模型现编的
    kbSource: null,
    // 未能回答的原因（OUT_OF_SCOPE / GENERAL），非空时展示兜底卡片
    fallbackReason: null,
    suggestedQuestions: []
  })

  try {
    const lastMessage = messages.value[messages.value.length - 1]
    await streamChat({
      question: guardedQuestion,
      modelId: props.modelId,
      sessionId: sessionId.value,
      // record：仅登录用户在模型页的问答才写入个人中心历史
      record: isLoggedIn.value && !!props.modelId,
      onMessage: (chunk) => {
        lastMessage.content += chunk
        scrollToBottom()
      },
      onMeta: (meta) => {
        // meta 是后端在回答完成时补发的结构化信息（可选）
        if (!meta || typeof meta !== 'object') return
        lastMessage.references = Array.isArray(meta.references) ? meta.references : []
        lastMessage.evidenceItems = Array.isArray(meta.evidenceItems) ? meta.evidenceItems : []
        lastMessage.confidence = ['high', 'medium', 'low'].includes(meta.confidence) ? meta.confidence : 'medium'
        lastMessage.noEvidence = meta.noEvidence === true
        if (meta.generatedAt) {
          lastMessage.sourceTimestamp = meta.generatedAt
        }
        // 知识库命中：回答来自审核过的档案，展示"来自知识库"标签建立信任
        lastMessage.kbSource = meta.kbSource || null
        // 越界/通识拦截：后端没给答案，给出兜底提示和建议问法
        lastMessage.fallbackReason = meta.fallbackReason || null
        lastMessage.suggestedQuestions = Array.isArray(meta.suggestedQuestions)
          ? meta.suggestedQuestions
          : []
      }
    })

    if (!lastMessage.isComplete) {
      lastMessage.isComplete = true
      lastMessage.showFeedback = true
    }
  } catch (error) {
    console.error('[ChatFloat] 发送消息失败', error)
    const lastMessage = messages.value[messages.value.length - 1]
    lastMessage.content = error?.message ? `发送失败：${error.message}` : '抱歉，发生了错误，请稍后重试。'
    lastMessage.showFeedback = false
    lastMessage.isComplete = true
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

const submitFeedback = async (message, helpful) => {
  message.helpful = helpful
  ElMessage.success(helpful ? '感谢您的反馈' : '感谢您的反馈')
}

const clearChat = () => {
  messages.value = []
  sessionId.value = Date.now().toString()
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

/**
 * 把 AI 返回的轻量 Markdown 转成 HTML。
 *
 * 为什么自己写而不用 marked：这里只需要粗体、行内代码、标题、列表这几种，
 * 引入完整解析器会让首屏多打包几十 KB。但自己写就必须自己处理 XSS：
 * 先整体转义尖括号，再放行我们生成的标签，模型输出里夹带的 <script> 就失效了。
 */
const escapeHtml = (text) => text
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')

const formatMessage = (content) => {
  if (!content) return ''

  // 先转义，后面每一步都只插入受控标签
  let html = escapeHtml(String(content))

  // 行内代码 `code`：先处理，避免内部内容被后续规则误伤
  html = html.replace(/`([^`\n]+)`/g, '<code class="md-code">$1</code>')

  // 粗体 **text** 与 __text__
  html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/__([^_\n]+)__/g, '<strong>$1</strong>')

  // 斜体 *text*（要求前后不是星号，避免和粗体规则打架）
  html = html.replace(/(^|[\s（(])\*([^*\n]+)\*(?=$|[\s）)，,。.!?])/g, '$1<em>$2</em>')

  // Markdown 链接 [text](url)
  html = html.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
  )

  // 裸链接自动识别（跳过已经是 href 的部分）
  html = html.replace(
    /(^|[\s(])(https?:\/\/[^\s<]+)/g,
    '$1<a href="$2" target="_blank" rel="noopener noreferrer">$2</a>'
  )

  // 行首的 # / ## / ### 标题
  html = html.replace(/^#{1,6}\s+(.+)$/gm, '<div class="md-heading">$1</div>')

  // 行首的 - / * / • 无序列表项，统一成带圆点的行
  html = html.replace(/^[\s]*[-*•]\s+(.+)$/gm, '<div class="md-li">$1</div>')

  // 行首的 1. 2. 数字列表
  // 数字后面可能直接跟粗体（"1.**建筑风格**：..."），所以分隔符放宽为「可有可无的空格」
  html = html.replace(/^[\s]*(\d+)[.、]\s*(.+)$/gm, '<div class="md-li md-li-num"><span class="md-li-index">$1.</span>$2</div>')

  // 剩下的换行转 <br>
  return html.replace(/\n/g, '<br>')
}

const getMessageReferences = (msg) => {
  if (Array.isArray(msg?.references) && msg.references.length > 0) {
    return msg.references.slice(0, 6)
  }
  if (!msg?.content) return []
  const links = new Set()
  const regex = /https?:\/\/[^\s<)"']+/g
  for (const match of msg.content.matchAll(regex)) {
    if (match[0]) links.add(match[0])
  }
  return Array.from(links).slice(0, 4)
}

const getMessageEvidences = (msg) => {
  if (Array.isArray(msg?.evidenceItems) && msg.evidenceItems.length > 0) {
    return msg.evidenceItems.slice(0, 6)
  }
  const text = (msg?.content || '').trim()
  if (!text) return []
  const marker = '参考依据：'
  const markerIndex = text.lastIndexOf(marker)
  if (markerIndex === -1) return []
  const evidenceText = text.slice(markerIndex + marker.length).trim()
  if (!evidenceText) return []
  return evidenceText
    .split('\n')
    .map(item => item.replace(/^[-*]\s*/, '').trim())
    .filter(Boolean)
    .slice(0, 4)
}

const getConfidenceLabel = (level) => {
  if (level === 'high') return '高'
  if (level === 'low') return '低'
  return '中'
}

/** 知识类型的中文名，和后端 KnowledgeGraphService.TYPE_LABELS 保持一致 */
const KB_TYPE_LABELS = {
  structure: '结构',
  material: '材料',
  history: '历史',
  culture: '文化',
  aesthetic: '审美',
  compare: '对比'
}
const getKbTypeLabel = (type) => KB_TYPE_LABELS[type] || type || ''

/** 兜底卡片文案：让用户知道为什么答不了，而不是看到一句冷冰冰的报错 */
const getFallbackTitle = (reason) => {
  if (reason === 'OUT_OF_SCOPE') return '这个问题超出了知识档案的范围'
  if (reason === 'GENERAL') return '这是一般性知识问题'
  return '暂时无法回答'
}

const getFallbackDesc = (reason) => {
  if (reason === 'OUT_OF_SCOPE') {
    return '当前档案只收录结构做法、材料选择、历史背景、文化内涵、艺术特色、同类比较这几类内容，造价、抗震等级、模型参数这类问题没有可靠数据。'
  }
  if (reason === 'GENERAL') {
    return '这个问题不针对当前展示的建筑，属于通用的古建筑知识，建议直接向 AI 提问获取系统性解答。'
  }
  return '请换一种问法，或稍后重试。'
}

/** 点兜底卡片里的建议问法，直接发出去 */
const askSuggested = (question) => {
  if (!question || isLoading.value) return
  inputMessage.value = question
  sendMessage()
}

const getVisibleReferences = (msg) => {
  const refs = getMessageReferences(msg)
  if (msg?.showAllReferences) return refs
  return refs.slice(0, 2)
}

const hasReferenceToggle = (msg) => getMessageReferences(msg).length > 2

const toggleReferenceExpand = (msg) => {
  msg.showAllReferences = !msg.showAllReferences
}

const simplifyUrl = (url) => {
  try {
    const parsed = new URL(url)
    return `${parsed.hostname}${parsed.pathname.length > 24 ? `${parsed.pathname.slice(0, 24)}...` : parsed.pathname}`
  } catch {
    return url
  }
}

const formatTime = (date) => {
  if (!date) return ''
  const d = new Date(date)
  const yyyy = d.getFullYear()
  const MM = (d.getMonth() + 1).toString().padStart(2, '0')
  const dd = d.getDate().toString().padStart(2, '0')
  const hh = d.getHours().toString().padStart(2, '0')
  const mm = d.getMinutes().toString().padStart(2, '0')
  const ss = d.getSeconds().toString().padStart(2, '0')
  return `${yyyy}-${MM}-${dd} ${hh}:${mm}:${ss}`
}

// 供页面级组件（AI 问答页）从外部触发提问或清空对话
defineExpose({ sendPresetQuestion, clearChat })
</script>

<style scoped>
.chat-float-container {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 1000;

  /*
    嵌入模式：作为独立页面主体时取消固定定位，
    让窗口直接铺满父容器（父容器负责限高与圆角）。
  */
  &.is-embedded {
    position: relative;
    inset: auto;
    bottom: auto;
    right: auto;
    width: 100%;
    height: 100%;
    min-height: 0;
    z-index: auto;

    .chat-window {
      position: relative;
      bottom: auto;
      right: auto;
      width: 100%;
      height: 100%;
      border-radius: 18px;
    }
  }

  .chat-toggle {
    width: 50px;
    height: 50px;
    background: linear-gradient(135deg, var(--v2-gold), color-mix(in srgb, var(--v2-gold) 62%, #000));
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 8px 22px color-mix(in srgb, var(--v2-gold) 36%, transparent);
    transition: transform 0.3s;

    &:hover {
      transform: scale(1.1);
    }

    .el-icon {
      font-size: 24px;
      color: var(--v2-accent-contrast);
    }
  }

  .chat-window {
    position: absolute;
    bottom: 70px;
    right: 0;
    /* 手机竖屏时 380px 会超出屏幕，跟随视口收窄；高度同理避开上下栏 */
    width: min(380px, calc(100vw - 24px));
    height: min(520px, calc(100vh - 140px));
    /* 配色跟随主题：深色是墨玉 + 暖金，浅色是宣纸 + 深墨，不再是写死的淡紫蓝 */
    background: var(--v2-chat-window);
    border-radius: 16px;
    border: 1px solid var(--v2-chat-border);
    box-shadow: var(--v2-shadow);
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .chat-header {
      padding: 14px 18px;
      background: var(--v2-chat-header);
      color: var(--v2-chat-header-text);
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--v2-chat-border);

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        letter-spacing: 0.3px;
      }

      /* 嵌入模式下头部加高，带上副标题与图标，更像一个页面而不是浮窗 */
      &.is-embedded {
        padding: 18px 22px;

        .chat-title-group {
          display: flex;
          align-items: center;
          gap: 12px;
          min-width: 0;
        }

        .chat-title-icon {
          width: 38px;
          height: 38px;
          flex: none;
          display: grid;
          place-items: center;
          border-radius: 12px;
          background: color-mix(in srgb, var(--v2-gold) 22%, transparent);
          border: 1px solid var(--v2-chip-border);

          .el-icon {
            font-size: 19px;
            color: var(--v2-gold);
          }
        }

        .chat-title-text {
          min-width: 0;

          h3 {
            font-size: 17px;
          }

          p {
            margin: 3px 0 0;
            font-size: 12px;
            color: var(--v2-chat-header-sub);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }
      }
    }

    .preset-questions {
      padding: 10px 15px;
      background: var(--v2-chat-preset-bg);
      border-bottom: 1px solid var(--v2-chat-border);

      .preset-title {
        font-size: 12px;
        color: var(--v2-text-weak);
        font-weight: 600;
        margin-bottom: 8px;
      }

      .preset-list {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;

        .preset-tag {
          cursor: pointer;
          font-size: 12px;
        }
      }
    }

    .chat-messages {
      flex: 1;
      overflow-y: auto;
      padding: 16px 14px;
      scroll-behavior: smooth;
      background: var(--v2-chat-body);

      /* 空对话的引导块 */
      .chat-welcome {
        padding: 26px 18px 22px;
        text-align: center;
        animation: welcome-in 420ms cubic-bezier(.2,.8,.2,1) both;

        .welcome-badge {
          width: 46px;
          height: 46px;
          margin: 0 auto 14px;
          display: grid;
          place-items: center;
          border-radius: 16px;
          background: var(--v2-chip-bg);
          border: 1px solid var(--v2-chip-border);

          .el-icon {
            font-size: 22px;
            color: var(--v2-gold);
          }
        }

        h4 {
          margin: 0 0 8px;
          font-size: 15px;
          font-weight: 700;
          color: var(--v2-text-strong);
        }

        p {
          margin: 0 0 16px;
          font-size: 12.5px;
          line-height: 1.75;
          color: var(--v2-text-weak);
        }

        .welcome-points {
          display: flex;
          flex-wrap: wrap;
          justify-content: center;
          gap: 6px;

          .point {
            padding: 4px 10px;
            border-radius: 999px;
            font-size: 11px;
            color: var(--v2-chip-text);
            background: var(--v2-chip-bg);
            border: 1px solid var(--v2-chip-border);
          }
        }
      }

      &::-webkit-scrollbar {
        width: 8px;
      }

      &::-webkit-scrollbar-track {
        background: transparent;
      }

      &::-webkit-scrollbar-thumb {
        border-radius: 999px;
        background: var(--v2-chip-border);
      }

      .message-item {
        display: flex;
        margin-bottom: 14px;
        align-items: flex-end;

        &.user {
          flex-direction: row-reverse;

          .message-content {
            align-items: flex-end;
            margin-right: 10px;
            margin-left: 0;
          }

          .message-text {
            background: var(--v2-chat-user-bg);
            color: var(--v2-chat-user-text);
            border-top-right-radius: 6px;
            box-shadow: var(--v2-shadow-soft);
          }
        }

        &.ai {
          .message-content {
            align-items: flex-start;
            margin-left: 10px;
          }

          .message-text {
            background: var(--v2-chat-ai-bg);
            color: var(--v2-chat-ai-text);
            border-top-left-radius: 6px;
            border: 1px solid var(--v2-chat-ai-border);
            box-shadow: none;
          }
        }

        .message-avatar {
          flex-shrink: 0;

          :deep(.el-avatar) {
            border: 2px solid var(--v2-chat-ai-border);
            box-shadow: none;
            color: var(--v2-accent-contrast);
            font-weight: 700;
            background: linear-gradient(140deg, var(--v2-gold), color-mix(in srgb, var(--v2-gold) 70%, #000));
          }
        }

        .message-content {
          display: flex;
          flex-direction: column;
          max-width: 70%;

          .message-text {
            padding: 10px 13px;
            border-radius: 14px;
            font-size: 14px;
            line-height: 1.65;
            word-break: break-word;
            /*
              不能再用 pre-wrap：formatMessage 已经把 \n 转成 <br> 了，
              两个叠在一起会出现双倍行距。用 normal 让 <br> 独立负责换行。
            */
            white-space: normal;

            /* AI 回答里的轻量 Markdown 元素 */
            :deep(strong) {
              font-weight: 700;
              color: var(--v2-text-strong);
            }

            :deep(em) {
              font-style: italic;
              opacity: 0.92;
            }

            :deep(a) {
              color: var(--v2-gold);
              text-decoration: underline;
              text-underline-offset: 2px;
              word-break: break-all;

              &:hover {
                color: color-mix(in srgb, var(--v2-gold) 78%, #fff);
              }
            }

            :deep(.md-code) {
              padding: 1px 5px;
              border-radius: 5px;
              background: var(--v2-chip-bg);
              color: var(--v2-chip-text);
              font-family: Consolas, Monaco, monospace;
              font-size: 12.5px;
            }

            /* 标题行：左侧一条短竖线，和正文拉开层次 */
            :deep(.md-heading) {
              margin: 8px 0 4px;
              padding-left: 8px;
              border-left: 3px solid var(--v2-gold);
              font-weight: 700;
              color: var(--v2-text-strong);

              &:first-child {
                margin-top: 0;
              }
            }

            /* 列表项：圆点/序号用伪元素或内联 span 定位，保证多行也缩进对齐 */
            :deep(.md-li) {
              position: relative;
              padding-left: 14px;
              margin: 3px 0;

              &::before {
                content: '';
                position: absolute;
                left: 3px;
                top: 0.68em;
                width: 5px;
                height: 5px;
                border-radius: 50%;
                background: var(--v2-gold);
              }

              &.md-li-num {
                padding-left: 20px;

                &::before {
                  display: none;
                }

                .md-li-index {
                  position: absolute;
                  left: 0;
                  color: var(--v2-gold);
                  font-weight: 700;
                }
              }
            }

            &.typing {
              display: flex;
              gap: 4px;
              padding: 12px 16px;

              .typing-dot {
                width: 6px;
                height: 6px;
                background: var(--v2-text-weak);
                border-radius: 50%;
                animation: typing 1.4s infinite;

                &:nth-child(2) {
                  animation-delay: 0.2s;
                }

                &:nth-child(3) {
                  animation-delay: 0.4s;
                }
              }
            }
          }

          .ai-message.streaming {
            position: relative;
          }

          .ai-message.streaming::after {
            content: '';
            display: inline-block;
            width: 8px;
            height: 1.15em;
            margin-left: 4px;
            vertical-align: text-bottom;
            border-radius: 2px;
            background: var(--v2-gold);
            animation: stream-caret 0.9s steps(1, end) infinite;
          }

          .message-references {
            margin-top: 8px;
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            max-width: 100%;
            position: relative;

            .ref-title {
              width: 100%;
              font-size: 11px;
              color: var(--v2-text-weak);
              font-weight: 600;
            }

            .ref-toggle-btn {
              position: absolute;
              top: -2px;
              right: 0;
              font-size: 11px;
              height: 24px;
              padding: 0 10px;
              border-radius: 999px;
              border: 1px solid var(--v2-chip-border) !important;
              background: var(--v2-chip-bg) !important;
              color: var(--v2-chip-text) !important;
              font-weight: 600;
              box-shadow: none !important;
              transition: all 0.2s ease;
              animation: ref-toggle-glow 1.8s ease-in-out infinite;
            }

            .ref-toggle-btn:hover {
              color: var(--v2-text-strong) !important;
              border-color: var(--v2-gold) !important;
              background: color-mix(in srgb, var(--v2-gold) 24%, transparent) !important;
              transform: translateY(-1px);
              animation: none;
            }

            &.has-toggle {
              padding-top: 24px;
            }

            &.has-toggle .ref-title {
              position: absolute;
              top: 2px;
              left: 0;
              width: auto;
              padding-right: 130px;
            }

            .ref-link {
              display: inline-flex;
              align-items: center;
              max-width: 100%;
              padding: 3px 8px;
              border-radius: 999px;
              border: 1px solid var(--v2-border-soft);
              background: var(--v2-surface-soft);
              color: var(--v2-text-body);
              font-size: 11px;
              line-height: 1.2;
              text-decoration: none;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
              transition: all 0.2s ease;
            }

            .ref-link:hover {
              color: var(--v2-gold);
              border-color: var(--v2-chip-border);
              background: var(--v2-chip-bg);
            }

            .ref-text {
              white-space: normal;
              line-height: 1.4;
            }
          }

          .message-feedback {
            display: flex;
            gap: 8px;
            margin-top: 8px;
            opacity: 1;
            transition: transform 0.22s ease;

            &:hover {
              transform: translateY(-1px);
            }
          }

          /*
            知识库来源标签。
            用暖金 + 深底，和项目整体的古建筑调性一致，
            也和大模型生成的置信度胶囊区分开——一眼能看出这是"查来的"而非"生成的"。
          */
          .message-kb-source {
            margin-top: 8px;
            width: fit-content;
            max-width: 100%;
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 5px 11px;
            border-radius: 999px;
            font-size: 11px;
            line-height: 1.35;
            border: 1px solid var(--v2-warn-border);
            background: var(--v2-warn-bg);
            color: var(--v2-warn-text);
            animation: kbSourceIn 0.45s cubic-bezier(0.16, 1, 0.3, 1);

            .kb-icon {
              font-size: 12px;
              color: var(--v2-gold);
              flex-shrink: 0;
            }

            .kb-text {
              font-weight: 600;
              white-space: nowrap;
            }

            /* 被问次数做成次要信息，不抢主标签的注意力 */
            .kb-hit {
              padding-left: 7px;
              margin-left: 1px;
              border-left: 1px solid var(--v2-warn-border);
              opacity: 0.75;
              font-weight: 400;
              white-space: nowrap;
            }
          }

          @keyframes kbSourceIn {
            from {
              opacity: 0;
              transform: translateY(4px);
            }
            to {
              opacity: 1;
              transform: translateY(0);
            }
          }

          /* 兜底卡片：说明为什么答不了 + 给出可问的方向 */
          .message-fallback {
            margin-top: 10px;
            padding: 11px 13px;
            border-radius: 12px;
            border: 1px solid var(--v2-warn-border);
            background: var(--v2-warn-bg);

            .fallback-title {
              display: flex;
              align-items: center;
              gap: 6px;
              font-size: 12px;
              font-weight: 600;
              color: var(--v2-warn-text);
              margin-bottom: 5px;

              .el-icon {
                font-size: 13px;
              }
            }

            .fallback-desc {
              font-size: 11.5px;
              line-height: 1.6;
              color: var(--v2-text-body);
            }

            .fallback-suggest {
              margin-top: 9px;
              display: flex;
              flex-wrap: wrap;
              align-items: center;
              gap: 6px;

              .suggest-label {
                font-size: 11px;
                color: var(--v2-text-weak);
              }

              .suggest-item {
                padding: 4px 10px;
                border-radius: 999px;
                border: 1px solid var(--v2-chip-border);
                background: var(--v2-surface-strong);
                color: var(--v2-chip-text);
                font-size: 11px;
                cursor: pointer;
                line-height: 1.4;
                transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);

                &:hover {
                  border-color: var(--v2-gold);
                  background: color-mix(in srgb, var(--v2-gold) 16%, transparent);
                  transform: translateY(-1px);
                }
              }
            }
          }

          .message-confidence {
            margin-top: 8px;
            width: fit-content;
            padding: 4px 10px;
            border-radius: 999px;
            font-size: 11px;
            line-height: 1.35;
            border: 1px solid var(--v2-info-border);
            background: var(--v2-info-bg);
            color: var(--v2-info-text);
          }

          .message-confidence.level-high {
            border-color: var(--v2-ok-border);
            background: var(--v2-ok-bg);
            color: var(--v2-ok-text);
          }

          .message-confidence.level-medium {
            border-color: var(--v2-warn-border);
            background: var(--v2-warn-bg);
            color: var(--v2-warn-text);
          }

          .message-confidence.level-low {
            border-color: var(--v2-danger-border);
            background: var(--v2-danger-bg);
            color: var(--v2-danger-text);
          }

          .message-evidence-warning {
            margin-top: 8px;
            width: fit-content;
            max-width: 100%;
            padding: 5px 10px;
            border-radius: 10px;
            border: 1px solid var(--v2-danger-border);
            background: var(--v2-danger-bg);
            color: var(--v2-danger-text);
            font-size: 11px;
            line-height: 1.4;
          }

          .message-no-references {
            margin-top: 8px;
            width: fit-content;
            max-width: 100%;
            padding: 4px 10px;
            border-radius: 999px;
            border: 1px solid var(--v2-info-border);
            background: var(--v2-info-bg);
            color: var(--v2-info-text);
            font-size: 11px;
            line-height: 1.35;
          }

          .message-time {
            font-size: 11px;
            color: var(--v2-text-weak);
            margin-top: 5px;
            padding: 2px 8px;
            border-radius: 999px;
            background: var(--v2-surface-soft);
            border: 1px solid var(--v2-border-soft);
            line-height: 1.4;
          }
        }
      }
    }

    .chat-input {
      padding: 14px;
      border-top: 1px solid var(--v2-chat-border);
      background: var(--v2-chat-input-bg);

      .el-input {
        --el-input-focus-border-color: var(--v2-gold);
      }
    }
  }
}

@media (max-width: 768px) {
  .chat-float-container {
    right: 12px;
    bottom: calc(12px + env(safe-area-inset-bottom, 0px));

    .chat-window {
      right: 0;
      bottom: 62px;
      width: min(380px, calc(100vw - 24px));
      height: min(520px, 70vh);
      max-height: calc(100vh - 140px);
      border-radius: 14px;
    }
  }
}

.chat-float-container .chat-window :deep(.el-tag.preset-tag) {
  color: var(--v2-chat-preset-text) !important;
  background: var(--v2-chat-preset-bg) !important;
  border-color: var(--v2-chat-preset-border) !important;
  font-weight: 500;
  transition: all 0.2s ease;
}

.chat-float-container .chat-window :deep(.el-tag.preset-tag:hover) {
  color: var(--v2-text-strong) !important;
  border-color: var(--v2-gold) !important;
  background: var(--v2-chat-preset-hover) !important;
}

.chat-float-container .chat-window :deep(.el-input__wrapper) {
  background: var(--v2-chat-input-bg) !important;
  box-shadow: 0 0 0 1px var(--v2-chat-border) inset !important;
  border-radius: 12px !important;
}

.chat-float-container .chat-window :deep(.el-input__inner) {
  color: var(--v2-text-strong) !important;
}

.chat-float-container .chat-window :deep(.el-input__inner::placeholder) {
  color: var(--v2-text-weak) !important;
}

.chat-float-container .chat-window :deep(.el-input-group__append .el-button) {
  color: var(--v2-chip-text) !important;
  background: var(--v2-chip-bg) !important;
  border-color: var(--v2-chip-border) !important;
  border-radius: 10px !important;
  box-shadow: none !important;
}

.chat-float-container .chat-window :deep(.el-input-group__append .el-button:hover) {
  color: var(--v2-text-strong) !important;
  background: color-mix(in srgb, var(--v2-gold) 24%, transparent) !important;
}

.chat-float-container .chat-window :deep(.chat-header .el-button) {
  color: var(--v2-chat-header-text) !important;
  border-color: var(--v2-chat-border) !important;
  background: var(--v2-surface-soft) !important;
  box-shadow: none !important;
}

.chat-float-container .chat-window :deep(.chat-header .el-button:hover) {
  color: var(--v2-text-strong) !important;
  background: color-mix(in srgb, var(--v2-gold) 22%, transparent) !important;
}

.chat-float-container .chat-window :deep(.message-feedback .el-button) {
  border-radius: 10px !important;
  border: 1px solid var(--v2-chat-border) !important;
  color: var(--v2-text-body) !important;
  background: var(--v2-chat-ai-bg) !important;
  box-shadow: none !important;
}

.chat-float-container .chat-window :deep(.message-feedback .el-button:hover) {
  color: var(--v2-text-strong) !important;
  border-color: var(--v2-gold) !important;
  background: color-mix(in srgb, var(--v2-gold) 18%, transparent) !important;
}

.chat-float-container .chat-window :deep(.ai-message a) {
  color: var(--v2-gold);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.chat-float-container .chat-window :deep(.ai-message a:hover) {
  color: color-mix(in srgb, var(--v2-gold) 78%, #fff);
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-4px);
  }
}

@keyframes welcome-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes stream-caret {
  0%, 45% {
    opacity: 0;
  }
  46%, 100% {
    opacity: 1;
  }
}

@keyframes ref-toggle-glow {
  0%, 100% {
    box-shadow: 0 4px 10px color-mix(in srgb, var(--v2-gold) 14%, transparent);
  }
  50% {
    box-shadow: 0 6px 16px color-mix(in srgb, var(--v2-gold) 26%, transparent);
  }
}
</style>
