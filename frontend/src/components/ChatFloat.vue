<template>
  <div class="chat-float-container" :class="{ expanded: isExpanded }">
    <div class="chat-toggle" @click="toggleChat">
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="9">
        <el-icon v-if="!isExpanded"><ChatRound /></el-icon>
        <el-icon v-else><Close /></el-icon>
      </el-badge>
    </div>

    <div class="chat-window" v-show="isExpanded">
      <div class="chat-header">
        <h3>智能知识问答</h3>
        <el-button text @click="clearChat">
          <el-icon><Delete /></el-icon>
        </el-button>
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
            <div
              class="message-confidence"
              :class="`level-${msg.confidence || 'medium'}`"
              v-if="msg.type === 'ai' && msg.isComplete"
            >
              置信度：{{ getConfidenceLabel(msg.confidence) }}
            </div>
            <div
              class="message-evidence-warning"
              v-if="msg.type === 'ai' && msg.isComplete && msg.noEvidence === true"
            >
              暂未检测到可验证依据，请谨慎采纳结论。
            </div>
            <div
              class="message-no-references"
              v-if="msg.type === 'ai' && msg.isComplete && msg.noEvidence !== true && getMessageReferences(msg).length === 0 && getMessageEvidences(msg).length === 0"
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
import { ChatRound, Close, Delete, Top, Bottom, Promotion } from '@element-plus/icons-vue'
import { getPresetQuestions, streamChat } from '@/api/chat'
import { useUiPreferences } from '@/composables/useUiPreferences'

const props = defineProps({
  modelId: {
    type: Number,
    default: null
  }
})

const authStore = useAuthStore()
const { t } = useUiPreferences()

