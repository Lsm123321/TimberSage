import { defineStore } from 'pinia'
import { ref } from 'vue'
import { streamChat } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const messages = ref([])
  const isLoading = ref(false)
  const sessionId = ref('')

  async function sendMessage(question, modelId) {
    isLoading.value = true
    try {
      if (!sessionId.value) {
        sessionId.value = Date.now().toString()
      }

      // 添加用户问题
      messages.value.push({
        type: 'user',
        content: question,
        timestamp: new Date()
      })

      let aiResponse = ''
      const aiMessageIndex = messages.value.length

      messages.value.push({
        type: 'ai',
        content: '',
        timestamp: new Date()
      })

      await streamChat({
        question,
        modelId,
        sessionId: sessionId.value,
        onMessage: (chunk) => {
          aiResponse += chunk
          messages.value[aiMessageIndex].content = aiResponse
        }
      })

      return { success: true, response: aiResponse }
    } catch (error) {
      console.error('[ChatStore] 聊天请求失败', error)
      return { success: false, message: error.message || '聊天失败' }
    } finally {
      isLoading.value = false
    }
  }

  function clearMessages() {
    messages.value = []
    sessionId.value = ''
  }

  return {
    messages,
    isLoading,
    sessionId,
    sendMessage,
    clearMessages
  }
})
