<template>
  <div class="layout-v2">
    <aside class="sidebar" :class="{ 'is-collapse': isCollapse }">
      <div class="logo v2-glass">
        <div class="logo-mark" aria-hidden="true" />
        <div class="logo-text" v-if="!isCollapse">
          <div class="title">{{ t('appName') }}</div>
          <div class="subtitle">{{ t('appSubtitle') }}</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><House /></el-icon>
          <template #title>{{ t('home') }}</template>
        </el-menu-item>

        <el-menu-item index="/buildings">
          <el-icon><OfficeBuilding /></el-icon>
          <template #title>{{ t('buildings') }}</template>
        </el-menu-item>

        <el-menu-item index="/map-explorer">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>{{ t('mapExplorer') }}</template>
        </el-menu-item>

        <!--
          原来这里是「我的收藏」。收藏入口已收进个人中心的标签页，
          这个位置换成 AI 知识问答，图标用 A 开头的对话气泡以示区分。
        -->
        <el-menu-item index="/ai-chat">
          <el-icon><ChatLineSquare /></el-icon>
          <template #title>{{ t('aiChat') }}</template>
        </el-menu-item>

        <el-menu-item index="/profile" :route="{ path: '/profile' }">
          <el-icon><User /></el-icon>
          <template #title>{{ t('profile') }}</template>
        </el-menu-item>

        <el-menu-item index="/role-upgrade" v-if="showRoleUpgradeEntry">
          <el-icon><TrendCharts /></el-icon>
          <template #title>{{ t('roleUpgradeMenu') }}</template>
        </el-menu-item>

        <el-menu-item index="/about">
          <el-icon><InfoFilled /></el-icon>
          <template #title>{{ t('aboutMenu') }}</template>
        </el-menu-item>
      </el-menu>

    </aside>

    <section class="main">
      <header class="header v2-glass-strong">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>

          <div class="route-title">
            <div class="route-label">{{ currentRoute || '沉浸式展陈' }}</div>
            <div class="route-hint">{{ t('routeHint') }}</div>
          </div>
        </div>

        <div class="header-right">
          <div class="search-wrapper">
            <el-select
              v-model="searchScope"
              class="search-scope"
              :placeholder="t('searchScopePlaceholder')"
            >
              <el-option :label="t('searchScopeAll')" value="all">
                <div class="scope-option">
                  <el-icon><Grid /></el-icon>
                  <span>{{ t('searchScopeAll') }}</span>
                </div>
              </el-option>
              <el-option :label="t('searchScopeModel')" value="model">
                <div class="scope-option">
                  <el-icon><Box /></el-icon>
                  <span>{{ t('searchScopeModel') }}</span>
                </div>
              </el-option>
              <el-option :label="t('searchScopeDynasty')" value="dynasty">
                <div class="scope-option">
                  <el-icon><CollectionTag /></el-icon>
                  <span>{{ t('searchScopeDynasty') }}</span>
                </div>
              </el-option>
              <el-option :label="t('searchScopeLocation')" value="location">
                <div class="scope-option">
                  <el-icon><Location /></el-icon>
                  <span>{{ t('searchScopeLocation') }}</span>
                </div>
              </el-option>
            </el-select>
            <el-input
              ref="searchInputRef"
              v-model="searchQuery"
              :placeholder="searchInputPlaceholder"
              @input="handleSearchInput"
              @keyup.enter="handleSearchEnter"
              @focus="showSuggestions = true"
              class="search-input"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <div v-if="showSuggestions && suggestions.length > 0" class="suggestions-dropdown v2-glass-strong">
              <div
                v-for="suggestion in suggestions"
                :key="suggestion.id"
                class="suggestion-item"
                @click="selectSuggestion(suggestion)"
              >
                <div class="suggestion-dot" aria-hidden="true" />
                <div class="suggestion-info">
                  <div class="suggestion-name">{{ suggestion.modelName }}</div>
                  <div class="suggestion-meta">{{ suggestion.dynasty }}</div>
                </div>
                <div class="suggestion-arrow" aria-hidden="true">↗</div>
              </div>
            </div>
          </div>

          <el-dropdown popper-class="v2-user-dropdown" @command="handleCommand">
            <span class="user-pill v2-glass">
              <el-avatar :size="30" :src="userAvatar">
                {{ userName?.charAt(0) }}
              </el-avatar>
              <span class="username">{{ userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">{{ t('profile') }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>{{ t('logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <Transition name="v2-page" mode="out-in">
          <router-view />
        </Transition>
      </main>
    </section>
    <FloatingUiControls />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useDebounceFn } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { House, OfficeBuilding, ChatDotRound, ChatLineSquare, User, Fold, Expand, ArrowDown, Search, TrendCharts, InfoFilled, Grid, Box, CollectionTag, Location } from '@element-plus/icons-vue'
import FloatingUiControls from '@/components/FloatingUiControls.vue'
import { useUiPreferences } from '@/composables/useUiPreferences'
import { usePageSearchStore } from '@/stores/pageSearch'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const pageSearchStore = usePageSearchStore()
const searchInputRef = ref(null)
const { t, language, initializePreferences } = useUiPreferences()

const isCollapse = ref(false)
const searchQuery = ref('')
const searchScope = ref('all')
const suggestions = ref([])
const showSuggestions = ref(false)

const userName = computed(() => authStore.userInfo?.nickname || authStore.userInfo?.username || t('userFallback'))
const userAvatar = computed(() => authStore.userInfo?.avatar || '')
const currentRoute = computed(() => {
  if (route.meta?.titleKey) return t(route.meta.titleKey)
  return route.meta?.title || (language.value === 'en' ? 'Immersive Exhibition' : '沉浸式展陈')
})
const showRoleUpgradeEntry = computed(() => authStore.userInfo?.role === 'user' || authStore.userInfo?.role === 'contributor')

/**
 * 菜单高亮项。
 * /profile 被「我的收藏」和「个人中心」共用，靠 tab 参数区分：
 * 带 tab=favorites 时高亮收藏，其余情况高亮个人中心。
 * 这样两项不会同时亮起。
 */
const activeMenu = computed(() => {
  if (route.path === '/profile') {
    return route.query?.tab === 'favorites' ? '/profile?tab=favorites' : '/profile'
  }
  return route.path
})

/** 当前页是否支持「顶部搜索就地过滤」 */
const isInlineFilterPage = computed(() => pageSearchStore.isInlineFilterRoute(route.path))

const searchInputPlaceholder = computed(() => {
  // 建筑列表 / 地图导览：顶部搜索直接过滤当前页，提示要说清楚
  if (isInlineFilterPage.value) {
    if (searchScope.value === 'dynasty') return t('searchPlaceholderDynasty')
    if (searchScope.value === 'location') return t('searchPlaceholderLocation')
    return t('searchInPagePlaceholder')
  }
  if (searchScope.value === 'model') return t('searchPlaceholderModel')
  if (searchScope.value === 'dynasty') return t('searchPlaceholderDynasty')
  if (searchScope.value === 'location') return t('searchPlaceholderLocation')
  return t('searchPlaceholderAll')
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const fetchSuggestions = async (query) => {
  if (!query || query.length < 1 || searchScope.value === 'dynasty') {
    suggestions.value = []
    return
  }

  try {
    // 走统一的 request 拦截器，token 能自动带上，别用裸 fetch
    const response = await request.get('/models/search/suggest', {
      params: {
        prefix: query,
        limit: 10
      }
    })
    const payload = response?.data
    suggestions.value = Array.isArray(payload) ? payload : (payload?.data || [])
  } catch (error) {
    console.error('[LayoutV2] 获取搜索建议失败', error)
    suggestions.value = []
  }
}

const debouncedFetchSuggestions = useDebounceFn(fetchSuggestions, 250)

const handleSearchInput = () => {
  showSuggestions.value = true
  if (searchScope.value === 'dynasty') {
    suggestions.value = []
    return
  }
  debouncedFetchSuggestions(searchQuery.value)
}

const handleSearchEnter = () => {
  showSuggestions.value = false
  const normalizedQuery = searchQuery.value.trim()
  if (!normalizedQuery) {
    // 就地过滤页允许空关键词=清空筛选；其他页面照旧提示
    if (isInlineFilterPage.value) {
      pageSearchStore.reset()
      return
    }
    ElMessage.warning(t('searchEmptyTip'))
    return
  }

  // 建筑列表 / 地图导览：不再跳搜索结果页，直接驱动当前页过滤
  if (isInlineFilterPage.value) {
    pageSearchStore.submit(normalizedQuery, searchScope.value)
    return
  }

  const query = {}
  query.keyword = normalizedQuery
  query.scope = searchScope.value
  router.push({ path: '/search', query })
}

/**
 * 建议项点击。
 * 在地图导览页点建议＝在地图上定位该建筑，比跳详情更贴合当前场景；
 * 其他页面维持跳详情页。
 */
const selectSuggestion = (suggestion) => {
  showSuggestions.value = false
  searchQuery.value = suggestion.modelName

  if (isInlineFilterPage.value) {
    // 统一用「建筑名」范围过滤，保证结果聚焦到这一条
    searchScope.value = 'model'
    pageSearchStore.submit(suggestion.modelName, 'model')
    return
  }

  router.push(`/buildings/${suggestion.id}`)
}

const handleClickOutside = (event) => {
  const searchWrapper = document.querySelector('.search-wrapper')
  if (searchWrapper && !searchWrapper.contains(event.target)) {
    showSuggestions.value = false
  }
}

const handleCommand = (command) => {
  if (command === 'logout') {
    authStore.logout()
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

onMounted(() => {
  initializePreferences()
  document.addEventListener('click', handleClickOutside)
})

watch(() => route.query, (newQuery) => {
  // 搜索结果页的参数变了就同步到顶栏搜索框，显示和实际检索别对不上
  if (route.path === '/search') {
    searchQuery.value = newQuery.keyword || ''
    searchScope.value = newQuery.scope || 'all'
  }
})

// 路由变化时切换 store 的活跃页，并让顶栏输入框跟着显示该页自己的关键词。
// 各页状态相互独立，不再需要"离开就清空"——切回某页时它自己的筛选还在。
watch(() => route.path, (newPath) => {
  pageSearchStore.setActivePath(newPath)
  if (pageSearchStore.isInlineFilterRoute(newPath)) {
    searchQuery.value = pageSearchStore.keyword
    searchScope.value = pageSearchStore.scope
  }
}, { immediate: true })

/**
 * 页面内点「清除筛选」时，store 里的 keyword 会被置空。
 * 顶栏输入框得跟着清空，否则会出现"页面已恢复全部、搜索框里还留着旧词"的错位。
 */
watch(() => pageSearchStore.keyword, (val) => {
  if (!val && searchQuery.value.trim()) {
    searchQuery.value = ''
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style lang="scss" scoped>
.layout-v2 {
  display: flex;
  width: 100%;
  height: 100vh;
  /* 防止内部溢出参与文档滚动，避免出现「双滚动条」与底部空白条 */
  overflow: hidden;
  position: relative;
  isolation: isolate;
  perspective: 1400px;

  &::before,
  &::after {
    content: '';
    position: fixed;
    width: 420px;
    height: 420px;
    border-radius: 999px;
    pointer-events: none;
    filter: blur(48px);
    opacity: 0.32;
    z-index: -1;
    animation: aura-float 12s var(--v2-ease) infinite alternate;
  }

  &::before {
    top: -120px;
    right: -80px;
    background: radial-gradient(circle, rgba(201, 169, 97, 0.40), rgba(201, 169, 97, 0));
  }

  &::after {
    left: -130px;
    bottom: -140px;
    background: radial-gradient(circle, rgba(124, 156, 146, 0.34), rgba(124, 156, 146, 0));
    animation-duration: 15s;
  }

  .sidebar {
    width: 260px;
    padding: 14px 12px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    transition: width 320ms var(--v2-ease);
    animation: slideInLeft 420ms var(--v2-ease);

    &.is-collapse {
      width: 78px;

      /*
        折叠后 logo-text 被 v-if 移除，只剩 44px 的图标。
        胶囊内部宽度约 54px（78 - 左右 padding/边框），默认左对齐会让图标贴左、
        右边空一条，看起来歪了。这里改成居中，并把左右 padding 收窄。
      */
      .logo {
        justify-content: center;
        padding: 14px 0;
        gap: 0;
      }
    }

    .logo {
      height: 78px;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 14px;
      overflow: hidden;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        inset: -40%;
        background:
          radial-gradient(circle at 30% 40%, rgba(214, 183, 122, 0.35), transparent 55%),
          radial-gradient(circle at 65% 20%, rgba(124, 156, 146, 0.28), transparent 60%);
        opacity: 0.65;
        filter: blur(18px);
        animation: v2-float 5.8s var(--v2-ease) infinite alternate;
      }

      .logo-mark {
        width: 44px;
        height: 44px;
        border-radius: 18px;
        position: relative;
        flex-shrink: 0;
        background-image: url('@/assets/images/logo.png');
        background-size: cover;
        background-position: center;
        background-repeat: no-repeat;
        box-shadow: 0 10px 22px rgba(214, 183, 122, 0.2);
        z-index: 1;
      }

      .logo-text {
        position: relative;
        z-index: 1;
        min-width: 0;

        .title {
          font-weight: 700;
          letter-spacing: 0.8px;
          font-size: 15px;
          color: var(--v2-text);
          text-shadow: 0 8px 30px rgba(214, 183, 122, 0.22);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .subtitle {
          margin-top: 4px;
          font-size: 12px;
          color: var(--v2-text-dim);
          letter-spacing: 0.5px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }

    .sidebar-menu {
      background: transparent;
      border-right: none;
      padding: 6px 2px;

      :deep(.el-menu-item) {
        margin: 10px 0;
        height: 52px;
        line-height: 52px;
        border-radius: 18px;
        color: var(--v2-text-dim);
        transition: transform 220ms var(--v2-ease), background 220ms var(--v2-ease), color 220ms var(--v2-ease);
        position: relative;
        overflow: hidden;

        &::before {
          content: '';
          position: absolute;
          inset: 0;
          border-radius: 18px;
          background: linear-gradient(90deg, rgba(214, 183, 122, 0.22), rgba(124, 156, 146, 0.14), rgba(177, 59, 47, 0.18));
          opacity: 0;
          filter: blur(0.2px);
          transition: opacity 220ms var(--v2-ease);
        }

        &::after {
          content: '';
          position: absolute;
          left: -120%;
          top: -20%;
          width: 40%;
          height: 140%;
          transform: rotate(16deg);
          background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.28), transparent);
          opacity: 0;
        }

        &:hover {
          background: rgba(255, 255, 255, 0.045);
          color: var(--v2-text);
          transform: translate3d(2px, -1px, 0);
        }

        &:hover::before {
          opacity: 0.22;
        }

        &:hover::after {
          opacity: 1;
          animation: nav-sweep 780ms ease;
        }

        &.is-active {
          color: rgba(255, 255, 255, 0.95);
          background: rgba(255, 255, 255, 0.055);
          box-shadow: 0 16px 42px rgba(0, 0, 0, 0.36);
          transform: translate3d(2px, -1px, 0);
          text-shadow: 0 6px 20px rgba(214, 183, 122, 0.24);
        }

        &.is-active::before {
          opacity: 0.32;
        }

        .el-icon {
          position: relative;
          z-index: 1;
        }

        span {
          position: relative;
          z-index: 1;
        }
      }
    }

  }

  .main {
    flex: 1;
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 14px 14px 16px 0;
    gap: 12px;
    // 原本是 hidden，但那会把 header 里搜索建议下拉超出容器的部分裁掉；
    // 改为 visible，横向溢出交给子元素自己控制
    overflow: visible;

    .header {
      height: 78px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 18px;
      border-radius: var(--v2-radius-xl);
      position: relative;
      // 搜索建议下拉要压过页面内容（地图卡片等自带玻璃质感和层叠上下文），
      // 这里给 header 建一个高层级上下文，否则下拉下半截会被后渲染的页面元素盖住
      z-index: 20;
      transform-style: preserve-3d;
      animation: fadeInDown 380ms var(--v2-ease);

      &::before {
        content: '';
        position: absolute;
        inset: -1px;
        border-radius: inherit;
        background: linear-gradient(90deg, rgba(214, 183, 122, 0.20), rgba(124, 156, 146, 0.14), rgba(177, 59, 47, 0.18));
        opacity: 0.35;
        filter: blur(10px);
        pointer-events: none;
      }

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;
        position: relative;
        z-index: 1;

        .collapse-btn {
          width: 40px;
          height: 40px;
          display: grid;
          place-items: center;
          border-radius: 14px;
          cursor: pointer;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid rgba(255, 255, 255, 0.10);
          transition: transform 220ms var(--v2-ease), background 220ms var(--v2-ease);

          &:hover {
            transform: translate3d(0, -1px, 0);
            background: rgba(255, 255, 255, 0.07);
          }
        }

        .route-title {
          display: flex;
          flex-direction: column;
          gap: 4px;
          min-width: 0;
        }

        .route-label {
          font-weight: 800;
          letter-spacing: 0.8px;
          font-size: 14px;
          color: var(--v2-text);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .route-hint {
          font-size: 12px;
          color: var(--v2-text-dim);
          letter-spacing: 0.6px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 12px;
        position: relative;
        z-index: 1;

        .search-wrapper {
          position: relative;
          display: flex;
          align-items: center;
          gap: 8px;

          .search-scope {
            width: 124px;
            flex-shrink: 0;
          }

          .scope-option {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
          }

          .search-input {
            width: 360px;
          }

          .suggestions-dropdown {
            position: absolute;
            top: calc(100% + 10px);
            left: 132px;
            width: 360px;
            padding: 8px 8px;
            z-index: 1000;
            animation: fadeInDown 240ms ease;
          }

          .suggestion-item {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 10px 10px;
            border-radius: 16px;
            cursor: pointer;
            transition: transform 220ms var(--v2-ease), background 220ms var(--v2-ease);

            &:hover {
              background: rgba(255, 255, 255, 0.045);
              transform: translate3d(0, -1px, 0);
            }

            .suggestion-dot {
              width: 10px;
              height: 10px;
              border-radius: 999px;
              background: linear-gradient(180deg, rgba(214, 183, 122, 0.9), rgba(124, 156, 146, 0.7));
              box-shadow: 0 0 0 6px rgba(214, 183, 122, 0.06);
              flex-shrink: 0;
            }

            .suggestion-info {
              flex: 1;
              min-width: 0;
            }

            .suggestion-name {
              font-size: 13px;
              color: var(--v2-text);
              font-weight: 650;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }

            .suggestion-meta {
              margin-top: 2px;
              font-size: 12px;
              color: var(--v2-muted);
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }

            .suggestion-arrow {
              color: rgba(255, 255, 255, 0.55);
              font-size: 12px;
              flex-shrink: 0;
            }
          }
        }

        .user-pill {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 8px 12px;
          cursor: pointer;
          transition: transform 220ms var(--v2-ease), box-shadow 220ms var(--v2-ease), border-color 220ms var(--v2-ease);

          &:hover {
            transform: translate3d(0, -1px, 0);
            box-shadow: 0 14px 30px rgba(0, 0, 0, 0.28);
          }

          .username {
            color: var(--v2-text);
            font-weight: 650;
            letter-spacing: 0.2px;
          }

        }
      }
    }

    .content {
      flex: 1;
      min-height: 0;
      overflow: auto;
      padding: 6px 14px 16px 14px;
      animation: fadeInUp 420ms var(--v2-ease);

        &::-webkit-scrollbar {
          width: 8px;
          height: 8px;
        }

        &::-webkit-scrollbar-thumb {
          border-radius: 999px;
          background: linear-gradient(180deg, rgba(214, 183, 122, 0.45), rgba(124, 156, 146, 0.45));
        }
    }
  }
}

.v2-page-enter-active {
  animation: v2-page-in 320ms var(--v2-ease);
}

.v2-page-leave-active {
  animation: v2-page-out 220ms var(--v2-ease);
}

@keyframes aura-float {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  100% {
    transform: translate3d(0, -16px, 0) scale(1.06);
  }
}

@keyframes nav-sweep {
  0% { left: -120%; }
  100% { left: 170%; }
}

@keyframes fadeInUp {
  0% {
    opacity: 0;
    transform: translate3d(0, 12px, 0);
  }
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes fadeInDown {
  0% {
    opacity: 0;
    transform: translate3d(0, -10px, 0);
  }
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes slideInLeft {
  0% {
    opacity: 0;
    transform: translate3d(-12px, 0, 0);
  }
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .layout-v2 .sidebar,
  .layout-v2 .main .header,
  .layout-v2 .main .content,
  .layout-v2 .sidebar .sidebar-menu :deep(.el-menu-item)::after {
    animation: none !important;
    transition: none !important;
  }
}

@media (max-width: 1200px) {
  .layout-v2 .main .header .header-right .search-wrapper .search-input {
    width: 300px;
  }

  .layout-v2 .main .header .header-right .search-wrapper .suggestions-dropdown {
    width: 300px;
  }
}

@media (max-width: 900px) {
  .layout-v2 .sidebar {
    width: 78px;
  }

  .layout-v2 .sidebar .logo .logo-text {
    display: none;
  }

  .layout-v2 .main {
    padding: 10px 10px 12px 0;
  }

  .layout-v2 .main .header {
    height: auto;
    min-height: 74px;
    padding: 10px 12px;
    gap: 10px;
  }

  .layout-v2 .main .header .header-right .search-wrapper .search-scope {
    width: 108px;
  }

  .layout-v2 .main .header .header-right .search-wrapper .search-input {
    width: 250px;
  }

  .layout-v2 .main .header .header-right .search-wrapper .suggestions-dropdown {
    left: 116px;
    width: 250px;
  }

  .layout-v2 .main .header .header-right .user-pill .username {
    display: none;
  }
}

@media (max-width: 768px) {
  .layout-v2 {
    height: 100dvh;
  }

  .layout-v2 .sidebar {
    width: 64px;
    padding: 10px 8px;
  }

  .layout-v2 .sidebar .logo {
    height: 58px;
    padding: 8px;
    justify-content: center;
  }

  .layout-v2 .sidebar .logo .logo-mark {
    width: 34px;
    height: 34px;
    border-radius: 12px;
  }

  .layout-v2 .sidebar .sidebar-menu :deep(.el-menu-item) {
    margin: 6px 0;
    height: 44px;
    line-height: 44px;
    border-radius: 14px;
    padding: 0 10px !important;
  }

  .layout-v2 .main {
    padding: 8px 8px 10px 0;
  }

  .layout-v2 .main .header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .layout-v2 .main .header .header-left {
    justify-content: space-between;
  }

  .layout-v2 .main .header .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .layout-v2 .main .header .header-right .search-wrapper {
    flex: 1;
    min-width: 0;
  }

  .layout-v2 .main .header .header-right .search-wrapper .search-scope {
    width: 96px;
  }

  .layout-v2 .main .header .header-right .search-wrapper .search-input {
    width: calc(100% - 104px);
    min-width: 0;
  }

  .layout-v2 .main .header .header-right .search-wrapper .suggestions-dropdown {
    left: 0;
    width: 100%;
    top: calc(100% + 8px);
  }

  .layout-v2 .main .content {
    padding: 4px 8px 12px 8px;
  }
}
</style>

<!-- 用户下拉菜单样式统一维护在 assets/styles/main.scss，此处不再重复定义 -->

