<template>
  <div class="v2-page about-us">
    <div class="page-head v2-glass-strong">
      <div class="title">{{ t('aboutTitle') }}</div>
      <div class="desc">{{ t('aboutSubtitle') }}</div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="v2-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('aboutInfo') }}</span>
            </div>
          </template>

          <div class="info">
            <p>{{ t('aboutIntro') }}</p>
            <p class="contact">
              <span>{{ t('aboutEmail') }}</span>
              <a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
            </p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card class="v2-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('aboutFeedback') }}</span>
            </div>
          </template>

          <el-form label-width="90px">
            <el-form-item :label="t('feedbackTitle')">
              <el-input v-model="title" :placeholder="t('feedbackTitlePlaceholder')" maxlength="128" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('feedbackContent')">
              <el-input
                v-model="content"
                type="textarea"
                :rows="5"
                :placeholder="t('feedbackContentPlaceholder')"
                maxlength="5000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item :label="t('feedbackContact')">
              <el-input v-model="contact" :placeholder="t('feedbackContactPlaceholder')" maxlength="128" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleSubmit">
                {{ t('feedbackSubmit') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createSiteFeedback } from '@/api/siteFeedback'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { t } = useUiPreferences()

const contactEmail = '3207964878@qq.com'

const title = ref('')
const content = ref('')
const contact = ref('')
const submitting = ref(false)

async function handleSubmit() {
  if (!title.value.trim()) {
    ElMessage.warning(t('feedbackTitleRequired'))
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning(t('feedbackContentRequired'))
    return
  }

  submitting.value = true
  try {
    await createSiteFeedback({
      title: title.value.trim(),
      content: content.value.trim(),
      contact: contact.value.trim()
    })
    ElMessage.success(t('submitSuccess'))
    title.value = ''
    content.value = ''
    contact.value = ''
  } catch (e) {
    console.error('[AboutUs] 提交站点反馈失败', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.about-us {
  .page-head {
    padding: 16px 18px;
    border-radius: 14px;
    margin-bottom: 14px;
    .title {
      font-size: 18px;
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
    :deep(.el-card__header) {
      border-bottom: 1px solid rgba(255, 255, 255, 0.12);
    }
    :deep(.el-form-item__label) {
      color: rgba(255, 255, 255, 0.76);
    }
  }

  .card-header {
    color: rgba(255, 255, 255, 0.88);
    font-weight: 700;
  }

  .info {
    color: rgba(255, 255, 255, 0.8);
    line-height: 1.8;
    .contact {
      margin-top: 10px;
      a {
        color: rgba(255, 255, 255, 0.92);
        text-decoration: underline;
      }
    }
  }
}
</style>

