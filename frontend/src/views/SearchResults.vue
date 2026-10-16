<template>
  <div class="search-results-container">
    <div class="search-header">
      <h1>{{ t('searchResults') }}</h1>
      <p v-if="keyword">{{ t('keywordLabel') }}: <strong>"{{ keyword }}"</strong></p>
    </div>

    <div class="filter-bar">
      <el-select v-model="selectedCategory" :placeholder="t('selectCategory')" clearable @change="handleSearch">
        <el-option :label="t('allCategory')" :value="null" />
        <el-option label="古桥梁" :value="1" />
        <el-option label="古宫殿" :value="2" />
        <el-option label="榫卯结构" :value="3" />
      </el-select>

      <el-select v-model="selectedDynasty" :placeholder="t('selectDynasty')" clearable @change="handleSearch">
        <el-option :label="t('allDynasty')" value="" />
        <el-option label="唐" value="唐" />
        <el-option label="宋" value="宋" />
        <el-option label="元" value="元" />
        <el-option label="明" value="明" />
        <el-option label="清" value="清" />
      </el-select>

      <el-select v-model="sortBy" :placeholder="t('sortBy')" @change="handleSearch">
        <el-option :label="t('relevance')" value="" />
        <el-option :label="t('latestPublished')" value="createdAt" />
        <el-option :label="t('mostViewed')" value="viewCount" />
        <el-option :label="t('mostLiked')" value="likeCount" />
      </el-select>

      <div class="result-count">
        {{ t('foundResultsPrefix') }} <span class="count">{{ total }}</span> {{ t('foundResultsSuffix') }}
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('loading') }}</span>
    </div>

    <template v-else>
      <div v-if="models.length > 0">
        <ModelGrid :models="models" :loading="false" :span="6" />
      </div>

      <div v-else class="empty-state">
        <el-empty :description="t('noRelatedModels')">
          <template #image>
            <el-icon :size="80"><Search /></el-icon>
          </template>
        </el-empty>

        <div class="hot-models-section">
          <h3>{{ t('hotModelRecommend') }}</h3>
          <ModelGrid :models="hotModels" :loading="false" :span="6" />
        </div>
      </div>

      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[12, 24, 36, 48]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useModelStore } from '@/stores/model'
import ModelGrid from '@/components/ModelGrid.vue'
import { Search, Loading } from '@element-plus/icons-vue'
import { useUiPreferences } from '@/composables/useUiPreferences'

const route = useRoute()
const router = useRouter()
const modelStore = useModelStore()
const { t } = useUiPreferences()

const loading = ref(false)
const models = ref([])
const hotModels = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const keyword = ref('')
const keywordScope = ref('all')
const selectedCategory = ref(null)
const selectedDynasty = ref('')
const sortBy = ref('')

function loadSearchResults() {
  loading.value = true
  modelStore.fetchModels(
    currentPage.value,
    pageSize.value,
    keyword.value,
    selectedCategory.value,
    selectedDynasty.value,
    1,
    keywordScope.value
  ).then((response) => {
    models.value = response.records || []
    total.value = response.total || 0
  }).catch((error) => {
    console.error('[SearchResults] 搜索失败', error)
  }).finally(() => {
    loading.value = false
  })
}

async function loadHotModels() {
  try {
    const data = await modelStore.fetchHotModels('viewCount', 4)
    hotModels.value = data
  } catch (error) {
    console.error('[SearchResults] 加载热门模型失败', error)
  }
}

watch(() => route.query, (newQuery) => {
  keyword.value = newQuery.keyword || ''
  keywordScope.value = newQuery.scope || 'all'
  selectedCategory.value = newQuery.categoryId ? Number(newQuery.categoryId) : null
  selectedDynasty.value = newQuery.dynasty || ''
  currentPage.value = 1
  loadSearchResults()
}, { immediate: true })

onMounted(() => {
  loadHotModels()
})

