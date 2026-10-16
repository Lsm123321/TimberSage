<template>
  <div class="v2-page role-apply">
    <div class="page-head v2-glass-strong">
      <div class="title">{{ t('roleUpgradeTitle') }}</div>
      <div class="desc">{{ t('roleUpgradeDesc') }}</div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="v2-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('roleUpgradeApplyCard') }}</span>
              <el-tag v-if="latest" :type="statusTagType(latest.status)" effect="dark">
                {{ statusText(latest.status) }}
              </el-tag>
            </div>
          </template>

          <el-form label-width="110px">
            <el-form-item :label="t('currentRole')">
              <el-tag effect="plain">{{ roleText(currentRole) }}</el-tag>
            </el-form-item>

            <el-form-item :label="t('targetRole')">
              <el-tag effect="plain" type="success">{{ roleText(targetRole) }}</el-tag>
            </el-form-item>

            <el-form-item :label="t('applyReason')">
              <el-input
                v-model="description"
                type="textarea"
                :rows="5"
                :placeholder="t('applyReasonPlaceholder')"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleSubmit" :disabled="!canApply">
                {{ canApply ? t('submitApply') : t('noNeedApply') }}
              </el-button>
              <el-button @click="refreshLatest" :loading="loadingLatest">
                {{ t('refresh') }}
              </el-button>
            </el-form-item>

            <el-alert
              v-if="latest && latest.status === 2 && latest.reviewRemark"
              :title="t('rejectedReason')"
              :description="latest.reviewRemark"
              type="error"
              show-icon
              class="mt-12"
            />
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card class="v2-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('roleUpgradeTips') }}</span>
            </div>
          </template>

          <div class="tips">
            <p>{{ t('roleUpgradeTip1') }}</p>
            <p>{{ t('roleUpgradeTip2') }}</p>
            <p>{{ t('roleUpgradeTip3') }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { submitRoleUpgradeApply, getMyLatestRoleUpgradeApply } from '@/api/roleUpgrade'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { t } = useUiPreferences()
const authStore = useAuthStore()

const description = ref('')
const submitting = ref(false)
const loadingLatest = ref(false)
const latest = ref(null)

const currentRole = computed(() => authStore.userInfo?.role || 'user')
const targetRole = computed(() => {
  if (currentRole.value === 'user') return 'contributor'
  if (currentRole.value === 'contributor') return 'admin'
  return ''
})

const canApply = computed(() => currentRole.value === 'user' || currentRole.value === 'contributor')

function roleText(role) {
  if (role === 'admin') return t('roleAdmin')
  if (role === 'contributor') return t('roleContributor')
  return t('roleUser')
}

function statusText(status) {
  if (status === 1) return t('applyApproved')
  if (status === 2) return t('applyRejected')
  return t('applyPending')
}

function statusTagType(status) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

async function refreshLatest() {
  loadingLatest.value = true
  try {
    const res = await getMyLatestRoleUpgradeApply()
    latest.value = res.data?.data || null
  } catch (e) {
    console.error('[RoleUpgradeApply] 加载最新申请失败', e)
  } finally {
    loadingLatest.value = false
  }
}

async function handleSubmit() {
  if (!canApply.value) return
  if (!description.value.trim()) {
    ElMessage.warning(t('applyReasonRequired'))
    return
  }

  submitting.value = true
  try {
    const res = await submitRoleUpgradeApply({ description: description.value.trim() })
    latest.value = res.data?.data || null
    ElMessage.success(t('submitSuccess'))
  } catch (e) {
    console.error('[RoleUpgradeApply] 提交申请失败', e)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!authStore.userInfo) {
    await authStore.fetchUserInfo()
  }
  await refreshLatest()
})
</script>

<style scoped lang="scss">
.role-apply {
  .page-head {
    padding: 16px 18px;
    border-radius: 14px;
    margin-bottom: 14px;
    .title {
      font-size: 18px;
      font-weight: 800;
      color: rgba(255, 255, 255, 0.92);
      letter-spacing: 0.2px;
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
    :deep(.el-card__header) {
      border-bottom: 1px solid rgba(255, 255, 255, 0.12);
    }
    :deep(.el-form-item__label) {
      color: rgba(255, 255, 255, 0.76);
    }
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: rgba(255, 255, 255, 0.88);
    font-weight: 700;
  }

  .tips {
    color: rgba(255, 255, 255, 0.78);
    line-height: 1.75;
    p {
      margin: 0 0 10px 0;
    }
  }

  .mt-12 {
    margin-top: 12px;
  }
}
</style>

