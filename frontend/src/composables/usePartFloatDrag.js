import { ref } from 'vue'

/**
 * 构件浮窗拖拽：偏移叠加在锚点定位之上；mousemove 时调用 requestSync 立刻刷新位置。
 */
export function usePartFloatDrag(requestSync) {
  const dragOffset = ref({ x: 0, y: 0 })
  const dragging = ref(false)

  const resetDragOffset = () => {
    dragOffset.value = { x: 0, y: 0 }
  }

  const onDragMouseDown = (e) => {
    if (e.button !== 0) return
    dragging.value = true
    const sx = e.clientX
    const sy = e.clientY
    const ox = dragOffset.value.x
    const oy = dragOffset.value.y

    const onMove = (ev) => {
      dragOffset.value = {
        x: ox + ev.clientX - sx,
        y: oy + ev.clientY - sy
      }
      requestSync()
    }

    const onUp = () => {
      dragging.value = false
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
      window.removeEventListener('blur', onUp)
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    window.addEventListener('blur', onUp)
  }

  return { dragOffset, dragging, resetDragOffset, onDragMouseDown }
}
