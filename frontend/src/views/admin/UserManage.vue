<template>
  <div class="user-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
          <span>用户管理</span>
          <el-button type="primary" @click="openCreateDialog">添加用户</el-button>
        </div>
      </template>

      <el-table class="user-table" :data="users" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="72" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" min-width="140" width="150" align="center">
          <template #default="scope">
            <el-select class="role-select" v-model="scope.row.role" @change="handleRoleChange(scope.row)">
              <el-option label="普通用户" value="user" />
              <el-option label="贡献者" value="contributor" />
              <el-option label="管理员" value="admin" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="168" width="180" align="center">
          <template #default="scope">
            <span class="cell-datetime">{{ formatCreatedAt(scope.row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="resetPassword(scope.row)">重置密码</el-button>
            <el-button size="small" type="danger" @click="deleteUser(scope.row)" style="margin-left: 8px;">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadUsers"
        style="margin-top: 20px; justify-content: center;"
      />
    </el-card>

    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>贡献者申请</span>
        </div>
      </template>

      <el-table :data="applications" style="width: 100%" v-loading="applicationsLoading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="申请人" width="150" />
        <el-table-column prop="description" label="申请说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" min-width="168" width="180" align="center">
          <template #default="scope">
            <span class="cell-datetime">{{ formatCreatedAt(scope.row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <template v-if="scope.row.status === 0">
              <el-button size="small" type="success" @click="approveApplication(scope.row)">通过</el-button>
              <el-button size="small" type="danger" @click="rejectApplication(scope.row)">拒绝</el-button>
            </template>
            <span v-else-if="scope.row.reviewRemark" class="review-remark">
              备注: {{ scope.row.reviewRemark }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="dialogTitle"
      width="500px"
      @closed="handleDialogClosed"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="isCreate">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="头像" prop="avatar">
          <div class="avatar-editor">
            <div
              class="avatar-preview"
              :class="{ 'is-dragover': avatarDragOver }"
              @click="triggerAvatarPick"
              @dragover.prevent="avatarDragOver = true"
              @dragenter.prevent="avatarDragOver = true"
              @dragleave.prevent="avatarDragOver = false"
              @drop.prevent="handleAvatarDrop"
              :title="'点击上传，或把图片拖进来'"
            >
              <!-- 选了新图就先显示本地预览，否则显示已保存的头像 -->
              <el-avatar :size="56" :src="previewObjectUrl || form.avatar">
                {{ (form.nickname || form.username || '?').charAt(0) }}
              </el-avatar>
              <div class="avatar-preview-mask">
                <el-icon v-if="!avatarUploading"><Camera /></el-icon>
                <el-icon v-else class="is-loading-icon"><Loading /></el-icon>
              </div>
            </div>
            <div class="avatar-fields">
              <div class="avatar-actions">
                <el-button size="small" :loading="avatarUploading" @click="triggerAvatarPick">
                  {{ avatarUploading ? '上传中' : '上传图片' }}
                </el-button>
                <el-button size="small" text @click="clearAvatar" v-if="form.avatar || pendingAvatarFile">清除</el-button>
              </div>
              <!-- 已选本地图片时给个提示，避免用户以为输入框坏了 -->
              <div v-if="pendingAvatarFile" class="avatar-pending-tip">
                已选择本地图片，点「确认」后上传
              </div>
              <!-- 保留手填入口：老数据或想直接用外链时还能用；选本地图后此框不参与保存 -->
              <el-input
                v-else
                v-model="form.avatar"
                placeholder="或直接填写头像 URL"
                size="small"
              />
            </div>
            <input
              ref="avatarInputRef"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif,image/bmp"
              class="avatar-input"
              @change="handleAvatarChange"
            />
          </div>
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色">
            <el-option label="普通用户" value="user" />
            <el-option label="贡献者" value="contributor" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Camera, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'

const loading = ref(false)
const applicationsLoading = ref(false)
const users = ref([])
const applications = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isCreate = ref(true)
const formRef = ref()
const avatarInputRef = ref(null)
const avatarUploading = ref(false)
// 选图后的待上传文件（点确认才真正提交，取消即丢弃）
const pendingAvatarFile = ref(null)
// 本地预览用的 blob URL，换图/关闭弹窗时要 revoke 掉
const previewObjectUrl = ref('')
// 打开编辑时记下原始头像，用于「清除」和上传失败回退
const originalAvatar = ref('')
const form = ref({
  id: null,
  username: '',
  password: '',
  email: '',
  nickname: '',
  avatar: '',
  role: 'user'
})
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度在 3 到 64 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

const dialogTitle = computed(() => isCreate.value ? '添加用户' : '编辑用户')

/** 将后端 ISO 时间格式化为单行可读字符串，避免表格列换行 */
const formatCreatedAt = (val) => {
  if (val == null || val === '') return '—'
  const d = new Date(String(val).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(val)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 释放 blob 预览并丢弃待上传文件；关闭弹窗或提交完成后调用 */
const releasePreview = () => {
  if (previewObjectUrl.value) {
    URL.revokeObjectURL(previewObjectUrl.value)
    previewObjectUrl.value = ''
  }
  pendingAvatarFile.value = null
}

const resetForm = () => {
  releasePreview()
  originalAvatar.value = ''
  form.value = {
    id: null,
    username: '',
    password: '',
    email: '',
    nickname: '',
    avatar: '',
    role: 'user'
  }
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

const openCreateDialog = () => {
  isCreate.value = true
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (user) => {
  isCreate.value = false
  releasePreview()
  // 记下原始值，「清除」和上传失败时用它回退
  originalAvatar.value = user.avatar || ''
  form.value = {
    id: user.id,
    username: user.username,
    password: '',
    email: user.email || '',
    nickname: user.nickname || '',
    avatar: user.avatar || '',
    role: user.role
  }
  dialogVisible.value = true
}

/** 弹窗关闭（含点取消、点遮罩、按 Esc）统一在这里清理预览资源 */
const handleDialogClosed = () => {
  releasePreview()
  originalAvatar.value = ''
}

const triggerAvatarPick = () => {
  if (avatarUploading.value) return
  avatarInputRef.value?.click()
}

/** 拖拽悬停高亮，离开或放下时复位 */
const avatarDragOver = ref(false)

/** 把拖进来的文件走和「选择文件」完全相同的校验与预览流程 */
const handleAvatarDrop = (event) => {
  avatarDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  // 复用 input 的 change 处理逻辑，避免两套校验规则走岔
  handleAvatarChange({ target: { files: [file], value: '' } })
}

/**
 * 选图后只做本地预览，不立刻上传。
 * 上传放在 submitForm 里，用户点「取消」就等于什么都没发生——
 * 否则弹窗还开着图就换了，点取消也回不去。
 */
const handleAvatarChange = (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件（JPG/PNG/WebP）')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('头像图片不能超过 5MB')
    return
  }

  // 换图时把上一个预览的 blob URL 释放掉，避免内存泄漏
  if (previewObjectUrl.value) URL.revokeObjectURL(previewObjectUrl.value)

  previewObjectUrl.value = URL.createObjectURL(file)
  pendingAvatarFile.value = file
}

/** 点「清除」：恢复成原始头像，并丢弃待上传的文件 */
const clearAvatar = () => {
  if (previewObjectUrl.value) {
    URL.revokeObjectURL(previewObjectUrl.value)
    previewObjectUrl.value = ''
  }
  pendingAvatarFile.value = null
  form.value.avatar = originalAvatar.value || ''
}

/**
 * 把待上传的头像真正提交上去，返回应写进表单的 URL。
 * userId 为 null 时说明用户还没建，返回 null 由调用方决定后续。
 */
const commitPendingAvatar = async (userId) => {
  if (!pendingAvatarFile.value || !userId) return null
  try {
    const formData = new FormData()
    formData.append('file', pendingAvatarFile.value)
    const resp = await request.post(`/admin/users/${userId}/avatar`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return resp.data?.data || null
  } catch (error) {
    console.error('[UserManage] 头像上传失败', error)
    ElMessage.warning('头像上传失败，其他资料已保存，可稍后重试')
    return null
  }
}

const submitForm = async () => {
  try {
    await formRef.value.validate()
    if (isCreate.value) {
      // 新建态直接提交表单 avatar（URL 或空），本地图片建完人再补传
      const created = await request.post('/admin/users', { ...form.value })
      const newUserId = created.data?.data?.id
      if (pendingAvatarFile.value && newUserId) {
        await commitPendingAvatar(newUserId)
      }
      ElMessage.success('用户创建成功')
    } else {
      const userId = form.value.id
      if (!userId) {
        ElMessage.error('用户ID缺失')
        return
      }

      // 有暂存图片就先上传拿真实 URL；否则沿用表单里的 URL（可能是外链或已存路径）
      let avatarToSave = form.value.avatar
      if (pendingAvatarFile.value) {
        avatarToSave = await commitPendingAvatar(userId) || originalAvatar.value || ''
      }

      await request.put(`/admin/users/${userId}`, {
        username: form.value.username,
        email: form.value.email,
        nickname: form.value.nickname,
        avatar: avatarToSave,
        role: form.value.role
      })
      ElMessage.success('用户更新成功')
    }
    releasePreview()
    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    console.error('[UserManage] 提交表单失败', error)
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else if (error.message) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('操作失败')
    }
  }
}

onMounted(() => {
  loadUsers()
  loadApplications()
})

const loadUsers = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/users', {
      params: { page: currentPage.value, pageSize: pageSize.value }
    })
    users.value = response.data.data?.records || []
    total.value = response.data.data?.total || 0
  } catch (error) {
    console.error('[UserManage] 加载用户列表失败', error)
  } finally {
    loading.value = false
  }
}

const loadApplications = async () => {
  applicationsLoading.value = true
  try {
    const response = await request.get('/admin/contribution-applies', {
      params: { status: 0 }
    })
    applications.value = response.data.data?.records || []
  } catch (error) {
    console.error('[UserManage] 加载申请列表失败', error)
  } finally {
    applicationsLoading.value = false
  }
}

const handleRoleChange = async (user) => {
  try {
    await request.put(`/admin/users/${user.id}/role`, { role: user.role })
    ElMessage.success('角色更新成功')
  } catch (error) {
    console.error('[UserManage] 更新用户角色失败', error)
    ElMessage.error('角色更新失败')
    loadUsers()
  }
}

const resetPassword = async (user) => {
  try {
    await ElMessageBox.confirm(`确定要重置用户 ${user.username} 的密码吗?`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.post(`/admin/users/${user.id}/reset-password`)
    ElMessage.success('密码已重置为默认密码（123456）')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[UserManage] 重置密码失败', error)
    }
  }
}

const deleteUser = async (user) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户 ${user.username} 吗?`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.delete(`/admin/users/${user.id}`)
    ElMessage.success('用户删除成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[UserManage] 删除用户失败', error)
      ElMessage.error('删除用户失败')
    }
  }
}

const getStatusType = (status) => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '已通过'
    case 2: return '已拒绝'
    default: return '未知'
  }
}

const approveApplication = async (application) => {
  try {
    await request.put(`/admin/contribution-applies/${application.id}/status`, { status: 1 })
    ElMessage.success('已通过申请')
    loadApplications()
    loadUsers()
  } catch (error) {
    console.error('[UserManage] 审批申请失败', error)
    ElMessage.error('审批失败')
  }
}

const rejectApplication = async (application) => {
  try {
    await ElMessageBox.prompt('请输入拒绝原因', '拒绝申请', {
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    }).then(async ({ value }) => {
      await request.put(`/admin/contribution-applies/${application.id}/status`, {
        status: 2,
        reviewRemark: value
      })
      ElMessage.success('已拒绝申请')
      loadApplications()
    })
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[UserManage] 驳回申请失败', error)
    }
  }
}
</script>

<style scoped>
.user-manage {
  /* 头像编辑区：左边预览、右边上传按钮 + URL 兜底输入 */
  .avatar-editor {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;

    .avatar-preview {
      position: relative;
      width: 56px;
      height: 56px;
      flex: none;
      border-radius: 50%;
      overflow: hidden;
      cursor: pointer;
      background: #eef2f7;
      box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);

      .avatar-preview-mask {
        position: absolute;
        inset: 0;
        display: grid;
        place-items: center;
        background: rgba(15, 23, 42, 0.55);
        color: #fff;
        opacity: 0;
        transition: opacity 160ms ease;

        .el-icon {
          font-size: 17px;
        }
      }

      &:hover .avatar-preview-mask {
        opacity: 1;
      }

      /* 拖拽悬停时给个明确的可放置反馈 */
      &.is-dragover {
        box-shadow: 0 0 0 2px #6da9df, 0 0 14px rgba(109, 169, 223, 0.55);

        .avatar-preview-mask {
          opacity: 1;
        }
      }

      .is-loading-icon {
        animation: user-avatar-spin 900ms linear infinite;
      }
    }

    .avatar-fields {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .avatar-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .avatar-pending-tip {
      font-size: 12px;
      color: #b45309;
      line-height: 1.4;
      padding: 4px 8px;
      border-radius: 6px;
      background: #fffbeb;
      border: 1px solid #fde68a;
    }

    .avatar-input {
      display: none;
    }
  }

  :deep(.el-table td.el-table__cell) {
    vertical-align: middle;
  }

  :deep(.el-table .cell-datetime) {
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }

  :deep(.user-table .role-select) {
    width: 100%;
    min-width: 128px;
    max-width: 148px;
  }

  /* In this page, the table is white background.
     Make role select readable like "email" column (dark text). */
  :deep(.el-table .role-select .el-select__wrapper) {
    background: rgba(255, 255, 255, 0.98) !important;
    border: 1px solid rgba(15, 23, 42, 0.18) !important;
    box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06) !important;
  }

  :deep(.el-table .role-select .el-select__selected-item),
  :deep(.el-table .role-select .el-select__placeholder) {
    color: rgba(15, 23, 42, 0.86) !important;
    font-weight: 650 !important;
  }

  :deep(.el-table .role-select .el-select__caret) {
    color: rgba(15, 23, 42, 0.6) !important;
  }

  :deep(.el-table .role-select .el-select__wrapper.is-focused) {
    border-color: rgba(102, 126, 234, 0.55) !important;
    box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.14) !important;
  }

  .review-remark {
    color: #999;
    font-size: 12px;
  }
}

@keyframes user-avatar-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
