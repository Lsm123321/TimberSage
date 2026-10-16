<template>
  <div class="home-container">
    <div
      class="hero-section"
      @pointermove="handleHeroPointerMove"
      @pointerleave="resetHeroPointer"
    >
      <div class="hero-bg-layer" />
      <div class="hero-flow-layer" aria-hidden="true" />
      <div class="hero-beam-layer" aria-hidden="true">
        <span class="beam beam-a" />
        <span class="beam beam-b" />
        <span class="beam beam-c" />
      </div>
      <div class="hero-particle-layer" aria-hidden="true">
        <span v-for="particle in heroParticles" :key="particle.id" class="particle" :style="particle.style" />
      </div>
      <div class="hero-pointer-glow" aria-hidden="true" />
      <div class="hero-content">
        <h1>{{ t('heroTitle') }}</h1>
        <p>{{ t('heroSubtitle') }}</p>
        <div class="hero-buttons">
          <el-button type="primary" size="large" class="hero-cta btn-shine" @click="$router.push('/buildings')">
            {{ t('browseBuildings') }}
          </el-button>
          <el-button size="large" class="hero-cta btn-shine" @click="$router.push('/map-explorer')">
            {{ t('mapExplorer') }}
          </el-button>
        </div>
      </div>
    </div>

    <div class="category-section">
      <h2>{{ t('categoryBrowse') }}</h2>
      <div class="category-nav">
        <div
          v-for="category in categories"
          :key="category.id"
          class="category-item"
          :class="{ active: selectedCategory === category.categoryCode }"
          @click="selectCategory(category)"
        >
          <el-icon class="category-icon"><component :is="category.icon" /></el-icon>
          <span class="category-name">{{ category.categoryName }}</span>
        </div>
      </div>
    </div>

    <div class="hot-keywords-section" v-if="hotKeywords.length > 0">
      <div class="section-header">
        <span class="section-title">{{ t('hotSearch') }}</span>
      </div>
      <div class="hot-keywords">
        <el-tag
          v-for="keyword in hotKeywords"
          :key="keyword"
          class="hot-keyword"
          @click="searchKeyword(keyword)"
        >
          {{ keyword }}
        </el-tag>
      </div>
    </div>

    <div class="models-section" v-if="selectedCategory || currentModels.length > 0">
      <div class="section-header">
        <h2>{{ selectedCategoryName || t('recommendedModels') }}</h2>
        <div class="section-actions">
          <el-button v-if="selectedCategory" text @click="clearCategorySelection">
            {{ t('backToDefault') }}
          </el-button>
          <el-button
            v-if="currentModels.length > 0"
            text
            @click="$router.push({ path: '/buildings', query: { category: selectedCategory } })"
          >
            {{ t('viewMore') }}
          </el-button>
        </div>
      </div>

      <ModelGrid v-if="currentModels.length > 0" :models="currentModels" :loading="loading" :span="6" />
      <el-empty
        v-else
        class="category-empty-state"
        :description="t('noModelsInCategory')"
      />
    </div>

    <div class="popular-section">
      <div class="section-header">
        <h2>{{ t('hotBuildings') }}</h2>
        <el-button text @click="$router.push('/buildings')">{{ t('viewMore') }}</el-button>
      </div>

      <ModelGrid :models="popularModels" :loading="loading" :span="6" />
    </div>

    <section class="feature-section">
      <div class="feature-header">
        <div class="title-wrap">
          <h2>{{ t('platformFeaturesTitle') }}</h2>
          <p class="subtitle">{{ t('platformFeaturesSubtitle') }}</p>
        </div>
        <div class="feature-highlights">
          <div class="pill">{{ t('featureHighlight1') }}</div>
          <div class="pill">{{ t('featureHighlight2') }}</div>
          <div class="pill">{{ t('featureHighlight3') }}</div>
        </div>
      </div>

      <el-row :gutter="[16, 16]" class="feature-grid">
        <el-col
          v-for="item in featureCards"
          :key="item.key"
          :xs="24"
          :sm="12"
          :md="8"
        >
          <div
            class="feature-card"
            :style="featureCardStyles[item.key]"
            @pointermove="(event) => handleFeatureCardMove(item.key, event)"
            @pointerleave="() => resetFeatureCard(item.key)"
          >
            <div class="icon-badge">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="card-body">
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
              <div class="meta">
                <span class="meta-dot" />
                <span class="meta-text">{{ item.meta }}</span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useModelStore } from '@/stores/model'
