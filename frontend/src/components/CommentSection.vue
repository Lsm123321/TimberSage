<template>
  <div class="comment-section">
    <div class="section-header">
      <h3>评论 <span class="count">({{ total }})</span></h3>
      <el-select v-model="sortBy" size="small" popper-class="comment-sort-popper" @change="handleSortChange">
        <el-option label="最新发布" value="createdAt" />
        <el-option label="最热评论" value="likeCount" />
      </el-select>
    </div>

    <div class="comment-input-section">
      <el-avatar :size="40" :src="userAvatar">
        {{ username?.charAt(0) || 'U' }}
      </el-avatar>
      <div class="input-wrapper">
        <el-input
          v-model="newComment"
          type="textarea"
          :rows="3"
          placeholder="发表看法..."
          maxlength="500"
          show-word-limit
        />
        <div class="input-actions">
          <el-button type="primary" size="small" @click="submitComment" :loading="submitting">
            发表评论
          </el-button>
        </div>
      </div>
    </div>

    <div class="comment-list">
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="comments.length === 0" class="empty-state">
        <el-empty description="暂无评论，来说两句吧~" />
      </div>

      <template v-else>
        <CommentItem
          v-for="comment in comments"
          :key="comment.id"
          :comment="comment"
          @like="handleCommentLike"
          @reply="handleReply"
          @delete="handleDelete"
          @loadMore="loadMoreReplies"
        />

        <div class="load-more" v-if="hasMore" @click="loadMoreComments">
          加载更多评论
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { openLoginGuide } from '@/composables/useLoginGuide'
import CommentItem from './CommentItem.vue'
import { Loading } from '@element-plus/icons-vue'

const props = defineProps({
  modelId: {
    type: Number,
    required: true
  }
})

const authStore = useAuthStore()

const comments = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const newComment = ref('')
const sortBy = ref('createdAt')
const page = ref(1)
const pageSize = ref(10)
const hasMore = ref(false)

const userAvatar = computed(() => authStore.userInfo?.avatar || '')
const username = computed(() => authStore.userInfo?.username || '')
const isLoggedIn = computed(() => !!authStore.token)
const authHeaders = computed(() => (
  authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {}
))

onMounted(() => {
  loadComments()
})

watch(() => props.modelId, () => {
  comments.value = []
  page.value = 1
  loadComments()
})

const loadComments = async (isLoadMore = false) => {
  loading.value = true
  try {
    const response = await fetch(
      `/api/comments?modelId=${props.modelId}&page=${page.value}&pageSize=${pageSize.value}&sortBy=${sortBy.value}`,
      { headers: authHeaders.value }
    )
    if (!response.ok) {
      throw new Error(`加载评论失败: ${response.status}`)
    }
    const data = await response.json()
    const records = data?.records || data?.content || []
    const totalCount = data?.total || data?.totalElements || 0
    if (isLoadMore) {
      comments.value = [...comments.value, ...records]
    } else {
      comments.value = records
    }
    total.value = totalCount
    hasMore.value = comments.value.length < total.value
  } catch (error) {
    console.error('[CommentSection] 加载评论失败', error)
  } finally {
    loading.value = false
  }
}

const loadMoreComments = () => {
  page.value++
  loadComments(true)
}

const loadMoreReplies = async (parentId) => {
  try {
    const response = await fetch(
      `/api/comments/${parentId}/replies?page=1&pageSize=20&sortBy=${sortBy.value}`,
      { headers: authHeaders.value }
    )
    if (!response.ok) {
      throw new Error(`加载回复失败: ${response.status}`)
    }
    const data = await response.json()
    const parentComment = comments.value.find(c => c.id === parentId)
    if (parentComment) {
      parentComment.replies = data?.records || data?.content || []
      parentComment.hasMoreReplies = false
    }
  } catch (error) {
    console.error('[CommentSection] 加载回复失败', error)
  }
}

const handleSortChange = () => {
  page.value = 1
  loadComments()
}

const submitComment = async () => {
  if (!newComment.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }

  if (!isLoggedIn.value) {
    openLoginGuide('评论')
    return
  }

  submitting.value = true
  try {
    const response = await fetch('/api/comments', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify({
        modelId: props.modelId,
        content: newComment.value.trim()
      })
    })

    const data = await response.json()
    if (response.ok && data?.id) {
      ElMessage.success('评论成功')
      newComment.value = ''
      page.value = 1
      await loadComments()
    } else {
      ElMessage.error(data?.message || '评论失败')
    }
  } catch (error) {
    console.error('[CommentSection] 发表评论失败', error)
    ElMessage.error('评论失败')
  } finally {
    submitting.value = false
  }
}

