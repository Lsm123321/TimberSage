import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('@/views/LayoutV2.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页', titleKey: 'routeHome' }
      },
      {
        path: 'about',
        name: 'AboutUs',
        component: () => import('@/views/AboutUs.vue'),
        meta: { title: '关于我们', titleKey: 'routeAbout' }
      },
      {
        path: 'buildings',
        name: 'Buildings',
        component: () => import('@/views/Buildings.vue'),
        meta: { title: '建筑列表', titleKey: 'routeBuildings' }
      },
      {
        path: 'buildings/:id',
        name: 'ModelDetail',
        component: () => import('@/views/ModelDetail.vue'),
        meta: { title: '模型详情', titleKey: 'routeModelDetail' }
      },
      {
        path: 'viewer/:id',
        name: 'ModelViewer',
        component: () => import('@/views/ModelViewer.vue'),
        meta: { title: '模型浏览', titleKey: 'routeModelViewer' }
      },
      {
        path: 'map-explorer',
        name: 'MapExplorer',
        component: () => import('@/views/MapExplorer.vue'),
        meta: { title: '地图导览', titleKey: 'routeMapExplorer' }
      },
      {
        path: 'chat',
        redirect: '/map-explorer'
      },
      {
        path: 'favorites',
        redirect: { path: '/profile', query: { tab: 'favorites' } }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', titleKey: 'routeProfile' }
      },
      {
        path: 'role-upgrade',
        name: 'RoleUpgradeApply',
        component: () => import('@/views/RoleUpgradeApply.vue'),
        meta: { title: '权限申请', titleKey: 'routeRoleUpgrade' }
      },
      {
        path: 'search',
        name: 'SearchResults',
        component: () => import('@/views/SearchResults.vue'),
        meta: { title: '搜索结果', titleKey: 'routeSearchResults' }
      }
    ]
  },
  {
    path: '/v2/:pathMatch(.*)*',
    redirect: to => `/${to.params.pathMatch || ''}`
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard'
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/AdminDashboard.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminDashboard' }
      },
      {
        path: 'models',
        name: 'AdminModels',
        component: () => import('@/views/admin/ModelManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminModels' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminUsers' }
      },
      {
        path: 'role-upgrade',
        name: 'AdminRoleUpgrade',
        component: () => import('@/views/admin/RoleUpgradeApplyManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminRoleUpgrade' }
      },
      {
        path: 'feedback',
        name: 'AdminFeedback',
        component: () => import('@/views/admin/FeedbackManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminFeedback' }
      },
      {
        path: 'site-feedback',
        name: 'AdminSiteFeedback',
        component: () => import('@/views/admin/SiteFeedbackManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminSiteFeedback' }
      },
      {
        path: 'comments',
        name: 'AdminComments',
        component: () => import('@/views/admin/CommentManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminComments' }
      },
      {
        path: 'questions',
        name: 'AdminQuestions',
        component: () => import('@/views/admin/PresetQuestionManage.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminQuestions' }
      },
      {
        path: 'statistics',
        name: 'AdminStatistics',
        component: () => import('@/views/admin/Statistics.vue'),
        meta: { requiresAdmin: true, titleKey: 'routeAdminStatistics' }
      },
      {
        path: 'voices',
        name: 'AdminVoiceManage',
        component: () => import('@/views/admin/VoiceManage.vue'),
        meta: { requiresAdmin: true, title: '音色管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin)

  if (requiresAuth && !authStore.token) {
    next({ name: 'Login' })
    return
  }

  // 有 token 但 userInfo 还没加载（刷新直达 admin 页时 App.vue 的 onMounted 还没完成）
  // 先拉取用户信息再校验角色，避免把 admin 误判成无权限
  if (requiresAdmin && authStore.token && !authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch (e) {
      // 拉取失败按无权限处理
    }
  }

  if (requiresAdmin && authStore.userInfo?.role !== 'admin') {
    next({ name: 'Forbidden' })
  } else if ((to.name === 'Login' || to.name === 'Register') && authStore.token) {
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router