import ModelGrid from '@/components/ModelGrid.vue'
import { Box, ChatLineRound, Collection, OfficeBuilding, House, Connection, Location, DataAnalysis, Checked } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUiPreferences } from '@/composables/useUiPreferences'

const router = useRouter()
const modelStore = useModelStore()
const { t } = useUiPreferences()

const loading = ref(false)
const popularModels = ref([])
const currentModels = ref([])
const selectedCategory = ref('')
const selectedCategoryName = ref('')
const hotKeywords = ref([])
const featureCards = ref([])

const categories = ref([])
const heroParticles = [
  { id: 1, style: '--size: 4px; --left: 8%; --top: 72%; --delay: 0.1s; --duration: 8.4s;' },
  { id: 2, style: '--size: 5px; --left: 22%; --top: 68%; --delay: 1.4s; --duration: 10.2s;' },
  { id: 3, style: '--size: 3px; --left: 34%; --top: 74%; --delay: 2.2s; --duration: 9.2s;' },
  { id: 4, style: '--size: 4px; --left: 52%; --top: 78%; --delay: 0.6s; --duration: 11.2s;' },
  { id: 5, style: '--size: 6px; --left: 68%; --top: 70%; --delay: 2.8s; --duration: 9.8s;' },
  { id: 6, style: '--size: 4px; --left: 82%; --top: 76%; --delay: 1.2s; --duration: 10.8s;' },
  { id: 7, style: '--size: 3px; --left: 90%; --top: 69%; --delay: 3.4s; --duration: 8.8s;' }
]
const featureCardStyles = reactive({})
const categoryIconMap = {
  bridge: Connection,
  palace: OfficeBuilding,
  temple: House,
  garden: Collection,
  dwelling: House,
  tower: OfficeBuilding
}

onMounted(async () => {
  await loadCategories()
  await loadPopularModels()
  await loadHotKeywords()
  buildFeatureCards()
})

const loadCategories = async () => {
  try {
    const response = await request.get('/categories')
    const rows = response.data?.data || []
    categories.value = rows
      .sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
      .slice(0, 3)
      .map(item => ({
        ...item,
        icon: categoryIconMap[item.categoryCode] || Connection
      }))
  } catch (error) {
    console.error('[Home] 加载分类失败', error)
    categories.value = []
  }
}

const loadPopularModels = async () => {
  loading.value = true
  try {
    // 取点赞前 4，正好凑满一行网格，不会落下单张卡片
    const data = await modelStore.fetchHotModels('like', 4)
    popularModels.value = data
  } catch (error) {
    console.error('[Home] 加载热门模型失败', error)
  } finally {
    loading.value = false
  }
}

const loadHotKeywords = async () => {
  try {
    const response = await request.get('/hot-search')
    const data = response.data
    if (data?.code === 200) {
      hotKeywords.value = (data.data || []).map(item => item.keyword)
    } else {
      hotKeywords.value = []
    }
  } catch (error) {
    console.error('[Home] 加载热搜关键词失败', error)
    hotKeywords.value = []
  }
}

const selectCategory = async (category) => {
  selectedCategory.value = category.categoryCode
  selectedCategoryName.value = category.categoryName
  loading.value = true
  try {
    // 故意不传 status，传了后端会走 status 分支把 categoryId 忽略掉
    const response = await modelStore.fetchModels(1, 4, '', category.id, '', '')
    const records = response.records || []
    currentModels.value = records.slice(0, 4)
    if (records.length === 0) {
      ElMessage.info(`${t('noModelsInCategoryPrefix')}“${category.categoryName}”${t('noModelsInCategorySuffix')}`)
    }
  } catch (error) {
    console.error('[Home] 加载分类模型失败', error)
  } finally {
    loading.value = false
  }
}

const clearCategorySelection = () => {
  selectedCategory.value = ''
  selectedCategoryName.value = ''
  currentModels.value = []
}

const searchKeyword = (keyword) => {
  router.push({ path: '/search', query: { keyword } })
}

