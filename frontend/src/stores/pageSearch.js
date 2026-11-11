import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 顶部搜索与页面内列表的联动通道。
 *
 * 为什么需要它：建筑列表、地图导览这类页面原本各自带一套搜索框，
 * 和顶部搜索完全重复。现在顶部搜索在这些页面里改为「就地过滤」，
 * 由本 store 传递关键词与范围，页面监听后自己重新拉数据。
 * 其他页面仍维持原来的行为（回车跳转到 /search）。
 *
 * 关键词按页面分槽存储，而不是共用一份：
 * 两个页面接受的查询参数不同（建筑列表认 keyword+dynasty，地图认 scope），
 * 共用一份会导致在建筑列表搜完再进地图，地图莫名只剩一条点位。
 */
export const usePageSearchStore = defineStore('pageSearch', () => {
  /**
   * 支持就地过滤的路由 → 该页的筛选状态。
   * 每个页面各自持有 keyword/scope/tick，互不影响。
   */
  const buckets = reactive({
    '/buildings': { keyword: '', scope: 'all', tick: 0 },
    '/map-explorer': { keyword: '', scope: 'all', tick: 0 }
  })

  /** 当前活跃页面路径，由 LayoutV2 在路由变化时写入 */
  const activePath = ref('/buildings')

  const INLINE_FILTER_ROUTES = ['/buildings', '/map-explorer']

  const isInlineFilterRoute = (path) => {
    if (!path) return false
    // /buildings/123 这种详情页不该被就地过滤，只有列表根路径算
    return INLINE_FILTER_ROUTES.some(base => path === base || path === `${base}/`)
  }

  /** 归一化路径，保证 /buildings 与 /buildings/ 落到同一个桶 */
  const normalizePath = (path) => {
    if (!path) return '/buildings'
    const trimmed = path.replace(/\/+$/, '')
    return INLINE_FILTER_ROUTES.includes(trimmed) ? trimmed : '/buildings'
  }

  const currentBucket = computed(() => buckets[normalizePath(activePath.value)] || buckets['/buildings'])

  /** 读取指定页面的状态；不传就用当前活跃页 */
  const bucketOf = (path) => buckets[normalizePath(path || activePath.value)] || buckets['/buildings']

  /** 对外暴露的是「当前页」的只读视图，页面用它们做展示 */
  const keyword = computed(() => currentBucket.value.keyword)
  const scope = computed(() => currentBucket.value.scope)
  const tick = computed(() => currentBucket.value.tick)

  const setActivePath = (path) => {
    activePath.value = normalizePath(path)
  }

  /**
   * 提交一次搜索。
   * targetPath 默认写当前活跃页——这样顶部搜索永远只影响用户正在看的页面。
   */
  const submit = (nextKeyword, nextScope = 'all', targetPath = null) => {
    const bucket = bucketOf(targetPath || activePath.value)
    bucket.keyword = (nextKeyword || '').trim()
    bucket.scope = nextScope || 'all'
    bucket.tick += 1
  }

  /** 清空某一页的筛选条件（默认当前页） */
  const reset = (targetPath = null) => {
    const bucket = bucketOf(targetPath || activePath.value)
    bucket.keyword = ''
    bucket.scope = 'all'
    bucket.tick += 1
  }

  return {
    activePath,
    keyword,
    scope,
    tick,
    isInlineFilterRoute,
    setActivePath,
    submit,
    reset
  }
})
