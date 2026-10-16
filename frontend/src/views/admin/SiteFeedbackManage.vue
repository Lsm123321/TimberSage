<template>
  <div class="admin-page">
    <div class="page-head v2-glass-strong">
      <div class="title">{{ t('adminSiteFeedbackTitle') }}</div>
      <div class="desc">{{ t('adminSiteFeedbackDesc') }}</div>
    </div>

    <el-card class="v2-card" shadow="never">
      <div class="toolbar">
        <el-select v-model="status" class="status-select" :placeholder="t('statusFilter')">
          <el-option :label="t('statusAll')" :value="''" />
          <el-option :label="t('feedbackUnprocessed')" :value="0" />
          <el-option :label="t('feedbackProcessed')" :value="1" />
          <el-option :label="t('feedbackIgnored')" :value="2" />
        </el-select>
        <el-button @click="loadData" :loading="loading">{{ t('refresh') }}</el-button>
      </div>

      <el-table :data="records" stripe class="v2-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" :label="t('feedbackTitle')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" :label="t('feedbackContent')" min-width="240" show-overflow-tooltip />
        <el-table-column prop="username" :label="t('user')" width="140" />
        <el-table-column prop="contact" :label="t('feedbackContact')" width="180" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('status')" width="120">
          <template #default="{ row }">
            <el-tag class="status-tag" :type="statusTagType(row.status)" effect="dark">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('createdAt')" width="180" />
        <el-table-column :label="t('actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openHandle(row)">{{ t('handle') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next, jumper, total"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="handleVisible"
      class="v2-unified-dialog site-feedback-dialog"
      modal-class="v2-unified-overlay"
      :title="t('handleFeedbackTitle')"
      width="560px"
    >
      <div class="handle-body">
        <div class="row">
          <div class="label">{{ t('feedbackTitle') }}</div>
          <div class="value">{{ current?.title }}</div>
        </div>
        <div class="row">
          <div class="label">{{ t('feedbackContent') }}</div>
          <div class="value pre">{{ current?.content }}</div>
        </div>
        <div class="row">
          <div class="label">{{ t('feedbackContact') }}</div>
          <div class="value">{{ current?.contact || '-' }}</div>
        </div>

        <el-form label-width="90px" class="mt-12">
          <el-form-item :label="t('status')">
            <el-select v-model="editStatus" style="width: 220px">
              <el-option :label="t('feedbackUnprocessed')" :value="0" />
              <el-option :label="t('feedbackProcessed')" :value="1" />
              <el-option :label="t('feedbackIgnored')" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('adminReply')">
            <el-input v-model="adminReply" type="textarea" :rows="4" :placeholder="t('adminReplyPlaceholder')" maxlength="5000" show-word-limit />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="handleVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetSiteFeedback, adminUpdateSiteFeedback } from '@/api/siteFeedback'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { t } = useUiPreferences()

const status = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])
const loading = ref(false)

const handleVisible = ref(false)
const current = ref(null)
const editStatus = ref(0)
const adminReply = ref('')
const saving = ref(false)

function statusText(s) {
  if (s === 1) return t('feedbackProcessed')
  if (s === 2) return t('feedbackIgnored')
  return t('feedbackUnprocessed')
}

function statusTagType(s) {
  if (s === 1) return 'success'
  if (s === 2) return 'info'
  return 'warning'
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (status.value !== '') params.status = status.value
    const res = await adminGetSiteFeedback(params)
    const data = res.data?.data
    total.value = data?.total || 0
    records.value = data?.records || []
  } catch (e) {
    console.error('[SiteFeedbackManage] 加载列表失败', e)
  } finally {
    loading.value = false
  }
}

function handlePageChange(p) {
  page.value = p
  loadData()
}

function openHandle(row) {
  current.value = row
  editStatus.value = row.status
  adminReply.value = row.adminReply || ''
  handleVisible.value = true
}