const buildFeatureCards = () => {
  featureCards.value = [
    {
      key: 'feature3d',
      icon: Box,
      title: t('feature3dTitle'),
      desc: t('feature3dDesc'),
      meta: t('feature3dMeta')
    },
    {
      key: 'featureMap',
      icon: Location,
      title: t('featureMapTitle'),
      desc: t('featureMapDesc'),
      meta: t('featureMapMeta')
    },
    {
      key: 'featureAI',
      icon: ChatLineRound,
      title: t('featureAiTitle'),
      desc: t('featureAiDesc'),
      meta: t('featureAiMeta')
    },
    {
      key: 'featureFav',
      icon: Collection,
      title: t('featureFavTitle'),
      desc: t('featureFavDesc'),
      meta: t('featureFavMeta')
    },
    {
      key: 'featureData',
      icon: DataAnalysis,
      title: t('featureDataTitle'),
      desc: t('featureDataDesc'),
      meta: t('featureDataMeta')
    },
    {
      key: 'featureWorkflow',
      icon: Checked,
      title: t('featureWorkflowTitle'),
      desc: t('featureWorkflowDesc'),
      meta: t('featureWorkflowMeta')
    }
  ]
}

const handleHeroPointerMove = (event) => {
  const section = event.currentTarget
  if (!section) return
  const rect = section.getBoundingClientRect()
  const x = ((event.clientX - rect.left) / rect.width) * 100
  const y = ((event.clientY - rect.top) / rect.height) * 100
  section.style.setProperty('--hero-pointer-x', `${x}%`)
  section.style.setProperty('--hero-pointer-y', `${y}%`)
}

const resetHeroPointer = (event) => {
  const section = event.currentTarget
  if (!section) return
  section.style.setProperty('--hero-pointer-x', '50%')
  section.style.setProperty('--hero-pointer-y', '38%')
}

const handleFeatureCardMove = (key, event) => {
  const card = event.currentTarget
  if (!card) return
  const rect = card.getBoundingClientRect()
  const rx = (event.clientX - rect.left - rect.width / 2) / rect.width
  const ry = (event.clientY - rect.top - rect.height / 2) / rect.height
  const rotateY = (rx * 7).toFixed(2)
  const rotateX = (-ry * 7).toFixed(2)
  const shiftX = (rx * 10).toFixed(2)
  const shiftY = (ry * 10).toFixed(2)
  featureCardStyles[key] = {
    transform: `perspective(760px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translate3d(${shiftX}px, ${shiftY}px, 0)`,
    '--card-glow-x': `${((event.clientX - rect.left) / rect.width) * 100}%`,
    '--card-glow-y': `${((event.clientY - rect.top) / rect.height) * 100}%`
  }
}

const resetFeatureCard = (key) => {
  featureCardStyles[key] = {
    transform: 'perspective(760px) rotateX(0deg) rotateY(0deg) translate3d(0, 0, 0)',
    '--card-glow-x': '18%',
    '--card-glow-y': '22%'
  }
}
</script>