const isExpanded = ref(false)
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
    const response = await getPresetQuestions(props.modelId)
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

  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再使用问答')
    return
  }

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
    isComplete: false
  })

  try {
    const lastMessage = messages.value[messages.value.length - 1]
    await streamChat({
      question: guardedQuestion,
      modelId: props.modelId,
      sessionId: sessionId.value,
      // record=true：允许后端把本次问答写入个人中心历史（具体由后端再做条件判断）
      record: true,
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

const formatMessage = (content) => {
  if (!content) return ''
  const markdownLinked = content.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
  )
  const linked = markdownLinked.replace(
    /(^|[\s(])(https?:\/\/[^\s<]+)/g,
    '$1<a href="$2" target="_blank" rel="noopener noreferrer">$2</a>'
  )
  return linked.replace(/\n/g, '<br>')
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
</script>

<style scoped>
.chat-float-container {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 1000;

  .chat-toggle {
    width: 50px;
    height: 50px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
    transition: transform 0.3s;

    &:hover {
      transform: scale(1.1);
    }

    .el-icon {
      font-size: 24px;
      color: white;
    }
  }

  .chat-window {
    position: absolute;
    bottom: 70px;
    right: 0;
    width: 380px;
    height: 520px;
    background: linear-gradient(180deg, #fbfcff 0%, #f4f6fd 100%);
    border-radius: 16px;
    border: 1px solid rgba(183, 195, 226, 0.58);
    box-shadow:
      0 18px 48px rgba(17, 24, 39, 0.24),
      0 4px 14px rgba(97, 112, 172, 0.12);
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .chat-header {
      padding: 14px 18px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(255, 255, 255, 0.2);

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        letter-spacing: 0.3px;
      }
    }

    .preset-questions {
      padding: 10px 15px;
      background: #f3f5fb;
      border-bottom: 1px solid #eee;

      .preset-title {
        font-size: 12px;
        color: #6b7280;
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
      background:
        radial-gradient(circle at 50% -10%, rgba(129, 140, 248, 0.12), transparent 40%),
        linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(246, 248, 255, 0.92));

      &::-webkit-scrollbar {
        width: 8px;
      }

      &::-webkit-scrollbar-track {
        background: transparent;
      }

      &::-webkit-scrollbar-thumb {
        border-radius: 999px;
        background: linear-gradient(180deg, rgba(99, 121, 221, 0.5), rgba(138, 108, 205, 0.5));
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
            background: linear-gradient(135deg, #5c74e5 0%, #6f5ec7 100%);
            color: white;
            border-top-right-radius: 6px;
            box-shadow: 0 8px 18px rgba(82, 102, 197, 0.34);
          }
        }

        &.ai {
          .message-content {
            align-items: flex-start;
            margin-left: 10px;
          }

          .message-text {
            background: rgba(255, 255, 255, 0.95);
            color: #1f2937;
            border-top-left-radius: 6px;
            border: 1px solid rgba(207, 216, 238, 0.92);
            box-shadow: 0 6px 16px rgba(22, 32, 66, 0.08);
          }
        }

        .message-avatar {
          flex-shrink: 0;

          :deep(.el-avatar) {
            border: 2px solid rgba(255, 255, 255, 0.85);
            box-shadow: 0 6px 16px rgba(18, 31, 69, 0.22);
            color: #ffffff;
            font-weight: 700;
            background: linear-gradient(140deg, #677eea 0%, #7b56be 100%);
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
            white-space: pre-wrap;

            &.typing {
              display: flex;
              gap: 4px;
              padding: 12px 16px;

              .typing-dot {
                width: 6px;
                height: 6px;
                background: #999;
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
            background: linear-gradient(180deg, rgba(95, 116, 225, 0.9), rgba(128, 93, 202, 0.9));
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
              color: #64748b;
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
              border: 1px solid rgba(141, 163, 222, 0.95) !important;
              background: linear-gradient(180deg, rgba(237, 243, 255, 0.96), rgba(226, 236, 255, 0.96)) !important;
              color: #3a528f !important;
              font-weight: 600;
              box-shadow: 0 4px 10px rgba(72, 100, 181, 0.14) !important;
              transition: all 0.2s ease;
              animation: ref-toggle-glow 1.8s ease-in-out infinite;
            }

            .ref-toggle-btn:hover {
              color: #203f84 !important;
              border-color: rgba(105, 134, 214, 0.98) !important;
              background: linear-gradient(180deg, rgba(227, 237, 255, 1), rgba(212, 227, 255, 1)) !important;
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
              border: 1px solid rgba(166, 185, 230, 0.85);
              background: rgba(236, 242, 255, 0.85);
              color: #36508d;
              font-size: 11px;
              line-height: 1.2;
              text-decoration: none;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
              transition: all 0.2s ease;
            }

            .ref-link:hover {
              color: #1f3a77;
              border-color: rgba(112, 143, 223, 0.95);
              background: rgba(224, 234, 255, 0.95);
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

          .message-confidence {
            margin-top: 8px;
            width: fit-content;
            padding: 4px 10px;
            border-radius: 999px;
            font-size: 11px;
            line-height: 1.35;
            border: 1px solid rgba(180, 191, 216, 0.9);
            background: rgba(237, 241, 249, 0.92);
            color: #55657f;
          }

          .message-confidence.level-high {
            border-color: rgba(108, 194, 128, 0.9);
            background: rgba(226, 248, 233, 0.95);
            color: #2f7a44;
          }

          .message-confidence.level-medium {
            border-color: rgba(226, 190, 106, 0.92);
            background: rgba(255, 247, 228, 0.95);
            color: #9b6b18;
          }

          .message-confidence.level-low {
            border-color: rgba(234, 136, 136, 0.92);
            background: rgba(255, 237, 237, 0.95);
            color: #a43b3b;
          }

          .message-evidence-warning {
            margin-top: 8px;
            width: fit-content;
            max-width: 100%;
            padding: 5px 10px;
            border-radius: 10px;
            border: 1px solid rgba(225, 124, 124, 0.86);
            background: rgba(255, 239, 239, 0.94);
            color: #9f2f2f;
            font-size: 11px;
            line-height: 1.4;
          }

          .message-no-references {
            margin-top: 8px;
            width: fit-content;
            max-width: 100%;
            padding: 4px 10px;
            border-radius: 999px;
            border: 1px solid rgba(198, 206, 221, 0.88);
            background: rgba(238, 242, 248, 0.92);
            color: #64748b;
            font-size: 11px;
            line-height: 1.35;
          }

          .message-time {
            font-size: 11px;
            color: #6b7280;
            margin-top: 5px;
            padding: 2px 8px;
            border-radius: 999px;
            background: rgba(255, 255, 255, 0.78);
            border: 1px solid rgba(205, 214, 234, 0.82);
            line-height: 1.4;
          }
        }
      }
    }

    .chat-input {
      padding: 14px;
      border-top: 1px solid #e3e8f4;
      background: rgba(251, 252, 255, 0.95);

      .el-input {
        --el-input-focus-border-color: #667eea;
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
  color: #374151 !important;
  background: #ffffff !important;
  border-color: #d7deec !important;
  font-weight: 500;
  transition: all 0.2s ease;
}

.chat-float-container .chat-window :deep(.el-tag.preset-tag:hover) {
  color: #2d3a63 !important;
  border-color: #aebce7 !important;
  background: #f4f7ff !important;
}

.chat-float-container .chat-window :deep(.el-input__wrapper) {
  background: #ffffff !important;
  box-shadow: 0 0 0 1px #d6def0 inset !important;
  border-radius: 12px !important;
}

.chat-float-container .chat-window :deep(.el-input__inner) {
  color: #111827 !important;
}

.chat-float-container .chat-window :deep(.el-input__inner::placeholder) {
  color: #9ca3af !important;
}

.chat-float-container .chat-window :deep(.el-input-group__append .el-button) {
  color: #4b5563 !important;
  background: #eef2ff !important;
  border-color: #d7deec !important;
  border-radius: 10px !important;
  box-shadow: 0 4px 12px rgba(99, 121, 221, 0.12) !important;
}

.chat-float-container .chat-window :deep(.el-input-group__append .el-button:hover) {
  color: #2d3a63 !important;
  background: #e3e9ff !important;
}

.chat-float-container .chat-window :deep(.chat-header .el-button) {
  color: rgba(255, 255, 255, 0.9) !important;
  border-color: rgba(255, 255, 255, 0.26) !important;
  background: rgba(255, 255, 255, 0.12) !important;
  box-shadow: none !important;
}

.chat-float-container .chat-window :deep(.chat-header .el-button:hover) {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.2) !important;
}

.chat-float-container .chat-window :deep(.message-feedback .el-button) {
  border-radius: 10px !important;
  border: 1px solid rgba(194, 206, 234, 0.92) !important;
  color: #4b5563 !important;
  background: rgba(255, 255, 255, 0.92) !important;
  box-shadow: 0 4px 10px rgba(17, 24, 39, 0.08) !important;
}

.chat-float-container .chat-window :deep(.message-feedback .el-button:hover) {
  color: #2f3f74 !important;
  border-color: rgba(141, 163, 222, 0.95) !important;
  background: #f4f7ff !important;
}

.chat-float-container .chat-window :deep(.ai-message a) {
  color: #3557a8;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.chat-float-container .chat-window :deep(.ai-message a:hover) {
  color: #1f3f8a;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-4px);
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
    box-shadow: 0 4px 10px rgba(72, 100, 181, 0.14);
  }
  50% {
    box-shadow: 0 6px 16px rgba(72, 100, 181, 0.28);
  }
}
</style>