async function save() {
  if (!current.value) return
  saving.value = true
  try {
    await adminUpdateSiteFeedback(current.value.id, { status: editStatus.value, adminReply: adminReply.value })
    ElMessage.success(t('submitSuccess'))
    handleVisible.value = false
    loadData()
  } catch (e) {
    console.error('[SiteFeedbackManage] 保存处理结果失败', e)
  } finally {
    saving.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.admin-page {
  .page-head {
    padding: 14px 16px;
    border-radius: 14px;
    margin-bottom: 12px;
    .title {
      font-size: 16px;
      font-weight: 800;
      color: rgba(255, 255, 255, 0.92);
    }
    .desc {
      margin-top: 6px;
      font-size: 13px;
      color: rgba(255, 255, 255, 0.72);
    }
  }

  .v2-card {
    border-radius: 14px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    background: rgba(255, 255, 255, 0.06);
  }

  .toolbar {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 12px;
  }

  .status-select {
    width: 220px;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }

  .handle-body {
    .row {
      display: grid;
      grid-template-columns: 90px 1fr;
      gap: 10px;
      margin-bottom: 10px;
      .label {
        color: #334155;
        font-weight: 650;
      }
      .value {
        color: #1f2937;
      }
      .pre {
        white-space: pre-wrap;
        word-break: break-word;
        line-height: 1.55;
      }
    }
  }

  .mt-12 {
    margin-top: 12px;
  }

  /* 局部修复：全局浅色文字覆盖导致状态列/筛选框/表格发白看不清 */
  :deep(.v2-table) {
    --sf-table-bg: rgba(255, 255, 255, 0.92);
    --sf-table-bg-alt: rgba(248, 250, 252, 0.92);
    --sf-table-border: rgba(15, 23, 42, 0.10);
    --sf-table-text: #1f2937;

    .el-table {
      background: transparent !important;
      color: var(--sf-table-text) !important;
      border-radius: 14px;
      overflow: hidden;
    }

    .el-table__inner-wrapper::before {
      background-color: transparent !important;
    }

    .el-table__header-wrapper th.el-table__cell {
      background: var(--sf-table-bg) !important;
      color: rgba(15, 23, 42, 0.82) !important;
      font-weight: 800;
      border-bottom: 1px solid var(--sf-table-border) !important;
    }

    .el-table__body-wrapper td.el-table__cell {
      background: var(--sf-table-bg) !important;
      color: var(--sf-table-text) !important;
      border-bottom: 1px solid rgba(15, 23, 42, 0.06) !important;
    }

    .el-table__body tr.el-table__row--striped td.el-table__cell {
      background: var(--sf-table-bg-alt) !important;
    }

    .cell {
      color: var(--sf-table-text) !important;
    }
  }

  :deep(.status-select .el-select__wrapper),
  :deep(.site-feedback-dialog .el-select__wrapper) {
    background: #ffffff !important;
    box-shadow: 0 0 0 1px rgba(100, 116, 139, 0.32) inset !important;
  }

  :deep(.status-select .el-select__placeholder),
  :deep(.status-select .el-select__selected-item),
  :deep(.site-feedback-dialog .el-select__placeholder),
  :deep(.site-feedback-dialog .el-select__selected-item) {
    color: #334155 !important;
  }

  :deep(.site-feedback-dialog .el-input__wrapper) {
    background: #ffffff !important;
    box-shadow: 0 0 0 1px rgba(100, 116, 139, 0.32) inset !important;
  }

  :deep(.site-feedback-dialog .el-input__inner),
  :deep(.site-feedback-dialog .el-textarea__inner),
  :deep(.site-feedback-dialog .el-form-item__label),
  :deep(.site-feedback-dialog .el-dialog__title) {
    color: #1f2937 !important;
  }

  :deep(.site-feedback-dialog .el-input__inner::placeholder),
  :deep(.site-feedback-dialog .el-textarea__inner::placeholder) {
    color: rgba(71, 85, 105, 0.78) !important;
  }

  :deep(.status-tag.el-tag) {
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

  :deep(.status-tag.el-tag--success) {
    border-color: rgba(16, 185, 129, 0.30) !important;
    background: rgba(16, 185, 129, 0.12) !important;
    color: rgba(6, 95, 70, 0.92) !important;
  }

  :deep(.status-tag.el-tag--warning) {
    border-color: rgba(245, 158, 11, 0.32) !important;
    background: rgba(245, 158, 11, 0.14) !important;
    color: rgba(120, 53, 15, 0.92) !important;
  }

  :deep(.status-tag.el-tag--info) {
    border-color: rgba(100, 116, 139, 0.30) !important;
    background: rgba(100, 116, 139, 0.12) !important;
    color: rgba(30, 41, 59, 0.90) !important;
  }
}
</style>

