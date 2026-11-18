<template>
  <div class="comment-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>{{ t('commentManageTitle') }}</span>
          <div class="filter-bar">
            <el-select
              v-model="searchType"
              style="width: 140px; margin-right: 10px;"
              @change="handleSearchTypeChange"
            >
              <el-option :label="t('searchByContent')" value="content" />
              <el-option :label="t('searchByUser')" value="username" />
            </el-select>
            <el-input
              v-model="keyword"
              :placeholder="searchType === 'username' ? t('usernameKeyword') : t('commentKeyword')"
              style="width: 260px; margin-right: 10px;"
              clearable
              @keyup.enter="loadComments"
            />
            <el-button type="primary" @click="loadComments">{{ t('search') }}</el-button>
          </div>
        </div>
      </template>

      <el-table :data="comments" class="admin-comment-table" style="width: 100%" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="t('commentUser')" width="120" />
        <el-table-column prop="modelName" :label="t('relatedModel')" width="150" />
        <el-table-column prop="content" :label="t('commentContent')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="likeCount" :label="t('likeCount')" width="100" />
        <el-table-column prop="parentId" :label="t('type')" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.parentId" class="type-tag" size="small" type="info">{{ t('reply') }}</el-tag>
            <el-tag v-else class="type-tag" size="small" type="success">{{ t('topLevelComment') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('commentTime')" width="180" />
        <el-table-column :label="t('actions')" width="150" fixed="right">
          <template #default="scope">
            <el-button size="small" type="danger" @click="deleteComment(scope.row)">{{ t('delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="batch-actions" v-if="selectedComments.length > 0">
        <el-button type="danger" @click="batchDelete">{{ t('batchDelete') }} ({{ selectedComments.length }})</el-button>
      </div>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadComments"
        style="margin-top: 20px; justify-content: center;"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUiPreferences } from '@/composables/useUiPreferences'

const loading = ref(false)
const comments = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const searchType = ref('content')
const selectedComments = ref([])
const { t } = useUiPreferences()

onMounted(() => {
  loadComments()
})

const loadComments = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (keyword.value) {
      params.keyword = keyword.value
      params.searchType = searchType.value
    }

    const response = await request.get('/admin/comments', { params })
    comments.value = response.data.data?.records || []
    total.value = response.data.data?.total || 0
  } catch (error) {
    console.error('[CommentManage] 加载评论列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearchTypeChange = () => {
  currentPage.value = 1
  loadComments()
}

const handleSelectionChange = (selection) => {
  selectedComments.value = selection
}

const deleteComment = async (comment) => {
  try {
    await ElMessageBox.confirm(t('confirmDeleteComment'), t('commentManageTitle'), {
      confirmButtonText: t('confirm'),
      cancelButtonText: t('cancel'),
      type: 'warning'
    })

    await request.delete(`/admin/comments/${comment.id}`)
    ElMessage.success(t('deleteSuccess'))
    loadComments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[CommentManage] 删除评论失败', error)
    }
  }
}

const batchDelete = async () => {
  if (selectedComments.value.length === 0) return

  try {
    const message = t('confirmDeleteSelected').replace('{count}', selectedComments.value.length)
    await ElMessageBox.confirm(message, t('commentManageTitle'), {
      confirmButtonText: t('confirm'),
      cancelButtonText: t('cancel'),
      type: 'warning'
    })

    const ids = selectedComments.value.map(c => c.id).join(',')
    await request.delete(`/admin/comments/batch?ids=${ids}`)
    ElMessage.success(t('batchDeleteSuccess'))
    selectedComments.value = []
    loadComments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[CommentManage] 批量删除评论失败', error)
    }
  }
}
</script>

<style scoped>
.comment-manage {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .filter-bar {
      display: flex;
      align-items: center;
    }
  }

  .batch-actions {
    margin-top: 20px;
    padding: 10px;
    background: var(--v2-surface-soft);
    border-radius: 4px;
  }

  /*
    表格配色跟随主题。
    原先把表格写死成白底深字（修深色下的发白），
    结果深色主题里整张表变成刺眼白块；改用语义 token 后两套主题都成立。
  */
  :deep(.admin-comment-table) {
    --cm-table-bg: var(--v2-surface-soft);
    --cm-table-bg-alt: var(--v2-surface-hover);
    --cm-table-border: var(--v2-divider);
    --cm-table-text: var(--v2-text-body);

    .el-table {
      background: transparent !important;
      color: var(--cm-table-text) !important;
      border-radius: 14px;
      overflow: hidden;
    }

    .el-table__inner-wrapper::before {
      background-color: transparent !important;
    }

    .el-table__header-wrapper th.el-table__cell {
      background: var(--cm-table-bg) !important;
      color: var(--v2-text-strong) !important;
      font-weight: 800;
      border-bottom: 1px solid var(--cm-table-border) !important;
    }

    .el-table__body-wrapper td.el-table__cell {
      background: transparent !important;
      color: var(--cm-table-text) !important;
      border-bottom: 1px solid var(--cm-table-border) !important;
    }

    .el-table__body tr.el-table__row--striped td.el-table__cell {
      background: var(--cm-table-bg-alt) !important;
    }

    .cell {
      color: var(--cm-table-text) !important;
    }

    /* 类型标签：默认走主题语义色（一级评论/回复都用低饱和胶囊，深色下不刺眼） */
    .type-tag.el-tag {
      border-radius: 999px;
      padding: 0 10px;
      height: 24px;
      line-height: 22px;
      font-weight: 800;
      letter-spacing: 0.2px;
      border: 1px solid var(--v2-border-soft) !important;
      background: var(--v2-surface-soft) !important;
      color: var(--v2-text-body) !important;
    }

    .type-tag.el-tag--info {
      border-color: var(--v2-info-border) !important;
      background: var(--v2-info-bg) !important;
      color: var(--v2-info-text) !important;
    }

    .type-tag.el-tag--success {
      border-color: var(--v2-ok-border) !important;
      background: var(--v2-ok-bg) !important;
      color: var(--v2-ok-text) !important;
    }
  }
}
</style>
