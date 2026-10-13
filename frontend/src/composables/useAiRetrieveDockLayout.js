import { ref, watch, nextTick } from 'vue'

/**
 * AI 检索/生成进度条跟随构件说明浮窗：
 * 默认叠在浮窗上方；当上方空间不足时自动切换到下方。
 */
export function useAiRetrieveDockLayout({ visible, wrapRef, floatCardRef, gap = 10 }) {
  const style = ref({})

  const sync = () => {
    if (!visible.value) {
      style.value = {}
      return
    }
    const wrapEl = wrapRef.value
    const cardEl = floatCardRef.value
    if (!wrapEl || !cardEl) {
      style.value = {}
      return
    }
    const wr = wrapEl.getBoundingClientRect()
    const cr = cardEl.getBoundingClientRect()
    if (cr.width < 2 || cr.height < 2) {
      style.value = {}
      return
    }

    // 进度条浮层的“估算高度”，用于在 DOM 未渲染完全时提前算位，避免抖动
    const estDockH = 92
    let top = cr.top - wr.top - gap - estDockH
    if (top < 8) {
      top = cr.bottom - wr.top + gap
    }
    const cx = cr.left + cr.width / 2 - wr.left

    style.value = {
      left: `${Math.round(cx)}px`,
      top: `${Math.round(top)}px`,
      transform: 'translateX(-50%)',
      zIndex: 1260
    }
  }

  watch(visible, (v) => {
    if (v) {
      nextTick(() => {
        requestAnimationFrame(() => {
          sync()
          requestAnimationFrame(sync)
        })
      })
    } else {
      style.value = {}
    }
  })

  return { aiRetrieveDockStyle: style, syncAiRetrieveDockLayout: sync }
}
