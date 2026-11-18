<template>
  <!--
    界面设置抽屉。

    交互设计：
      · 鼠标移入 → 抽屉平滑滑出（像拉开一条卷轴）
      · 移出或 5 秒无操作 → 自动收回
      · 左侧的箭头既是"提示这里能展开"，也可以直接点击切换
      · 触屏设备没有 hover，靠点击箭头开合，同样 5 秒后自动收回

    为什么默认收起：原来三个按钮常驻，在手机上会盖住地图和表单。
    收起来只留一条窄拉手，占用从 60×150 降到 40×96。
  -->
  <div
    class="ui-drawer"
    :class="{ 'is-open': isOpen }"
    @mouseenter="handleEnter"
    @mouseleave="handleLeave"
  >
    <!-- 抽屉本体：外层负责宽度裁剪，内层固定宽度，这样滑出时文字不会被挤压变形 -->
    <div class="drawer-clip" :class="{ 'is-open': isOpen }">
      <div class="drawer-panel">
        <div class="panel-head">
          <span class="head-mark" aria-hidden="true" />
          <span class="head-title">界面</span>
        </div>

        <button class="panel-row" type="button" @click="handleLanguage">
          <span class="row-chip">{{ language === 'zh' ? 'EN' : '中' }}</span>
          <span class="row-label">{{ language === 'zh' ? 'English' : '中文' }}</span>
        </button>

        <button class="panel-row" type="button" @click="handleTheme">
          <span class="row-chip">{{ theme === 'dark' ? '☀' : '❄' }}</span>
          <span class="row-label">{{ theme === 'dark' ? '宣纸' : '冷蓝' }}</span>
        </button>

        <button class="panel-row" type="button" @click="handleStyle">
          <span class="row-chip">{{ buttonStyleIcon }}</span>
          <span class="row-label">{{ styleLabel }}</span>
        </button>
      </div>
    </div>

    <!-- 拉手：箭头 + 竖排小字。展开时箭头朝左提示可收起 -->
    <button
      class="drawer-handle"
      type="button"
      :aria-expanded="isOpen"
      :aria-label="isOpen ? '收起界面设置' : '展开界面设置'"
      @click="toggle"
    >
      <span class="handle-arrow">{{ isOpen ? '‹' : '›' }}</span>
      <span class="handle-text" v-if="!isOpen">设置</span>
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { language, theme, buttonStyle, toggleLanguage, toggleTheme, cycleButtonStyle } = useUiPreferences()

/** 无操作多久自动收起（毫秒） */
const AUTO_COLLAPSE_MS = 5000

const isOpen = ref(false)
let collapseTimer = null

const buttonStyleIcon = computed(() => {
  if (buttonStyle.value === 'glass') return '◍'
  if (buttonStyle.value === 'neon') return '✦'
  return '●'
})

const styleLabel = computed(() => {
  if (buttonStyle.value === 'glass') return '玻璃'
  if (buttonStyle.value === 'neon') return '霓虹'
  return '圆角'
})

function clearTimer() {
  if (collapseTimer) {
    clearTimeout(collapseTimer)
    collapseTimer = null
  }
}

/** 重新开始 5 秒倒计时。每次操作后都调用，避免刚点完就被收起 */
function scheduleCollapse() {
  clearTimer()
  collapseTimer = setTimeout(() => {
    isOpen.value = false
    collapseTimer = null
  }, AUTO_COLLAPSE_MS)
}

function openDrawer() {
  isOpen.value = true
  scheduleCollapse()
}

function closeDrawer() {
  isOpen.value = false
  clearTimer()
}

function toggle() {
  if (isOpen.value) {
    closeDrawer()
  } else {
    openDrawer()
  }
}

/* 悬停展开：桌面端最主要的打开方式 */
function handleEnter() {
  clearTimer()
  isOpen.value = true
}

/* 移开不立刻收，交给 5 秒倒计时，避免鼠标轻轻一扫就闪一下 */
function handleLeave() {
  if (isOpen.value) {
    scheduleCollapse()
  }
}

/* 三个操作都重置倒计时，让用户能连着点 */
const handleLanguage = () => {
  toggleLanguage()
  scheduleCollapse()
}
const handleTheme = () => {
  toggleTheme()
  scheduleCollapse()
}
const handleStyle = () => {
  cycleButtonStyle()
  scheduleCollapse()
}

onBeforeUnmount(clearTimer)
</script>

<style scoped lang="scss">
.ui-drawer {
  /*
    定位在左下角。
    用 scroll 系列变量是为了让主题切换时这里也跟着变，
    不再写死深色/浅色——两套主题都是浅色，靠变量区分冷暖。
  */
  position: fixed;
  left: 18px;
  bottom: 18px;
  z-index: 3000;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  /* 让抽屉整体有"纸片叠在一起"的层次 */
  filter: drop-shadow(0 10px 24px rgba(74, 58, 34, 0.16));
}

/* ---------------------------------------------------------------- 抽屉本体 */

