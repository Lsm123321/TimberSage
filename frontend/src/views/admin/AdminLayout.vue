<template>
  <div class="admin-layout">
    <aside class="admin-sidebar v2-glass">
      <div class="logo">
        <div class="logo-mark" aria-hidden="true" />
        <div class="logo-text">
          <div class="title">{{ t('admin') }}</div>
          <div class="subtitle">{{ t('adminConsole') }}</div>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item index="/admin/dashboard"><el-icon><Odometer /></el-icon><span>{{ t('dashboard') }}</span></el-menu-item>
        <el-menu-item index="/admin/models"><el-icon><Box /></el-icon><span>{{ t('modelManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/users"><el-icon><User /></el-icon><span>{{ t('userManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/role-upgrade"><el-icon><TrendCharts /></el-icon><span>{{ t('roleUpgradeManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/feedback"><el-icon><ChatDotRound /></el-icon><span>{{ t('feedbackManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/site-feedback"><el-icon><ChatSquare /></el-icon><span>{{ t('siteFeedbackManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/comments"><el-icon><ChatLineSquare /></el-icon><span>{{ t('commentManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/questions"><el-icon><QuestionFilled /></el-icon><span>{{ t('questionManage') }}</span></el-menu-item>
        <el-menu-item index="/admin/statistics"><el-icon><DataLine /></el-icon><span>{{ t('statistics') }}</span></el-menu-item>
        <el-menu-item index="/admin/voices"><el-icon><Microphone /></el-icon><span>音色管理</span></el-menu-item>
      </el-menu>
    </aside>

    <section class="admin-main">
      <header class="admin-header v2-glass-strong">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">{{ t('home') }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ t('admin') }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="user-info">
          <el-dropdown popper-class="v2-user-dropdown" @command="handleCommand">
            <span class="user-pill v2-glass">
              <span class="username">{{ userInfo?.username || t('adminFallback') }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">{{ t('profile') }}</el-dropdown-item>
                <el-dropdown-item command="logout">{{ t('logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="admin-content">
        <router-view />
      </main>
    </section>
    <FloatingUiControls />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import FloatingUiControls from '@/components/FloatingUiControls.vue'
import { useUiPreferences } from '@/composables/useUiPreferences'
import {
  Odometer,
  Box,
  User,
  ChatDotRound,
  ChatSquare,
  ChatLineSquare,
  QuestionFilled,
  DataLine,
  TrendCharts,
  ArrowDown,
  Microphone
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t, initializePreferences } = useUiPreferences()

const userInfo = computed(() => authStore.userInfo)

const activeMenu = computed(() => route.path)

const currentPageTitle = computed(() => {
  if (route.meta?.titleKey) return t(route.meta.titleKey)
  return ''
})

const handleCommand = (command) => {
  if (command === 'logout') {
    authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

onMounted(() => {
  initializePreferences()
})

</script>

<style lang="scss" scoped>
.admin-layout {
  height: 100vh;
  display: flex;
  gap: 12px;
  padding: 12px;
  position: relative;
  isolation: isolate;

  &::before,
  &::after {
    content: '';
    position: fixed;
    width: 360px;
    height: 360px;
    border-radius: 999px;
    pointer-events: none;
    filter: blur(50px);
    opacity: 0.28;
    z-index: -1;
    animation: admin-aura 14s ease-in-out infinite alternate;
  }

  &::before {
    top: -120px;
    right: -80px;
    background: radial-gradient(circle, rgba(98, 121, 255, 0.48), rgba(98, 121, 255, 0));
  }

  &::after {
    left: -120px;
    bottom: -120px;
    background: radial-gradient(circle, rgba(77, 216, 255, 0.42), rgba(77, 216, 255, 0));
    animation-duration: 18s;
  }

  .admin-sidebar {
    width: 260px;
    padding: 14px 12px;
    display: flex;
    flex-direction: column;
    border-radius: var(--v2-radius-xl);
    animation: admin-slide-in 380ms var(--v2-ease);

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px 14px;

      .logo-mark {
        width: 38px;
        height: 38px;
        border-radius: 14px;
        background-image: url('@/assets/images/logo.png');
        background-size: cover;
        background-position: center;
        background-repeat: no-repeat;
        box-shadow: 0 8px 18px rgba(214, 183, 122, 0.2);
      }

      .title {
        color: var(--v2-text);
        font-weight: 700;
      }

      .subtitle {
        color: var(--v2-muted);
        font-size: 12px;
      }
    }

    .admin-menu {
      background: transparent;
      border-right: none;

      :deep(.el-menu-item) {
        margin: 8px 0;
        border-radius: 14px;
        color: var(--v2-text-dim);
        transition: transform 220ms ease, background 220ms ease, color 220ms ease;
        position: relative;
        overflow: hidden;
      }

      :deep(.el-menu-item)::after {
        content: '';
        position: absolute;
        left: -120%;
        top: -20%;
        width: 38%;
        height: 140%;
        transform: rotate(14deg);
        background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
        opacity: 0;
      }

      :deep(.el-menu-item:hover) {
        background: rgba(255, 255, 255, 0.05);
        color: var(--v2-text);
        transform: translate3d(2px, -1px, 0);
      }

      :deep(.el-menu-item:hover)::after {
        opacity: 1;
        animation: admin-nav-sweep 760ms ease;
      }

      :deep(.el-menu-item.is-active) {
        color: #fff1d8;
        background: linear-gradient(90deg, rgba(202, 163, 106, 0.24), rgba(143, 59, 47, 0.2));
        box-shadow: inset 0 0 0 1px rgba(202, 163, 106, 0.22);
      }
    }
  }

  .admin-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .admin-header {
    height: 72px;
    padding: 0 18px;
    border-radius: var(--v2-radius-xl);
    display: flex;
    align-items: center;
    justify-content: space-between;
    animation: admin-fade-down 360ms var(--v2-ease);

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;

      .user-pill {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 12px;
        border-radius: 999px;
        color: rgba(241, 245, 249, 0.96);
        background: rgba(22, 28, 40, 0.52);
        border: 1px solid rgba(255, 255, 255, 0.12);
        box-shadow: 0 10px 28px rgba(0, 0, 0, 0.22);
        cursor: pointer;
        transition: transform 220ms var(--v2-ease), background 220ms var(--v2-ease), border-color 220ms var(--v2-ease);

        &:hover {
          transform: translate3d(0, -1px, 0);
          background: rgba(22, 28, 40, 0.66);
          border-color: rgba(255, 255, 255, 0.16);
        }

        :deep(.el-icon) {
          color: rgba(241, 245, 249, 0.78);
        }
      }

      .username {
        color: rgba(241, 245, 249, 0.96);
        font-weight: 650;
        letter-spacing: 0.2px;
        text-shadow: 0 2px 10px rgba(0, 0, 0, 0.28);
      }
    }
  }

  .admin-content {
    flex: 1;
    overflow: auto;
    padding: 8px;
    animation: admin-fade-up 440ms var(--v2-ease);
  }
}

@keyframes admin-aura {
  0% { transform: translate3d(0, 0, 0) scale(1); }
  100% { transform: translate3d(0, -18px, 0) scale(1.08); }
}

@keyframes admin-slide-in {
  0% {
    opacity: 0;
    transform: translate3d(-12px, 0, 0);
  }
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes admin-fade-down {
  0% {
    opacity: 0;
    transform: translate3d(0, -10px, 0);
  }
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes admin-fade-up {
  0% {
    opacity: 0;
    transform: translate3d(0, 12px, 0);
  }
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes admin-nav-sweep {
  0% { left: -120%; }
  100% { left: 160%; }
}

@media (prefers-reduced-motion: reduce) {
  .admin-layout::before,
  .admin-layout::after,
  .admin-layout .admin-sidebar,
  .admin-layout .admin-header,
  .admin-layout .admin-content {
    animation: none !important;
  }
}
</style>

<style lang="scss">
.v2-user-dropdown {
  border: 1px solid rgba(255, 255, 255, 0.16) !important;
  background: rgba(22, 28, 40, 0.88) !important;
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.42) !important;
  backdrop-filter: blur(12px) saturate(1.2);
  -webkit-backdrop-filter: blur(12px) saturate(1.2);

  --el-dropdown-menu-box-shadow: none;
  --el-dropdown-menuItem-hover-fill: rgba(255, 255, 255, 0.1);
  --el-dropdown-menuItem-hover-color: #ffffff;
  --el-dropdown-menuItem-divided-color: rgba(255, 255, 255, 0.16);
  --el-text-color-primary: rgba(241, 245, 249, 0.94);
}

.v2-user-dropdown .el-dropdown-menu {
  background: transparent !important;
  padding: 8px 6px !important;
}

.v2-user-dropdown .el-dropdown-menu__item {
  color: rgba(241, 245, 249, 0.94) !important;
  font-weight: 650;
}

.v2-user-dropdown .el-dropdown-menu__item:hover {
  background: rgba(255, 255, 255, 0.1) !important;
  color: #ffffff !important;
}

.v2-user-dropdown .el-dropdown-menu__item--divided::before {
  background-color: rgba(255, 255, 255, 0.16) !important;
}
</style>
