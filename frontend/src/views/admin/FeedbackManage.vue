<template>
  <div class="feedback-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>模型纠错反馈</span>
          <el-select v-model="modelStatus" placeholder="筛选状态" clearable @change="loadModelFeedbacks">
            <el-option label="全部" value="" />
            <el-option label="待处理" :value="0" />
            <el-option label="已采纳" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </div>
      </template>

      <el-table :data="modelFeedbacks" class="admin-feedback-table" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="modelName" label="模型名称" width="150" />
        <el-table-column prop="modelId" label="模型ID" width="100" />
        <el-table-column prop="username" label="提交用户" width="120" />
        <el-table-column prop="errorType" label="错误类型" width="120" />
        <el-table-column prop="description" label="错误描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag class="status-tag" :type="getModelStatusType(scope.row.status)">
              {{ getModelStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="viewModelFeedback(scope.row)">查看</el-button>
            <el-button size="small" type="primary" plain @click="goToModel(scope.row)">前往模型</el-button>
            <template v-if="scope.row.status === 0">
              <el-button size="small" type="success" @click="adoptModelFeedback(scope.row)">采纳</el-button>
              <el-button size="small" type="danger" @click="rejectModelFeedback(scope.row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-if="modelTotal > 0"
          background
          layout="total, prev, pager, next"
          :total="modelTotal"
          :page-size="modelPageSize"
          :current-page="modelPage"
          @current-change="handleModelPageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="dialogTitle"
      width="600px"
    >
      <div class="feedback-detail" v-if="currentFeedback">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="反馈用户">{{ currentFeedback.username }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentFeedback.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="错误类型" v-if="currentFeedback.errorType">
            {{ currentFeedback.errorType }}
          </el-descriptions-item>
          <el-descriptions-item label="模型名称" v-if="currentFeedback.modelName" :span="2">
            {{ currentFeedback.modelName }}
          </el-descriptions-item>
          <el-descriptions-item label="模型ID" v-if="currentFeedback.modelId">
            {{ currentFeedback.modelId }}
          </el-descriptions-item>
          <el-descriptions-item label="反馈内容" :span="2">
            {{ currentFeedback.content || currentFeedback.description }}
          </el-descriptions-item>
          <el-descriptions-item label="附件" v-if="currentFeedback.attachments" :span="2">
            <a :href="attachment" target="_blank" v-for="(attachment, idx) in parseAttachments(currentFeedback.attachments)" :key="idx">
              附件{{ idx + 1 }}
            </a>
          </el-descriptions-item>
          <el-descriptions-item label="处理备注" :span="2" v-if="currentFeedback.adminRemark">
            {{ currentFeedback.adminRemark }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="remark-input" v-if="currentFeedback.status === 0">
          <h4>处理备注</h4>
          <el-input
            v-model="adminRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入处理备注"
          />
        </div>
      </div>
      <template #footer v-if="currentFeedback && currentFeedback.status === 0">
        <el-button
          type="primary"
          plain
          @click="goToModel(currentFeedback)"
        >
          前往模型
        </el-button>
        <el-button @click="detailVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const modelFeedbacks = ref([])
const modelStatus = ref('')
const modelPage = ref(1)
const modelPageSize = ref(10)
const modelTotal = ref(0)
const detailVisible = ref(false)
const currentFeedback = ref(null)
const dialogTitle = ref('')
const adminRemark = ref('')

onMounted(() => {
  loadModelFeedbacks()
})

const loadModelFeedbacks = async () => {
  loading.value = true
  try {
    const params = { page: modelPage.value, pageSize: modelPageSize.value }
    if (modelStatus.value !== '') {
      params.status = modelStatus.value
    }
    const response = await request.get('/admin/model-feedback', { params })
    const pageData = response.data
    modelFeedbacks.value = pageData?.content || []
    modelTotal.value = pageData?.totalElements || 0
  } catch (error) {
    console.error('[FeedbackManage] 加载纠错反馈失败', error)
  } finally {
    loading.value = false
  }
}

const handleModelPageChange = (p) => {
  modelPage.value = p
  loadModelFeedbacks()
}

const getModelStatusType = (status) => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const getModelStatusText = (status) => {
  switch (status) {
    case 0: return '待处理'
    case 1: return '已采纳'
    case 2: return '已驳回'
    default: return '未知'
  }
}

const viewModelFeedback = (feedback) => {
  currentFeedback.value = feedback
  dialogTitle.value = '模型纠错反馈详情'
  adminRemark.value = ''
  detailVisible.value = true
}

const adoptModelFeedback = async (feedback) => {
  try {
    await request.put(`/admin/model-feedback/${feedback.id}`, { status: 1, adminRemark: adminRemark.value || '' })
    ElMessage.success('已采纳')
    loadModelFeedbacks()
  } catch (error) {
    console.error('[FeedbackManage] 采纳反馈失败', error)
    ElMessage.error('操作失败')
  }
}

const rejectModelFeedback = async (feedback) => {
  try {
    await request.put(`/admin/model-feedback/${feedback.id}`, { status: 2, adminRemark: adminRemark.value || '已驳回' })
    ElMessage.success('已驳回')
    loadModelFeedbacks()
  } catch (error) {
    console.error('[FeedbackManage] 驳回反馈失败', error)
    ElMessage.error('操作失败')
  }
}

const submitFeedback = async () => {
  if (!currentFeedback.value) return

  const url = `/admin/model-feedback/${currentFeedback.value.id}`

  try {
    await request.put(url, { status: 1, adminRemark: adminRemark.value })
    ElMessage.success('处理成功')
    detailVisible.value = false
    loadModelFeedbacks()
  } catch (error) {
    console.error('[FeedbackManage] 提交处理结果失败', error)
    ElMessage.error('处理失败')
  }
}

const parseAttachments = (attachments) => {
  if (!attachments) return []
  try {
    return typeof attachments === 'string' ? JSON.parse(attachments) : attachments
  } catch {
    return []
  }
}

const goToModel = (feedback) => {
  const id = Number(feedback?.modelId)
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.warning('该反馈未关联有效模型ID')
    return
  }
  // 跳到模型管理页并带上预览参数，那边会自动打开预览
  router.push({ path: '/admin/models', query: { previewModelId: String(id) } })
}
</script>

<style scoped>
.feedback-manage {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 14px;
  }

  .feedback-detail {
    .remark-input {
      margin-top: 20px;

      h4 {
        margin-bottom: 10px;
      }
    }
  }

  /* 局部修复：管理端浅底表格里 tag/文字发白问题（被全局 .el-tag 覆盖） */
  :deep(.admin-feedback-table) {
    --fm-table-bg: rgba(255, 255, 255, 0.92);
    --fm-table-bg-alt: rgba(248, 250, 252, 0.92);
    --fm-table-border: rgba(15, 23, 42, 0.10);
    --fm-table-text: #1f2937;

    .el-table {
      background: transparent !important;
      color: var(--fm-table-text) !important;
      border-radius: 14px;
      overflow: hidden;
    }

    .el-table__inner-wrapper::before {
      background-color: transparent !important;
    }

    .el-table__header-wrapper th.el-table__cell {
      background: var(--fm-table-bg) !important;
      color: rgba(15, 23, 42, 0.82) !important;
      font-weight: 800;
      border-bottom: 1px solid var(--fm-table-border) !important;
    }

    .el-table__body-wrapper td.el-table__cell {
      background: var(--fm-table-bg) !important;
      color: var(--fm-table-text) !important;
      border-bottom: 1px solid rgba(15, 23, 42, 0.06) !important;
    }

    .el-table__body tr.el-table__row--striped td.el-table__cell {
      background: var(--fm-table-bg-alt) !important;
    }

    .cell {
      color: var(--fm-table-text) !important;
    }

    .status-tag.el-tag {
      border-radius: 999px;
      padding: 0 10px;
      height: 24px;
      line-height: 22px;
      font-weight: 800;
      letter-spacing: 0.2px;
      border: 1px solid rgba(15, 23, 42, 0.16) !important;
      background: rgba(15, 23, 42, 0.05) !important;
      color: rgba(15, 23, 42, 0.88) !important;
    }
  }
}
</style>