.drawer-clip {
  /*
    用 max-width 从 0 动画到目标宽度实现滑出。
    比 transform: scaleX 好，因为文字不会被横向压扁。
  */
  max-width: 0;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
  transform: translateX(-8px);
  transition:
    max-width 0.36s cubic-bezier(0.16, 1, 0.3, 1),
    opacity 0.26s ease,
    transform 0.36s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-clip.is-open {
  max-width: 180px;
  opacity: 1;
  pointer-events: auto;
  transform: translateX(0);
}

.drawer-panel {
  width: 174px;
  padding: 10px;
  border-radius: 16px;
  background: var(--v2-surface-strong, rgba(255, 253, 249, 0.97));
  border: 1px solid var(--v2-border-soft, rgba(122, 96, 58, 0.18));
  backdrop-filter: blur(14px) saturate(1.15);
  -webkit-backdrop-filter: blur(14px) saturate(1.15);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 6px 6px;
  border-bottom: 1px solid var(--v2-divider, rgba(122, 96, 58, 0.12));
  margin-bottom: 2px;
}

/* 一枚小小的朱砂点，当作"印章"式的标题装饰 */
.head-mark {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--v2-vermillion, #a8473a);
  flex-shrink: 0;
}

.head-title {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--v2-text-weak, rgba(88, 72, 48, 0.8));
}

.panel-row {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 8px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.18s ease;
}

.panel-row:hover {
  background: var(--v2-surface-hover, rgba(168, 129, 60, 0.1));
}

/* 左侧小方块：显示当前值（EN / 中 / ☀ / ❄ / ●） */
.row-chip {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  color: var(--v2-chip-text, #7a5716);
  background: var(--v2-chip-bg, rgba(168, 129, 60, 0.12));
  border: 1px solid var(--v2-chip-border, rgba(140, 105, 46, 0.35));
}

.row-label {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--v2-text-strong, #2a2419);
  white-space: nowrap;
}

/* ---------------------------------------------------------------- 拉手 */

.drawer-handle {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 40px;
  height: 88px;
  padding: 0;
  border-radius: 14px;
  cursor: pointer;
  background: var(--v2-surface-strong, rgba(255, 253, 249, 0.97));
  border: 1px solid var(--v2-border-soft, rgba(122, 96, 58, 0.18));
  backdrop-filter: blur(14px) saturate(1.15);
  -webkit-backdrop-filter: blur(14px) saturate(1.15);
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1),
              border-color 0.2s ease,
              background 0.2s ease;
}

.drawer-handle:hover {
  transform: translateY(-2px);
  border-color: var(--v2-chip-border, rgba(140, 105, 46, 0.4));
  background: var(--v2-surface-hover, rgba(168, 129, 60, 0.1));
}

.drawer-handle:active {
  transform: translateY(0) scale(0.97);
}

.handle-arrow {
  font-size: 15px;
  line-height: 1;
  color: var(--v2-gold, #a8813c);
  font-weight: 700;
}

/* 竖排"设置"两字，压缩拉手宽度 */
.handle-text {
  writing-mode: vertical-rl;
  font-size: 10.5px;
  letter-spacing: 2px;
  color: var(--v2-text-weak, rgba(88, 72, 48, 0.8));
  line-height: 1;
}

/* ---------------------------------------------------------------- 响应式 */

/* 平板：整体缩小一档，避免在 768~1024 的屏幕上显得笨重 */
@media (max-width: 1024px) {
  .ui-drawer {
    left: 12px;
    bottom: 12px;
    gap: 5px;
  }

  .drawer-panel {
    width: 164px;
    padding: 9px;
    border-radius: 14px;
  }

  .drawer-clip.is-open {
    max-width: 170px;
  }

  .drawer-handle {
    width: 36px;
    height: 78px;
    border-radius: 12px;
  }

  .panel-row {
    padding: 6px 7px;
  }

  .row-chip {
    width: 24px;
    height: 24px;
    font-size: 11px;
  }

  .row-label {
    font-size: 12px;
  }
}

/* 手机：进一步压缩，抽屉宽度再收窄，拉手更薄 */
@media (max-width: 640px) {
  .ui-drawer {
    left: 10px;
    bottom: 10px;
    gap: 4px;
    filter: drop-shadow(0 6px 16px rgba(74, 58, 34, 0.14));
  }

  .drawer-panel {
    width: 152px;
    padding: 8px;
    border-radius: 13px;
    gap: 3px;
  }

  .drawer-clip.is-open {
    max-width: 158px;
  }

  .drawer-handle {
    width: 32px;
    height: 68px;
    border-radius: 11px;
    gap: 2px;
  }

  .handle-arrow {
    font-size: 13px;
  }

  .handle-text {
    font-size: 9.5px;
    letter-spacing: 1.5px;
  }

  .panel-head {
    padding: 1px 5px 5px;
  }

  .head-title {
    font-size: 10px;
    letter-spacing: 1px;
  }

  .panel-row {
    padding: 6px;
    gap: 7px;
    border-radius: 9px;
  }

  .row-chip {
    width: 22px;
    height: 22px;
    font-size: 10.5px;
    border-radius: 7px;
  }

  .row-label {
    font-size: 11.5px;
  }
}

/* 非常矮的横屏（比如手机横放）：再压一压高度，别顶到屏幕外 */
@media (max-height: 480px) {
  .ui-drawer {
    bottom: 8px;
  }

  .drawer-handle {
    height: 56px;
  }

  .drawer-panel {
    padding: 7px;
  }

  .panel-row {
    padding: 5px 6px;
  }
}

/* 尊重系统的"减少动态效果"偏好 */
@media (prefers-reduced-motion: reduce) {
  .drawer-clip,
  .drawer-handle {
    transition: none !important;
  }
}
</style>