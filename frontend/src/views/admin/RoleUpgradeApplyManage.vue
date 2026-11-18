<template>
  <div class="admin-page">
    <div class="page-head v2-glass-strong">
      <div class="title">{{ t('adminRoleUpgradeTitle') }}</div>
      <div class="desc">{{ t('adminRoleUpgradeDesc') }}</div>
    </div>

    <el-card class="v2-card" shadow="never">
      <div class="toolbar">
        <el-select v-model="status" class="status-select" :placeholder="t('statusFilter')">
          <el-option :label="t('statusAll')" :value="''" />
          <el-option :label="t('applyPending')" :value="0" />
          <el-option :label="t('applyApproved')" :value="1" />
          <el-option :label="t('applyRejected')" :value="2" />
        </el-select>
        <el-button @click="loadData" :loading="loading">{{ t('refresh') }}</el-button>
      </div>

      <el-table :data="records" stripe class="v2-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="t('user')" min-width="120" />
        <el-table-column prop="fromRole" :label="t('currentRole')" min-width="120">
          <template #default="{ row }">
            <el-tag class="role-tag role-tag--from" effect="plain">{{ roleText(row.fromRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="toRole" :label="t('targetRole')" min-width="120">
          <template #default="{ row }">
            <el-tag class="role-tag role-tag--to" effect="plain" type="success">{{ roleText(row.toRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('applyReason')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('status')" width="110">
          <template #default="{ row }">
            <el-tag class="status-tag" :type="statusTagType(row.status)" effect="dark">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('createdAt')" width="180" />
        <el-table-column :label="t('actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" :disabled="row.status !== 0" @click="approve(row)">
              {{ t('approve') }}
            </el-button>
            <el-button size="small" type="danger" :disabled="row.status !== 0" @click="reject(row)">
              {{ t('reject') }}
            </el-button>
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

    <el-dialog v-model="rejectVisible" :title="t('rejectTitle')" width="520px">
      <el-input v-model="rejectRemark" type="textarea" :rows="4" :placeholder="t('rejectRemarkPlaceholder')" />
      <template #footer>
        <el-button @click="rejectVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="danger" :loading="rejecting" @click="confirmReject">{{ t('confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminGetRoleUpgradeApplies, adminUpdateRoleUpgradeApplyStatus } from '@/api/roleUpgrade'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { t } = useUiPreferences()

const status = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])
const loading = ref(false)

const rejectVisible = ref(false)
const rejectRemark = ref('')
const rejecting = ref(false)
const rejectRow = ref(null)

function roleText(role) {
  if (role === 'admin') return t('roleAdmin')
  if (role === 'contributor') return t('roleContributor')
  return t('roleUser')
}

function statusText(s) {
  if (s === 1) return t('applyApproved')
  if (s === 2) return t('applyRejected')
  return t('applyPending')
}

function statusTagType(s) {
  if (s === 1) return 'success'
  if (s === 2) return 'danger'
  return 'warning'
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (status.value !== '') params.status = status.value
    const res = await adminGetRoleUpgradeApplies(params)
    const data = res.data?.data
    total.value = data?.total || 0
    records.value = data?.records || []
  } catch (e) {
    console.error('[RoleUpgradeApplyManage] 加载列表失败', e)
  } finally {
    loading.value = false
  }
}

function handlePageChange(p) {
  page.value = p
  loadData()
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(t('approveConfirm'), t('tips'), { type: 'warning' })
    await adminUpdateRoleUpgradeApplyStatus(row.id, { status: 1 })
    ElMessage.success(t('submitSuccess'))
    loadData()
  } catch (e) {
    // 用户点了取消会走到这里，不用管；真正的失败上面已经提示过
  }
}

function reject(row) {
  rejectRow.value = row
  rejectRemark.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectRow.value) return
  if (!rejectRemark.value.trim()) {
    ElMessage.warning(t('rejectRemarkRequired'))
    return
  }
  rejecting.value = true
  try {
    await adminUpdateRoleUpgradeApplyStatus(rejectRow.value.id, { status: 2, reviewRemark: rejectRemark.value.trim() })
    ElMessage.success(t('submitSuccess'))
    rejectVisible.value = false
    loadData()
  } catch (e) {
    console.error('[RoleUpgradeApplyManage] 驳回申请失败', e)
  } finally {
    rejecting.value = false
  }
}

onMounted(() => {
  loadData()
})
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
      color: var(--v2-text-strong);
    }
    .desc {
      margin-top: 6px;
      font-size: 13px;
      color: var(--v2-text-weak);
    }
  }

  .v2-card {
    border-radius: var(--v2-radius-lg);
    border: 1px solid var(--v2-border-soft);
    background: var(--v2-surface);
  }

  .toolbar {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 12px;
  }

  .status-select {
    width: 200px;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }

  /* 局部修复：全局主题把 el-tag / el-table 文本设成浅色，
     在管理端表格的浅色底上会导致“当前角色/目标角色”看不清。 */
  :deep(.v2-table) {
    --v2-table-bg: rgba(255, 255, 255, 0.92);
    --v2-table-bg-soft: rgba(255, 255, 255, 0.86);
    --v2-table-border: rgba(15, 23, 42, 0.10);
    --v2-table-text: #1f2937;
    --v2-table-text-dim: rgba(31, 41, 55, 0.78);

    /* 表格整体与行背景 */
    .el-table {
      background: transparent !important;
      color: var(--v2-table-text) !important;
      border-radius: 14px;
      overflow: hidden;
    }

    .el-table__inner-wrapper::before {
      background-color: transparent !important;
    }

    .el-table__header-wrapper th.el-table__cell {
      background: var(--v2-table-bg) !important;
      color: rgba(15, 23, 42, 0.82) !important;
      font-weight: 800;
      border-bottom: 1px solid var(--v2-table-border) !important;
    }

    .el-table__body-wrapper td.el-table__cell {
      background: var(--v2-table-bg-soft) !important;
      color: var(--v2-table-text) !important;
      border-bottom: 1px solid rgba(15, 23, 42, 0.06) !important;
    }

    .el-table__body tr.el-table__row--striped td.el-table__cell {
      background: rgba(248, 250, 252, 0.92) !important;
    }

    /* Tooltip 触发列的文字也要清晰 */
    .cell {
      color: var(--v2-table-text) !important;
    }

    /* 当前角色/目标角色标签：提升对比度，避免被全局 .el-tag 覆盖 */
    .role-tag.el-tag {
      border-radius: 999px;
      padding: 0 10px;
      height: 26px;
      line-height: 24px;
      font-weight: 800;
      letter-spacing: 0.2px;
      border: 1px solid rgba(15, 23, 42, 0.16) !important;
      background: rgba(15, 23, 42, 0.05) !important;
      color: rgba(15, 23, 42, 0.86) !important;
    }

    .role-tag--to.el-tag {
      border-color: rgba(16, 185, 129, 0.28) !important;
      background: rgba(16, 185, 129, 0.10) !important;
      color: rgba(6, 95, 70, 0.92) !important;
    }

    /* 暗色状态标签保留，但加一点可读性 */
    .el-tag--dark {
      font-weight: 800;
      letter-spacing: 0.2px;
    }

    /* 状态列：避免全局 .el-tag 背景/文字覆盖导致发白 */
    .status-tag.el-tag {
      border-radius: 999px;
      height: 26px;
      line-height: 26px;
      padding: 0 10px;
      font-weight: 800;
      letter-spacing: 0.2px;
      border: 1px solid rgba(15, 23, 42, 0.16) !important;
      color: rgba(15, 23, 42, 0.92) !important;
      background: rgba(15, 23, 42, 0.06) !important;
    }

    .status-tag.el-tag--success {
      border-color: rgba(16, 185, 129, 0.30) !important;
      background: rgba(16, 185, 129, 0.12) !important;
      color: rgba(6, 95, 70, 0.92) !important;
    }

    .status-tag.el-tag--warning {
      border-color: rgba(245, 158, 11, 0.32) !important;
      background: rgba(245, 158, 11, 0.14) !important;
      color: rgba(120, 53, 15, 0.92) !important;
    }

    .status-tag.el-tag--danger {
      border-color: rgba(239, 68, 68, 0.30) !important;
      background: rgba(239, 68, 68, 0.12) !important;
      color: rgba(127, 29, 29, 0.92) !important;
    }
  }
}
</style>

