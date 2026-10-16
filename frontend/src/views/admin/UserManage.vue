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
          <el-input v-model="form.avatar" placeholder="请输入头像URL" />
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

const resetForm = () => {
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

const submitForm = async () => {
  try {
    await formRef.value.validate()
    if (isCreate.value) {
      await request.post('/admin/users', form.value)
      ElMessage.success('用户创建成功')
    } else {
      // 使用表单中的用户ID
      const userId = form.value.id
      if (!userId) {
        ElMessage.error('用户ID缺失')
        return
      }
      await request.put(`/admin/users/${userId}`, {
        username: form.value.username,
        email: form.value.email,
        nickname: form.value.nickname,
        avatar: form.value.avatar,
        role: form.value.role
      })
      ElMessage.success('用户更新成功')
    }
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
</style>
