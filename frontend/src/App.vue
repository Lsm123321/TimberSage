<template>
  <el-config-provider :locale="elementLocale">
    <div id="app">
      <div class="immersive-bg" aria-hidden="true">
        <span class="orb orb-a" />
        <span class="orb orb-b" />
        <span class="orb orb-c" />
        <span class="grid-layer" />
        <span class="noise-layer" />
      </div>
      <div class="pointer-glow" aria-hidden="true" />
      <router-view v-slot="{ Component, route }">
        <Transition name="global-page" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </Transition>
      </router-view>
      <!-- 全局登录引导：未登录用户点写操作时统一弹出 -->
      <LoginGuideDialog />
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { useAuthStore } from '@/stores/auth'
import { useUiPreferences } from '@/composables/useUiPreferences'
import LoginGuideDialog from '@/components/LoginGuideDialog.vue'

const authStore = useAuthStore()
const { language, initializePreferences } = useUiPreferences()
initializePreferences()

const elementLocale = computed(() => (language.value === 'en' ? en : zhCn))
let removePointerListener = null

onMounted(() => {
  if (authStore.token) {
    authStore.fetchUserInfo()
  }
  const glowEl = document.querySelector('.pointer-glow')
  if (!glowEl) return
  const handlePointerMove = (event) => {
    glowEl.style.setProperty('--x', `${event.clientX}px`)
    glowEl.style.setProperty('--y', `${event.clientY}px`)
    glowEl.style.setProperty('--opacity', '1')
  }
  const handlePointerLeave = () => {
    glowEl.style.setProperty('--opacity', '0')
  }
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
  window.addEventListener('pointerleave', handlePointerLeave, { passive: true })
  removePointerListener = () => {
    window.removeEventListener('pointermove', handlePointerMove)
    window.removeEventListener('pointerleave', handlePointerLeave)
  }
})

onUnmounted(() => {
  if (removePointerListener) {
    removePointerListener()
    removePointerListener = null
  }
})
</script>

<style scoped>
#app {
  position: relative;
  width: 100%;
  min-height: 100%;
  isolation: isolate;
}

.immersive-bg {
  position: fixed;
  inset: -24%;
  z-index: -3;
  pointer-events: none;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(64px);
  opacity: 0.36;
  animation: orbDrift 18s ease-in-out infinite alternate;
}

.orb-a {
  width: 42vw;
  height: 42vw;
  left: 4%;
  top: 10%;
  background: radial-gradient(circle, rgba(201, 169, 97, 0.40) 0%, rgba(201, 169, 97, 0) 68%);
}

.orb-b {
  width: 38vw;
  height: 38vw;
  right: 6%;
  top: 2%;
  animation-duration: 24s;
  background: radial-gradient(circle, rgba(124, 156, 146, 0.34) 0%, rgba(124, 156, 146, 0) 70%);
}

.orb-c {
  width: 50vw;
  height: 50vw;
  left: 24%;
  bottom: -10%;
  animation-duration: 28s;
  background: radial-gradient(circle, rgba(178, 67, 47, 0.24) 0%, rgba(178, 67, 47, 0) 72%);
}

.grid-layer {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(255, 255, 255, 0.018), rgba(255, 255, 255, 0.018)),
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent 58px,
      rgba(255, 255, 255, 0.018) 58px,
      rgba(255, 255, 255, 0.018) 59px
    ),
    repeating-linear-gradient(
      0deg,
      transparent 0,
      transparent 58px,
      rgba(255, 255, 255, 0.016) 58px,
      rgba(255, 255, 255, 0.016) 59px
    );
  opacity: 0.38;
}

.noise-layer {
  position: absolute;
  inset: 0;
  opacity: 0.12;
  mix-blend-mode: overlay;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.02), rgba(255, 255, 255, 0.02)),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='.38'/%3E%3C/svg%3E");
}

.pointer-glow {
  --x: 50vw;
  --y: 50vh;
  --opacity: 0;
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: -1;
  opacity: var(--opacity);
  transition: opacity 280ms ease;
  background:
    radial-gradient(340px 340px at var(--x) var(--y), rgba(201, 169, 97, 0.16), transparent 68%),
    radial-gradient(260px 260px at calc(var(--x) + 40px) calc(var(--y) - 20px), rgba(124, 156, 146, 0.13), transparent 70%);
}

.global-page-enter-active {
  animation: globalPageIn 420ms cubic-bezier(.2,.9,.2,1);
}

.global-page-leave-active {
  animation: globalPageOut 260ms cubic-bezier(.2,.9,.2,1);
}

@keyframes globalPageIn {
  0% { opacity: 0; transform: translate3d(0, 10px, 0) scale(0.996); }
  100% { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
}

@keyframes globalPageOut {
  0% { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
  100% { opacity: 0; transform: translate3d(0, 8px, 0) scale(0.997); }
}

@keyframes orbDrift {
  0% { transform: translate3d(-1%, -1%, 0) scale(1); }
  100% { transform: translate3d(1.8%, 1.2%, 0) scale(1.08); }
}

@media (prefers-reduced-motion: reduce) {
  .orb,
  .global-page-enter-active,
  .global-page-leave-active {
    animation: none !important;
  }

  .pointer-glow {
    display: none;
  }
}
</style>
