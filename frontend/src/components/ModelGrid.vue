<template>
  <div class="model-grid">
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="span" v-for="model in models" :key="model.id">
        <div
          class="model-card"
          :style="cardStyles[model.id]"
          @click="goToDetail(model.id)"
          @pointermove="(event) => handleCardPointerMove(model.id, event)"
          @pointerleave="() => resetCardPointer(model.id)"
        >
          <div class="card-image">
            <img :src="model.thumbnailPath || defaultImage" :alt="model.modelName" />
            <div class="card-overlay">
              <span class="view-btn btn-shine">{{ t('viewDetail') }}</span>
            </div>
            <div class="card-badges">
              <el-tag v-if="model.categoryName" size="small" type="info">{{ model.categoryName }}</el-tag>
            </div>
          </div>
          <div class="card-content">
            <h3 class="model-name">{{ model.modelName }}</h3>
            <p class="dynasty" v-if="model.dynasty">{{ model.dynasty }}</p>
            <p class="location" v-if="model.location">{{ model.location }}</p>
            <div class="card-stats">
              <span><el-icon><View /></el-icon> {{ model.viewCount || 0 }}</span>
              <span
                class="stat-action"
                :class="{ active: !!model.isFavorite }"
                @click.stop="handleToggleFavorite(model)"
              >
                <el-icon><Star /></el-icon> {{ model.favoriteCount ?? 0 }}
              </span>
              <span
                class="stat-action"
                :class="{ active: !!model.isLiked }"
                @click.stop="handleToggleLike(model)"
              >
                <el-icon><CaretTop /></el-icon> {{ model.likeCount || 0 }}
              </span>
              <span><el-icon><Download /></el-icon> {{ model.downloadCount || 0 }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div v-if="!loading && models.length === 0" class="empty-state">
      <el-empty :description="emptyText" />
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { View, Star, CaretTop, Download } from '@element-plus/icons-vue'
import { useModelStore } from '@/stores/model'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { useUiPreferences } from '@/composables/useUiPreferences'

defineProps({
  models: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  span: {
    type: Number,
    default: 6
  },
  emptyText: {
    type: String,
    default: '暂无数据'
  }
})

const router = useRouter()
const modelStore = useModelStore()
const authStore = useAuthStore()
const { t } = useUiPreferences()
const defaultImage = 'https://via.placeholder.com/300x200?text=No+Image'
const cardStyles = reactive({})

const goToDetail = (id) => {
  router.push(`/buildings/${id}`)
}

const ensureLoggedIn = () => {
  if (authStore.token) return true
  ElMessage.warning('请先登录后再操作')
  router.push('/login')
  return false
}

const handleToggleLike = async (model) => {
  if (!ensureLoggedIn()) return
  const prevLiked = !!model.isLiked
  const prevCount = Number(model.likeCount) || 0
  model.isLiked = !prevLiked
  model.likeCount = Math.max(0, prevCount + (model.isLiked ? 1 : -1))
  const result = await modelStore.toggleLike(model.id)
  if (!result?.success) {
    model.isLiked = prevLiked
    model.likeCount = prevCount
    ElMessage.error(result?.message || '点赞失败')
  }
}

const handleToggleFavorite = async (model) => {
  if (!ensureLoggedIn()) return
  const prevFavorite = !!model.isFavorite
  const prevCount = Number(model.favoriteCount) || 0
  model.isFavorite = !prevFavorite
  model.favoriteCount = Math.max(0, prevCount + (model.isFavorite ? 1 : -1))
  try {
    if (model.isFavorite) {
      await modelStore.addFavorite(model.id)
      ElMessage.success('已收藏')
    } else {
      await modelStore.removeFavorite(model.id)
      ElMessage.success('已取消收藏')
    }
  } catch (error) {
    model.isFavorite = prevFavorite
    model.favoriteCount = prevCount
    ElMessage.error('收藏操作失败')
  }
}

const handleCardPointerMove = (id, event) => {
  const card = event.currentTarget
  if (!card) return
  const rect = card.getBoundingClientRect()
  const rx = (event.clientX - rect.left - rect.width / 2) / rect.width
  const ry = (event.clientY - rect.top - rect.height / 2) / rect.height
  const rotateY = (rx * 7.5).toFixed(2)
  const rotateX = (-ry * 7.5).toFixed(2)
  const shiftX = (rx * 9).toFixed(2)
  const shiftY = (ry * 9).toFixed(2)
  cardStyles[id] = {
    transform: `perspective(880px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translate3d(${shiftX}px, ${shiftY}px, 0)`,
    '--card-glow-x': `${((event.clientX - rect.left) / rect.width) * 100}%`,
    '--card-glow-y': `${((event.clientY - rect.top) / rect.height) * 100}%`,
    '--img-shift-x': `${(rx * 6).toFixed(2)}px`,
    '--img-shift-y': `${(ry * 6).toFixed(2)}px`
  }
}

const resetCardPointer = (id) => {
  cardStyles[id] = {
    transform: 'perspective(880px) rotateX(0deg) rotateY(0deg) translate3d(0, 0, 0)',
    '--card-glow-x': '50%',
    '--card-glow-y': '30%',
    '--img-shift-x': '0px',
    '--img-shift-y': '0px'
  }
}
</script>

<style lang="scss" scoped>
.model-grid {
  .el-col {
    perspective: 1000px;
  }

  .model-card {
    --card-glow-x: 50%;
    --card-glow-y: 30%;
    --img-shift-x: 0px;
    --img-shift-y: 0px;
    position: relative;
    background:
      radial-gradient(circle at 10% 10%, rgba(255, 255, 255, 0.6), transparent 42%),
      linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.82));
    border-radius: 14px;
    border: 1px solid rgba(30, 41, 59, 0.12);
    overflow: hidden;
    cursor: pointer;
    transition:
      transform 180ms ease,
      box-shadow 220ms ease,
      border-color 220ms ease,
      background 220ms ease;
    margin-bottom: 20px;
    transform-style: preserve-3d;
    box-shadow:
      0 10px 24px rgba(15, 23, 42, 0.1),
      inset 0 1px 0 rgba(255, 255, 255, 0.75);

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      pointer-events: none;
      background: radial-gradient(circle at var(--card-glow-x) var(--card-glow-y), rgba(120, 205, 255, 0.2), rgba(120, 205, 255, 0) 42%);
      opacity: 0;
      transition: opacity 180ms ease;
      z-index: 1;
    }

    &:hover {
      border-color: rgba(102, 126, 234, 0.28);
      background:
        radial-gradient(circle at 10% 10%, rgba(255, 255, 255, 0.7), transparent 42%),
        linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(255, 255, 255, 0.86));
      box-shadow: 0 18px 36px rgba(15, 23, 42, 0.16);

      .card-overlay {
        opacity: 1;
      }

      &::before {
        opacity: 1;
      }
    }

    .card-image {
      position: relative;
      height: 180px;
      overflow: hidden;
      transform: translateZ(16px);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        transform: translate3d(var(--img-shift-x), var(--img-shift-y), 0) scale(1.05);
        transition: transform 180ms ease;
      }

      .card-overlay {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background:
          radial-gradient(circle at 50% 42%, rgba(122, 221, 255, 0.2), rgba(122, 221, 255, 0)),
          rgba(4, 8, 18, 0.52);
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 180ms ease;
        z-index: 2;

        .view-btn {
          color: white;
          border: 1px solid rgba(255, 255, 255, 0.88);
          background: rgba(255, 255, 255, 0.12);
          backdrop-filter: blur(5px);
          padding: 8px 20px;
          border-radius: 999px;
          font-weight: 700;
          letter-spacing: 0.3px;
          position: relative;
          overflow: hidden;
        }
      }

      .card-badges {
        position: absolute;
        top: 10px;
        left: 10px;
      }
    }

    .card-content {
      padding: 15px;
      position: relative;
      z-index: 2;
      transform: translateZ(12px);

      .model-name {
        font-size: 16px;
        color: #0f172a;
        margin-bottom: 8px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .dynasty {
        color: #5b6cd7;
        font-size: 14px;
        margin-bottom: 5px;
      }

      .location {
        color: #64748b;
        font-size: 12px;
        margin-bottom: 10px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .card-stats {
        display: flex;
        gap: 15px;
        color: #64748b;
        font-size: 12px;

        span {
          display: flex;
          align-items: center;
          gap: 3px;
        }

        .stat-action {
          cursor: pointer;
          transition: color 0.2s ease;

          &:hover {
            color: #5b6cd7;
          }

          &.active {
            color: #5b6cd7;
          }
        }
      }
    }
  }

  .empty-state {
    padding: 60px 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .model-grid {
    .model-card {
      transform: none !important;
      transition: box-shadow 180ms ease, border-color 180ms ease;

      &::before {
        display: none;
      }

      .card-image img {
        transform: none !important;
      }
    }
  }
}
</style>
