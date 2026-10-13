import request from '../utils/request'

// 用 fetch 手动读 SSE 流（axios 处理不了流式 body）。
// 后端接口：GET /api/chat/stream（text/event-stream），事件有四种：
// message 是回答增量文本，meta 是 JSON（引用/置信度/时间等），complete 结束，error 是错误信息
export async function streamChat({ question, modelId, sessionId, record = false, onMessage, onMeta }) {
  const token = window.localStorage.getItem('token') || ''
  const apiBase = (import.meta.env.VITE_APP_API_BASE_URL || '/api').replace(/\/$/, '')
  const params = new URLSearchParams()
  params.set('question', question)
  if (modelId !== null && modelId !== undefined && modelId !== '') params.set('modelId', String(modelId))
  if (sessionId) params.set('sessionId', String(sessionId))
  if (record) params.set('record', 'true')

  const response = await fetch(`${apiBase}/chat/stream?${params.toString()}`, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    cache: 'no-store'
  })

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('登录已过期，请重新登录')
    }
    if (response.status === 403) {
      throw new Error('没有访问权限，请确认已登录')
    }
    throw new Error(`AI请求失败(${response.status})`)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    // SSE event 以空行分隔（两个换行 \n\n）
    const parts = buffer.split(/\n\n/)
    buffer = parts.pop() || ''

    for (const part of parts) {
      const lines = part.split('\n')
      let eventName = 'message'
      const dataLines = []

      for (const line of lines) {
        if (line.startsWith('event:')) {
          // event 名称（后端 emitter.send(SseEmitter.event().name("xxx")...)）
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          // data 可能跨多行：SSE 允许多条 data: 叠加，最终以换行拼接
          dataLines.push(line.slice(5).trimStart())
        }
      }

      const data = dataLines.join('\n')
      if (!data) continue

      if (eventName === 'message') {
        onMessage?.(data)
      } else if (eventName === 'meta') {
        try {
          onMeta?.(JSON.parse(data))
        } catch {
          onMeta?.(null)
        }
      } else if (eventName === 'complete') {
        // 收到 complete 就收工，上层据此结束 loading
        return
      } else if (eventName === 'error') {
        // 后端把错误消息放在 data 里，抛出去让 UI 弹窗兜底
        throw new Error(data)
      }
    }
  }
}

export const getPresetQuestions = (modelId) => {
  const params = {}
  if (modelId !== null && modelId !== undefined && modelId !== '') {
    params.modelId = modelId
  }
  return request({
    url: '/preset-questions',
    method: 'get',
    params
  })
}

export const rateChat = (chatId, rating) => {
  return request({
    url: `/chat/feedback/${chatId}`,
    method: 'post',
    data: { helpful: rating === 1 ? true : false }
  })
}
