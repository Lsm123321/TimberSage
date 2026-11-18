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
  /*
    关于我们整页原来把颜色写死成「半透明白字 + 白描边」，
    这是按深色底设计的；切到浅色主题后文字和面板一起消失。
    改用语义 token 后，深色是墨底暖金字、浅色是奶白底深墨字。
  */
  .page-head {
    padding: 16px 18px;
    border-radius: 14px;
    margin-bottom: 14px;
    .title {
      font-size: 18px;
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
    :deep(.el-card__header) {
      border-bottom: 1px solid var(--v2-divider);
    }
    :deep(.el-form-item__label) {
      color: var(--v2-text-body);
    }
  }

  .card-header {
    color: var(--v2-text-strong);
    font-weight: 700;
  }

  .info {
    color: var(--v2-text-body);
    line-height: 1.8;
    .contact {
      margin-top: 10px;
      a {
        color: var(--v2-gold);
        text-decoration: underline;
      }
    }
  }
}
</style>