const handleSearch = () => {
  currentPage.value = 1
  router.push({
    path: '/search',
    query: {
      keyword: keyword.value,
      scope: keywordScope.value,
      categoryId: selectedCategory.value ?? undefined,
      dynasty: selectedDynasty.value || undefined
    }
  })
  loadSearchResults()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  loadSearchResults()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadSearchResults()
}
</script>

<style lang="scss" scoped>
.search-results-container {
  color: #e2e8f0;

  .search-header {
    margin-bottom: 20px;

    h1 {
      font-size: 24px;
      color: #f8fafc;
      text-shadow: 0 4px 16px rgba(8, 15, 30, 0.42);
      margin-bottom: 10px;
    }

    p {
      color: #cbd5e1;
      font-size: 14px;
      letter-spacing: 0.2px;

      :deep(strong),
      :deep(b) {
        color: #f8fafc;
      }
    }
  }

  .filter-bar {
    display: flex;
    gap: 15px;
    align-items: center;
    background: rgba(248, 250, 255, 0.96);
    border: 1px solid rgba(181, 196, 227, 0.62);
    box-shadow:
      0 12px 34px rgba(15, 23, 42, 0.18),
      inset 0 1px 0 rgba(255, 255, 255, 0.45);
    padding: 20px;
    border-radius: 14px;
    margin-bottom: 20px;

    :deep(.el-select) {
      min-width: 170px;
    }

    :deep(.el-select__wrapper) {
      background: #ffffff !important;
      box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.52) inset !important;
      border-radius: 10px !important;
      color: #0f172a !important;
      transition: box-shadow 0.2s ease;
    }

    :deep(.el-select__wrapper.is-focused) {
      box-shadow:
        0 0 0 1px rgba(96, 129, 203, 0.75) inset !important,
        0 0 0 3px rgba(96, 129, 203, 0.16) !important;
    }

    :deep(.el-select__placeholder),
    :deep(.el-select__selected-item),
    :deep(.el-input__inner) {
      color: #1e293b !important;
      font-weight: 560;
    }

    :deep(.el-select__placeholder) {
      color: #64748b !important;
      font-weight: 500;
    }

    .result-count {
      margin-left: auto;
      color: #334155;
      font-weight: 560;

      .count {
        color: #2563eb;
        font-weight: 700;
      }
    }
  }

  .loading-state {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 60px 0;
    color: #cbd5e1;

    .el-icon {
      margin-right: 10px;
    }
  }

  .empty-state {
    background: rgba(248, 250, 255, 0.96);
    border: 1px solid rgba(181, 196, 227, 0.58);
    box-shadow: 0 12px 34px rgba(15, 23, 42, 0.16);
    padding: 40px;
    border-radius: 14px;
    text-align: center;

    :deep(.el-empty__description) {
      color: #475569 !important;
      font-weight: 560;
    }

    .hot-models-section {
      margin-top: 40px;

      h3 {
        font-size: 18px;
        color: #0f172a;
        margin-bottom: 20px;
      }
    }
  }

  .pagination {
    display: flex;
    justify-content: center;
    margin-top: 30px;
  }
}

@media (max-width: 900px) {
  .search-results-container .filter-bar {
    flex-wrap: wrap;
    gap: 10px;
    padding: 14px;
  }

  .search-results-container .filter-bar :deep(.el-select) {
    min-width: 0;
    width: calc(50% - 6px);
  }

  .search-results-container .filter-bar .result-count {
    width: 100%;
    margin-left: 0;
    text-align: right;
  }
}

@media (max-width: 768px) {
  .search-results-container .search-header h1 {
    font-size: 20px;
  }

  .search-results-container .search-header p {
    font-size: 13px;
  }

  .search-results-container .filter-bar {
    border-radius: 12px;
  }

  .search-results-container .filter-bar :deep(.el-select) {
    width: 100%;
  }

  .search-results-container .filter-bar .result-count {
    text-align: left;
  }

  .search-results-container .empty-state {
    padding: 24px 14px;
  }

  .search-results-container .pagination {
    overflow-x: auto;
    justify-content: flex-start;
    padding-bottom: 4px;
  }
}
</style>
