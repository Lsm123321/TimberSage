import { ref } from 'vue'

/** 底部进度浮窗标题（润色后的文案，避免「AI 命名」等生硬用语） */
export const PART_AI_RETRIEVE_TITLE = '正在通过 AI 自动检索该构件的称谓与关联信息…'

/**
 * 检索进度（单请求时模拟百分比，避免长时间停在 0%）
 */
export function usePartAiRetrieveDock() {
  const visible = ref(false)
  const percentage = ref(0)
  let tickTimer = null

  const stopTicker = () => {
    if (tickTimer != null) {
      clearInterval(tickTimer)
      tickTimer = null
    }
  }

  const startTicker = () => {
    stopTicker()
    tickTimer = setInterval(() => {
      percentage.value = Math.min(90, percentage.value + (6 + Math.random() * 14))
    }, 200)
  }

  /** @returns {Promise<void>} */
  const runWithProgress = async (task) => {
    visible.value = true
    percentage.value = 8
    startTicker()
    try {
      await task()
      stopTicker()
      percentage.value = 100
      await new Promise((r) => setTimeout(r, 420))
    } finally {
      stopTicker()
      visible.value = false
      percentage.value = 0
    }
  }

  return { visible, percentage, runWithProgress, stopTicker }
}
