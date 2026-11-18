<template>
  <!--
    全局登录引导弹窗。
    未登录用户点"点赞/收藏/评论/下载"等写操作时弹出，
    给两个明确出口：去登录，或者继续只读浏览。
  -->
  <transition name="login-guide-fade">
    <div
      v-if="state.visible"
      class="login-guide-mask"
      role="dialog"
      aria-modal="true"
      aria-labelledby="login-guide-title"
      @click.self="closeLoginGuide"
    >
      <div class="login-guide-card">
        <div class="guide-badge" aria-hidden="true">印</div>

        <h3 id="login-guide-title" class="guide-title">
          {{ state.action ? `登录后才能${state.action}` : '登录后使用完整功能' }}
        </h3>
        <p class="guide-desc">{{ getLoginHint(state.action) }}</p>

        <ul class="guide-points">
          <li>浏览模型、地图与 AI 问答不需要登录</li>
          <li>登录后可点赞、收藏、评论、下载并保存记录</li>
        </ul>

        <div class="guide-actions">
          <el-button class="guide-secondary" @click="closeLoginGuide">先随便看看</el-button>
          <el-button type="primary" class="guide-primary" @click="goLogin">
            去登录 / 注册
          </el-button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useLoginGuide } from '@/composables/useLoginGuide'

const router = useRouter()
const route = useRoute()
const { state, closeLoginGuide, getLoginHint } = useLoginGuide()

const goLogin = () => {
  closeLoginGuide()
  // 带上当前页面，登录后能回到原处
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}
</script>

<style scoped lang="scss">
.login-guide-mask {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(8, 12, 20, 0.46);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
}

.login-guide-card {
  width: min(420px, 100%);
  padding: 26px 24px 22px;
  border-radius: 22px;
  text-align: center;
  background: var(--v2-surface-strong);
  border: 1px solid var(--v2-border-soft);
  box-shadow: var(--v2-shadow);

  .guide-badge {
    width: 44px;
    height: 44px;
    margin: 0 auto 14px;
    display: grid;
    place-items: center;
    border-radius: 14px;
    font-size: 16px;
    font-weight: 800;
    color: var(--v2-chip-text);
    background: var(--v2-chip-bg);
    border: 1px solid var(--v2-chip-border);
  }

  .guide-title {
    margin: 0 0 8px;
    font-size: 17px;
    font-weight: 800;
    color: var(--v2-text-strong);
  }

  .guide-desc {
    margin: 0 0 14px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--v2-text-body);
  }

  .guide-points {
    margin: 0 0 20px;
    padding: 12px 14px;
    list-style: none;
    text-align: left;
    border-radius: 12px;
    background: var(--v2-surface-soft);
    border: 1px dashed var(--v2-border-soft);

    li {
      position: relative;
      padding-left: 16px;
      font-size: 12.5px;
      line-height: 1.8;
      color: var(--v2-text-weak);

      &::before {
        content: '';
        position: absolute;
        left: 3px;
        top: 0.72em;
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background: var(--v2-gold);
      }
    }
  }

  .guide-actions {
    display: flex;
    gap: 10px;
    justify-content: center;

    .guide-secondary,
    .guide-primary {
      flex: 1 1 0;
      min-height: 40px;
      border-radius: 12px !important;
    }
  }
}

.login-guide-fade-enter-active,
.login-guide-fade-leave-active {
  transition: opacity 200ms ease;

  .login-guide-card {
    transition: transform 220ms cubic-bezier(.2, .8, .2, 1);
  }
}

.login-guide-fade-enter-from,
.login-guide-fade-leave-to {
  opacity: 0;

  .login-guide-card {
    transform: translateY(10px) scale(0.98);
  }
}
</style>