<style lang="scss" scoped>
.home-container {
  --hero-pointer-x: 50%;
  --hero-pointer-y: 38%;

  .hero-section {
    height: 430px;
    position: relative;
    background: #08101f;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: -20px -20px 30px -20px;
    overflow: hidden;
    isolation: isolate;
    border-radius: 20px;
    border: 1px solid rgba(142, 220, 255, 0.26);
    box-shadow:
      0 28px 70px rgba(7, 15, 31, 0.45),
      inset 0 0 44px rgba(120, 215, 255, 0.1);

    .hero-bg-layer {
      position: absolute;
      inset: -8%;
      z-index: 0;
      background-image:
        linear-gradient(120deg, rgba(8, 14, 28, 0.22), rgba(20, 34, 62, 0.12)),
        url('@/assets/images/bg.jpg');
      background-size: cover;
      background-position: center;
      background-repeat: no-repeat;
      transform-origin: center;
      animation: heroBgDrift 16s ease-in-out infinite alternate;
    }

    .hero-flow-layer {
      position: absolute;
      inset: 0;
      z-index: 1;
      pointer-events: none;
      background: linear-gradient(112deg, transparent 0%, rgba(124, 224, 255, 0.2) 46%, transparent 80%);
      transform: translateX(-110%);
      animation: heroFlow 6s linear infinite;
    }

    .hero-beam-layer {
      position: absolute;
      inset: 0;
      z-index: 1;
      pointer-events: none;
      overflow: hidden;

      .beam {
        position: absolute;
        width: 2px;
        height: 240px;
        border-radius: 999px;
        background: linear-gradient(180deg, rgba(125, 236, 255, 0), rgba(125, 236, 255, 0.95), rgba(125, 236, 255, 0));
        box-shadow: 0 0 22px rgba(125, 236, 255, 0.6);
        opacity: 0.85;
      }

      .beam-a {
        left: 18%;
        top: -20%;
        transform: rotate(18deg);
        animation: beamSweepA 7.5s ease-in-out infinite;
      }

      .beam-b {
        left: 52%;
        top: -16%;
        transform: rotate(-13deg);
        animation: beamSweepB 9s ease-in-out infinite;
      }

      .beam-c {
        left: 78%;
        top: -12%;
        transform: rotate(11deg);
        animation: beamSweepC 8.4s ease-in-out infinite;
      }
    }

    .hero-particle-layer {
      position: absolute;
      inset: 0;
      z-index: 1;
      pointer-events: none;
      overflow: hidden;

      .particle {
        position: absolute;
        left: var(--left);
        top: var(--top);
        width: var(--size);
        height: var(--size);
        border-radius: 999px;
        background: radial-gradient(circle, rgba(168, 241, 255, 0.95), rgba(168, 241, 255, 0));
        box-shadow: 0 0 14px rgba(120, 215, 255, 0.7);
        animation: particleFloat var(--duration) ease-in-out var(--delay) infinite;
      }
    }

    .hero-pointer-glow {
      position: absolute;
      inset: 0;
      z-index: 2;
      pointer-events: none;
      background:
        radial-gradient(circle at var(--hero-pointer-x) var(--hero-pointer-y), rgba(148, 224, 255, 0.26), rgba(148, 224, 255, 0) 34%),
        radial-gradient(circle at calc(var(--hero-pointer-x) - 10%) calc(var(--hero-pointer-y) + 12%), rgba(128, 179, 255, 0.14), rgba(128, 179, 255, 0) 38%);
      transition: background 140ms ease;
    }

    &::before {
      content: '';
      position: absolute;
      inset: -1px;
      z-index: 1;
      pointer-events: none;
      border: 1px solid rgba(106, 227, 255, 0.34);
      box-shadow: inset 0 0 24px rgba(60, 170, 255, 0.2);
    }

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      z-index: 0;
      background:
        radial-gradient(circle at 22% 30%, rgba(255, 255, 255, 0.12), transparent 42%),
        radial-gradient(circle at 78% 70%, rgba(255, 255, 255, 0.08), transparent 38%),
        linear-gradient(180deg, rgba(5, 12, 24, 0.08) 0%, rgba(5, 12, 24, 0.32) 100%);
      pointer-events: none;
    }

    .hero-content {
      position: relative;
      z-index: 3;
      text-align: center;
      color: white;
      padding: 0 20px;
      animation: heroContentFadeIn 0.9s ease-out both;

      h1 {
        font-size: 52px;
        margin-bottom: 20px;
        text-shadow: 0 4px 18px rgba(0, 0, 0, 0.35);
        animation: heroTitleGlow 3.2s ease-in-out infinite alternate;
      }

      p {
        font-size: 19px;
        margin-bottom: 30px;
        opacity: 0.95;
        text-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
        animation: heroSubtitleBreath 3.2s ease-in-out infinite alternate;
      }

      .hero-buttons {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 14px;

        .el-button {
          border-radius: 999px;
          height: 46px;
          padding: 0 30px;
          transition: transform 0.25s ease, box-shadow 0.25s ease, filter 0.25s ease;

          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 10px 22px rgba(0, 0, 0, 0.24);
            filter: brightness(1.03);
          }
        }

        .el-button:nth-child(1) {
          animation: heroButtonRise 0.7s ease-out 0.2s both;
        }

        .el-button:nth-child(2) {
          animation: heroButtonRise 0.7s ease-out 0.32s both;
        }

        .hero-cta {
          position: relative;
          overflow: hidden;
          isolation: isolate;

          &::after {
            content: '';
            position: absolute;
            top: -24%;
            left: -130%;
            width: 48%;
            height: 150%;
            transform: rotate(24deg);
            background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0));
            animation: buttonShine 3.6s ease-in-out infinite;
            pointer-events: none;
            z-index: 2;
          }
        }
      }
    }
  }

  .category-section {
    background: white;
    padding: 30px;
    border-radius: 10px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);

    h2 {
      font-size: 20px;
      color: #333;
      margin-bottom: 20px;
    }

    .category-nav {
      display: flex;
      gap: 30px;

      .category-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 20px 30px;
        border-radius: 10px;
        cursor: pointer;
        transition: all 0.3s;

        &:hover {
          background: #f5f7fa;

          &:not(.active) .category-name {
            color: #0f172a;
          }
        }

        &.active {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;

          .category-icon {
            color: white;
          }
        }

        .category-icon {
          font-size: 40px;
          color: #667eea;
          margin-bottom: 10px;
        }

        .category-name {
          font-size: 14px;
          color: #334155;
          font-weight: 650;
          letter-spacing: 0.2px;
          opacity: 0.94;
        }
      }
    }
  }

  .hot-keywords-section {
    background: rgba(12, 18, 34, 0.62);
    padding: 20px 30px;
    border-radius: 10px;
    margin-bottom: 20px;
    border: 1px solid rgba(136, 220, 255, 0.22);
    box-shadow:
      0 18px 40px rgba(3, 7, 18, 0.35),
      inset 0 0 22px rgba(120, 215, 255, 0.08);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);

    .section-header {
      margin-bottom: 15px;

      .section-title {
        font-size: 16px;
        color: rgba(235, 248, 255, 0.92);
        letter-spacing: 0.2px;
        text-shadow: 0 2px 10px rgba(0, 0, 0, 0.28);
      }
    }

    .hot-keywords {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;

      .hot-keyword {
        cursor: pointer;
        padding: 8px 14px;
        border-radius: 999px;
        border: 1px solid rgba(144, 224, 255, 0.24);
        background: linear-gradient(135deg, rgba(92, 201, 255, 0.22), rgba(141, 114, 255, 0.18));
        color: rgba(245, 252, 255, 0.96);
        font-weight: 650;
        letter-spacing: 0.2px;
        text-shadow: 0 1px 10px rgba(0, 0, 0, 0.22);
        transition: transform 140ms ease, opacity 140ms ease, background 140ms ease, border-color 140ms ease;

        &:hover {
          opacity: 0.96;
          transform: translateY(-1px);
          border-color: rgba(170, 235, 255, 0.45);
          background: linear-gradient(135deg, rgba(92, 201, 255, 0.3), rgba(141, 114, 255, 0.26));
        }
      }
    }

    // el-tag 自带样式在深色背景上看不清，这里定向改掉
    :deep(.hot-keyword.el-tag) {
      --el-tag-bg-color: transparent;
      --el-tag-border-color: transparent;
      --el-tag-text-color: inherit;
      background: inherit;
      border-color: inherit;
    }

    :deep(.hot-keyword.el-tag .el-tag__content) {
      color: inherit;
    }
  }

  .models-section,
  .popular-section {
    background: white;
    padding: 30px;
    border-radius: 10px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;

      .section-actions {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      h2 {
        font-size: 20px;
        color: #333;
      }
    }
  }

  .category-empty-state {
    padding: 24px 0 8px;
  }

  .feature-section {
    background:
      radial-gradient(circle at 12% 18%, rgba(124, 224, 255, 0.14), transparent 42%),
      radial-gradient(circle at 82% 24%, rgba(102, 126, 234, 0.14), transparent 46%),
      linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(255, 255, 255, 0.92) 100%);
    padding: 34px 34px 28px;
    border-radius: 14px;
    border: 1px solid rgba(15, 23, 42, 0.08);
    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
    overflow: hidden;

    .feature-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 18px;

      .title-wrap {
        h2 {
          font-size: 22px;
          color: #0f172a;
          margin: 0 0 8px;
          letter-spacing: 0.2px;
        }

        .subtitle {
          margin: 0;
          color: rgba(51, 65, 85, 0.88);
          line-height: 1.6;
          font-size: 13px;
        }
      }

      .feature-highlights {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        justify-content: flex-end;

        .pill {
          padding: 8px 12px;
          border-radius: 999px;
          background: rgba(15, 23, 42, 0.06);
          border: 1px solid rgba(15, 23, 42, 0.08);
          color: rgba(15, 23, 42, 0.78);
          font-size: 12px;
          line-height: 1;
          white-space: nowrap;
        }
      }
    }

    .feature-grid {
      margin-top: 10px;

      :deep(.el-col) {
        display: flex;
      }
    }

    .feature-card {
      position: relative;
      display: flex;
      gap: 12px;
      padding: 16px 16px 14px;
      width: 100%;
      min-height: 186px;
      border-radius: 14px;
      border: 1px solid rgba(15, 23, 42, 0.08);
      background: rgba(255, 255, 255, 0.74);
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
      transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease, background 0.22s ease;
      overflow: hidden;

      &::after {
        content: '';
        position: absolute;
        inset: 0;
        pointer-events: none;
        background: radial-gradient(circle at var(--card-glow-x, 18%) var(--card-glow-y, 22%), rgba(124, 224, 255, 0.2), transparent 46%);
        opacity: 0;
        transition: opacity 0.22s ease;
      }

      &:hover {
        border-color: rgba(102, 126, 234, 0.26);
        background: rgba(255, 255, 255, 0.9);
        box-shadow: 0 14px 30px rgba(15, 23, 42, 0.12);

        &::after {
          opacity: 1;
        }
      }

      .icon-badge {
        width: 44px;
        height: 44px;
        border-radius: 14px;
        display: grid;
        place-items: center;
        flex: 0 0 auto;
        background: linear-gradient(135deg, rgba(102, 126, 234, 0.22), rgba(124, 224, 255, 0.18));
        border: 1px solid rgba(102, 126, 234, 0.22);
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);

        .el-icon {
          font-size: 22px;
          color: rgba(15, 23, 42, 0.8);
        }
      }

      .card-body {
        position: relative;
        z-index: 1;
        flex: 1;
        display: flex;
        flex-direction: column;

        h3 {
          margin: 1px 0 6px;
          font-size: 15px;
          color: #0f172a;
          letter-spacing: 0.2px;
        }

        p {
          margin: 0 0 10px;
          color: rgba(51, 65, 85, 0.9);
          font-size: 12.5px;
          line-height: 1.65;
          flex: 1;
        }

        .meta {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-top: auto;
          color: rgba(51, 65, 85, 0.72);
          font-size: 12px;

          .meta-dot {
            width: 6px;
            height: 6px;
            border-radius: 999px;
            background: rgba(102, 126, 234, 0.62);
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.12);
          }
        }
      }
    }
  }
}

