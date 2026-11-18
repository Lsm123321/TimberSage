<template>
  <div class="admin-login">
    <div class="bg-grid" aria-hidden="true"></div>
    <div class="bg-glow bg-glow-a" aria-hidden="true"></div>
    <div class="bg-glow bg-glow-b" aria-hidden="true"></div>

    <main class="login-card">
      <header class="brand">
        <div class="brand-mark">智</div>
        <div class="brand-text">
          <h1>智筑云览 · 管理控制台</h1>
          <p>ADMIN CONSOLE</p>
        </div>
      </header>

      <div class="divider" aria-hidden="true"></div>

      <p class="hint">后台入口独立于前台，仅管理员账号可登录。</p>

      <el-alert
        v-if="deniedMessage"
        :title="deniedMessage"
        type="warning"
        :closable="false"
        show-icon
        class="denied"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="管理员账号"
            :prefix-icon="User"
            size="large"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="验证码"
              size="large"
              maxlength="6"
              @keyup.enter="handleLogin"
            />
            <button type="button" class="captcha-box" title="点击刷新验证码" @click="loadCaptcha">
              <img v-if="captchaSvg" :src="captchaSvg" alt="验证码" />
              <span v-else>加载中</span>
            </button>
          </div>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="submit"
          :loading="loading"
          @click="handleLogin"
        >
          进入控制台
        </el-button>
      </el-form>

      <footer class="footer">
        <router-link to="/">← 返回主站</router-link>
        <span>仅限授权管理员</span>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getLoginCaptcha } from '@/api/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const captchaSvg = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: ''
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const deniedMessage = computed(() => (
  route.query.denied
    ? '当前登录账号没有管理员权限，请使用管理员账号重新登录。'
    : ''
))

const loadCaptcha = async () => {
  try {
    const res = await getLoginCaptcha()
    const payload = res.data?.data
    form.captchaId = payload?.captchaId || ''
    captchaSvg.value = payload?.captchaSvg || ''
    form.captchaCode = ''
  } catch (error) {
    console.error('[AdminLogin] 验证码加载失败', error)
    ElMessage.error('验证码加载失败，请稍后重试')
  }
}

const resetFormAfterFailure = async () => {
  form.password = ''
  form.captchaCode = ''
  await loadCaptcha()
}

const handleLogin = async () => {
  if (!formRef.value || loading.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  ElMessage.closeAll()

  try {
    const result = await authStore.loginAction(form, { redirect: false })
    if (!result.success) {
      ElMessage.error(result.message || '登录失败，请检查账号、密码和验证码')
      await resetFormAfterFailure()
      return
    }

    // loginAction 内部已经拉过 userInfo；这里再确认一次角色，
    // 普通用户即使拿到 token 也不能进后台。
    if (authStore.userInfo?.role !== 'admin') {
      authStore.clearAuth()
      ElMessage.error('该账号不是管理员，已退出登录')
      await resetFormAfterFailure()
      return
    }

    ElMessage.success('管理员登录成功')
    const redirect = typeof route.query.redirect === 'string'
      && route.query.redirect.startsWith('/admin')
      ? route.query.redirect
      : '/admin/dashboard'
    router.replace(redirect)
  } catch (error) {
    console.error('[AdminLogin] 登录异常', error)
    ElMessage.error('登录请求失败，请稍后重试')
    await resetFormAfterFailure()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 已经是管理员就直接进后台，避免重复登录
  if (authStore.token) {
    if (!authStore.userInfo) {
      try {
        await authStore.fetchUserInfo()
      } catch (error) {
        // 拉取失败按未登录处理
      }
    }
    if (authStore.userInfo?.role === 'admin') {
      router.replace('/admin/dashboard')
      return
    }
  }
  await loadCaptcha()
})
</script>

<style lang="scss" scoped>
.admin-login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 18px;
  position: relative;
  overflow: hidden;
  background: #080b10;
  color: #f2ecdf;
}

.bg-grid {
  position: fixed;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(circle at 50% 42%, #000 0%, transparent 72%);
  pointer-events: none;
}

.bg-glow {
  position: fixed;
  width: 520px;
  height: 520px;
  border-radius: 999px;
  filter: blur(90px);
  opacity: 0.34;
  pointer-events: none;
}

.bg-glow-a {
  top: -180px;
  right: -120px;
  background: radial-gradient(circle, rgba(201, 169, 97, 0.7), transparent 68%);
}

.bg-glow-b {
  bottom: -200px;
  left: -140px;
  background: radial-gradient(circle, rgba(72, 122, 112, 0.62), transparent 68%);
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(440px, 100%);
  padding: 34px 32px 26px;
  border-radius: 22px;
  border: 1px solid rgba(214, 183, 122, 0.24);
  background:
    linear-gradient(160deg, rgba(24, 30, 38, 0.96), rgba(12, 16, 22, 0.97));
  box-shadow:
    0 30px 80px rgba(0, 0, 0, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(16px);
  animation: card-in 520ms cubic-bezier(0.16, 1, 0.3, 1) both;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 15px;
  font-size: 22px;
  font-weight: 800;
  color: #20180a;
  background: linear-gradient(145deg, #f2d79b, #c9a961);
  box-shadow: 0 10px 24px rgba(201, 169, 97, 0.28);
}

.brand-text h1 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0.5px;
  color: #f7f1e4;
}

.brand-text p {
  margin: 3px 0 0;
  font-size: 10px;
  letter-spacing: 3px;
  color: rgba(214, 183, 122, 0.72);
}

.divider {
  height: 1px;
  margin: 22px 0 18px;
  background: linear-gradient(90deg, transparent, rgba(214, 183, 122, 0.35), transparent);
}

.hint {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(242, 236, 223, 0.58);
}

.denied {
  margin-bottom: 16px;
  border-radius: 12px;
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-input__wrapper) {
    background: rgba(255, 255, 255, 0.055);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12) inset;
    border-radius: 12px;
    padding: 2px 12px;
  }

  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px rgba(214, 183, 122, 0.72) inset;
  }

  :deep(.el-input__inner) {
    color: #f7f1e4;
    caret-color: #d6b77a;
  }

  :deep(.el-input__inner::placeholder) {
    color: rgba(242, 236, 223, 0.38);
  }

  :deep(.el-input__prefix),
  :deep(.el-input__suffix) {
    color: rgba(242, 236, 223, 0.52);
  }
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-box {
  width: 128px;
  flex: 0 0 128px;
  height: 42px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: rgba(242, 236, 223, 0.55);
  font-size: 12px;
  transition: border-color 180ms ease;

  &:hover {
    border-color: rgba(214, 183, 122, 0.65);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.submit {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border: none;
  border-radius: 13px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #20180a;
  background: linear-gradient(135deg, #f0d398, #c9a961);
  box-shadow: 0 14px 30px rgba(201, 169, 97, 0.22);
  transition: transform 180ms ease, box-shadow 180ms ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 18px 36px rgba(201, 169, 97, 0.3);
  }
}

.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 22px;
  font-size: 12px;
  color: rgba(242, 236, 223, 0.38);

  a {
    color: rgba(214, 183, 122, 0.82);
    text-decoration: none;

    &:hover {
      color: #f0d398;
    }
  }
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 520px) {
  .login-card {
    padding: 28px 22px 22px;
  }

  .captcha-box {
    width: 108px;
    flex-basis: 108px;
  }
}
</style>