const handleCommentLike = async (commentId) => {
  if (!isLoggedIn.value) {
    openLoginGuide('点赞')
    return
  }

  try {
    await fetch(`/api/comments/${commentId}/like`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })

    const comment = findCommentById(commentId)
    if (comment) {
      comment.isLiked = !comment.isLiked
      comment.likeCount = (comment.likeCount || 0) + (comment.isLiked ? 1 : -1)
    }
  } catch (error) {
    console.error('[CommentSection] 点赞失败', error)
    ElMessage.error('操作失败')
  }
}

const handleReply = async ({ parentId, content }) => {
  if (!isLoggedIn.value) {
    openLoginGuide('评论')
    return
  }

  try {
    const response = await fetch('/api/comments', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify({
        modelId: props.modelId,
        parentId,
        content
      })
    })

    const data = await response.json()
    if (response.ok && data?.id) {
      ElMessage.success('回复成功')
      await loadComments()
    } else {
      ElMessage.error(data?.message || '回复失败')
    }
  } catch (error) {
    console.error('[CommentSection] 回复失败', error)
    ElMessage.error('回复失败')
  }
}

const handleDelete = async (commentId) => {
  try {
    await fetch(`/api/comments/${commentId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })

    ElMessage.success('删除成功')
    comments.value = comments.value.filter(c => c.id !== commentId)
    total.value--
  } catch (error) {
    console.error('[CommentSection] 删除失败', error)
    ElMessage.error('删除失败')
  }
}

const findCommentById = (id) => {
  for (const comment of comments.value) {
    if (comment.id === id) return comment
    if (comment.replies) {
      for (const reply of comment.replies) {
        if (reply.id === id) return reply
      }
    }
  }
  return null
}
</script>

<style scoped>
.comment-section {
  background: var(--v2-surface-strong);
  border: 1px solid var(--v2-border-soft);
  border-radius: var(--v2-radius-lg);
  padding: 20px;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 15px;
    border-bottom: 1px solid var(--v2-divider);

    h3 {
      margin: 0;
      font-size: 18px;
      color: var(--v2-text-strong);

      .count {
        color: var(--v2-text-weak);
        font-weight: normal;
        font-size: 14px;
      }
    }
  }

  .comment-input-section {
    display: flex;
    gap: 15px;
    margin-bottom: 25px;

    .input-wrapper {
      flex: 1;

      .input-actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 10px;
      }
    }
  }

  .comment-list {
    .loading-state {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 40px 0;
      color: var(--v2-text-weak);

      .el-icon {
        margin-right: 10px;
      }
    }

    .empty-state {
      padding: 40px 0;
    }

    .load-more {
      text-align: center;
      padding: 15px 0;
      color: var(--v2-gold);
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  /*
    输入框显式引用主题 token。
    原来这里为了压住全局的浅色文字写死了白底深字，
    浅色主题没问题，深色主题却会冒出白块，现在两个主题都跟着走。
  */
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    background: var(--v2-surface-input) !important;
    box-shadow: 0 0 0 1px var(--v2-border-soft) inset !important;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner),
  :deep(.el-select__placeholder),
  :deep(.el-select__selected-item) {
    color: var(--v2-text-body) !important;
  }

  :deep(.el-input__inner::placeholder),
  :deep(.el-textarea__inner::placeholder) {
    color: var(--v2-text-weak) !important;
  }

  :deep(.el-input__count-inner) {
    color: var(--v2-text-weak) !important;
  }
}
</style>

<style>
/*
  下拉是 teleport 到 body 上的，只能用全局作用域；
  颜色同样走主题变量，否则深浅切换后会留一块白底。
*/
.comment-sort-popper.el-popper {
  border: 1px solid var(--v2-border-soft) !important;
  background: var(--v2-surface-strong) !important;
  box-shadow: var(--v2-shadow) !important;
}

.comment-sort-popper .el-select-dropdown__item {
  color: var(--v2-text-body) !important;
  font-weight: 600;
}

.comment-sort-popper .el-select-dropdown__item.is-selected {
  color: var(--v2-chip-text) !important;
  background: var(--v2-chip-bg) !important;
}
</style>