@keyframes heroBgDrift {
  0% {
    transform: translate3d(0, 0, 0) scale(1.02);
    filter: saturate(1) brightness(1);
  }
  100% {
    transform: translate3d(0, -6px, 0) scale(1.05);
    filter: saturate(1.08) brightness(1.05);
  }
}

@keyframes heroContentFadeIn {
  0% {
    opacity: 0;
    transform: translateY(12px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes heroTitleGlow {
  0% {
    text-shadow: 0 4px 18px rgba(0, 0, 0, 0.35);
  }
  100% {
    text-shadow: 0 8px 26px rgba(76, 194, 255, 0.28), 0 4px 18px rgba(0, 0, 0, 0.35);
  }
}

@keyframes heroSubtitleBreath {
  0% {
    opacity: 0.88;
    transform: translateY(0);
  }
  100% {
    opacity: 1;
    transform: translateY(-1px);
  }
}

@keyframes heroButtonRise {
  0% {
    opacity: 0;
    transform: translateY(10px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes heroFlow {
  0% {
    transform: translateX(-110%);
  }
  100% {
    transform: translateX(120%);
  }
}

@keyframes beamSweepA {
  0%, 100% { transform: translate3d(0, -30px, 0) rotate(18deg); opacity: 0.45; }
  50% { transform: translate3d(80px, 240px, 0) rotate(18deg); opacity: 0.95; }
}

@keyframes beamSweepB {
  0%, 100% { transform: translate3d(0, -26px, 0) rotate(-13deg); opacity: 0.32; }
  50% { transform: translate3d(-72px, 230px, 0) rotate(-13deg); opacity: 0.9; }
}

@keyframes beamSweepC {
  0%, 100% { transform: translate3d(0, -16px, 0) rotate(11deg); opacity: 0.36; }
  50% { transform: translate3d(58px, 216px, 0) rotate(11deg); opacity: 0.88; }
}

@keyframes particleFloat {
  0% {
    transform: translate3d(0, 0, 0) scale(0.86);
    opacity: 0.16;
  }
  30% {
    opacity: 0.95;
  }
  100% {
    transform: translate3d(0, -240px, 0) scale(1.08);
    opacity: 0;
  }
}

@keyframes buttonShine {
  0%, 22% {
    left: -130%;
    opacity: 0;
  }
  42% {
    opacity: 0.95;
  }
  62%, 100% {
    left: 145%;
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-container {
    .hero-section {
      .hero-bg-layer {
        animation: none;
      }

      .hero-flow-layer {
        animation: none;
      }

      .hero-beam-layer .beam,
      .hero-particle-layer .particle {
        animation: none;
      }

      .hero-pointer-glow {
        display: none;
      }
    }

    .hero-content {
      animation: none;

      h1,
      p {
        animation: none;
      }

      .hero-buttons .el-button {
        animation: none !important;
      }

      .hero-buttons .hero-cta::after {
        animation: none;
      }
    }

    .feature-card {
      transform: none !important;
    }
  }
}
</style>
