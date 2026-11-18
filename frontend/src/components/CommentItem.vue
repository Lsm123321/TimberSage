<template>
  <div class="comment-item" :class="{ 'is-reply': isReply }">
    <div class="comment-main">
      <el-avatar :size="isReply ? 28 : 40" :src="comment.userAvatar">
        {{ comment.username?.charAt(0) || 'U' }}
      </el-avatar>

      <div class="comment-body">
        <div class="comment-header">
          <span class="username">{{ comment.username }}</span>
          <el-tag v-if="comment.userRole === 'admin'" size="small" type="danger">管理员</el-tag>
          <el-tag v-else-if="comment.userRole === 'contributor'" size="small" type="success">贡献者</el-tag>
          <span class="time">{{ formatTime(comment.createdAt) }}</span>
        </div>

        <div class="comment-content">{{ comment.content }}</div>

        <div class="comment-actions">
          <span class="action-btn" @click="handleLike">
            <el-icon :class="{ liked: comment.isLiked }"><Pointer /></el-icon>
            {{ comment.likeCount || 0 }}
          </span>
          <span class="action-btn" @click="handleReply" v-if="!isReply">
            <el-icon><ChatLineRound /></el-icon>
            回复
          </span>
          <span class="action-btn delete" @click="handleDelete" v-if="canDelete">
            <el-icon><Delete /></el-icon>
            删除
          </span>
        </div>

        <div class="reply-input" v-if="showReplyInput && !isReply">
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="2"
            :placeholder="`回复 @${comment.username}...`"
          />
          <div class="reply-actions">
            <el-button size="small" @click="cancelReply">取消</el-button>
            <el-button size="small" type="primary" @click="submitReply" :loading="submitting">发送</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="comment-replies" v-if="!isReply && comment.replies?.length > 0">
      <div
        v-for="reply in comment.replies"
        :key="reply.id"
        class="reply-wrapper"
      >
        <CommentItem
          :comment="reply"
          :is-reply="true"
          @reply="handleReplyToReply"
          @delete="handleDeleteReply"
          @like="handleLikeReply"
        />
      </div>
      <div class="load-more-replies" v-if="comment.hasMoreReplies" @click="loadMoreReplies">
        加载更多回复
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Pointer, ChatLineRound, Delete } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  comment: {
    type: Object,
    required: true
  },
  isReply: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['like', 'reply', 'delete', 'loadMore'])

const authStore = useAuthStore()
const replyContent = ref('')
const showReplyInput = ref(false)
const submitting = ref(false)

const canDelete = computed(() => {
  if (!authStore.userInfo) return false
  return authStore.userInfo.id === props.comment.userId ||
         authStore.userInfo.role === 'admin'
})

const handleLike = () => {
  emit('like', props.comment.id)
}

const handleReply = () => {
  showReplyInput.value = !showReplyInput.value
}

const handleReplyToReply = (parentId) => {
  emit('reply', parentId)
}

const cancelReply = () => {
  showReplyInput.value = false
  replyContent.value = ''
}

const submitReply = async () => {
  if (!replyContent.value.trim()) return

  emit('reply', {
    parentId: props.comment.id,
    content: replyContent.value.trim()
  })

  replyContent.value = ''
  showReplyInput.value = false
}

const handleDelete = () => {
  emit('delete', props.comment.id)
}

const handleDeleteReply = (id) => {
  emit('delete', id)
}

const handleLikeReply = (id) => {
  emit('like', id)
}

const loadMoreReplies = () => {
  emit('loadMore', props.comment.id)
}

const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return `${date.getMonth() + 1}-${date.getDate()}`
}
</script>

<style scoped>
.comment-item {
  padding: 15px 0;
  border-bottom: 1px solid #f0f0f0;

  &:last-child {
    border-bottom: none;
  }

  &.is-reply {
    padding: 10px 0;
    border-bottom: none;
  }

  .comment-main {
    display: flex;
    gap: 12px;

    .comment-body {
      flex: 1;
      min-width: 0;

      .comment-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 6px;

        .username {
          font-weight: 500;
          color: var(--v2-text-strong);
          font-size: 14px;
        }

        .time {
          color: var(--v2-text-weak);
          font-size: 12px;
        }
      }

      .comment-content {
        color: var(--v2-text-body);
        font-size: 14px;
        line-height: 1.6;
        margin-bottom: 8px;
        word-break: break-word;
      }

      .comment-actions {
        display: flex;
        gap: 20px;

        .action-btn {
          display: flex;
          align-items: center;
          gap: 4px;
          color: var(--v2-text-weak);
          font-size: 13px;
          cursor: pointer;
          transition: color 0.2s;

          &:hover {
            color: var(--v2-gold);
          }

          &.liked {
            color: #ff6b6b;
          }

          &.delete:hover {
            color: #ff4949;
          }
        }
      }

      .reply-input {
        margin-top: 10px;

        .reply-actions {
          display: flex;
          justify-content: flex-end;
          gap: 10px;
          margin-top: 10px;
        }
      }
    }
  }

  .comment-replies {
    margin-left: 52px;
    padding-left: 15px;
    border-left: 2px solid var(--v2-divider);
    margin-top: 10px;

    .reply-wrapper {
      padding: 10px 0;
    }

    .load-more-replies {
      padding: 10px 0;
      color: var(--v2-gold);
      font-size: 13px;
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>
