<template>
  <div class="login-container">
    <div v-if="useFallbackBg" class="login-bg-fallback" aria-hidden="true"></div>

    <video
      v-show="!useFallbackBg"
      class="login-bg-video-fill"
      :src="loginBgVideo"
      autoplay
      muted
      loop
      playsinline
      preload="auto"
      aria-hidden="true"
    ></video>
    <video
      ref="videoRef"
      v-show="!useFallbackBg"
      class="login-bg-video"
      :src="loginBgVideo"
      autoplay
      muted
      loop
      playsinline
      preload="auto"
      @loadeddata="onLoadedData"
      @canplay="onCanPlay"
      @error="onVideoError"
    ></video>
    <div class="login-bg-overlay"></div>
    <div class="aurora-layer"></div>
    <transition name="login-success-fade">
      <div v-if="loginSuccess" class="login-success-mask" aria-hidden="true">
        <div class="boot-welcome-panel">
          <div class="scan-line"></div>
          <!-- 头像框：有头像就显示图片，没有就退回用户名首字，避免圆圈空着 -->
          <div class="success-ring">
            <img v-if="welcomeAvatar" :src="welcomeAvatar" :alt="welcomeName" class="ring-avatar" />
            <span v-else class="ring-avatar-fallback">{{ welcomeInitial }}</span>
          </div>
          <div class="boot-title">WELCOME</div>
          <div class="boot-subtitle">欢迎你，{{ welcomeName }}</div>
          <div class="boot-status">
            系统启动中<span class="dot dot-1">.</span><span class="dot dot-2">.</span><span class="dot dot-3">.</span>
          </div>
          <div class="boot-cursor"></div>
        </div>
      </div>
    </transition>
    <div class="login-box">
      <div class="login-header">
        <h1>智筑云览</h1>
        <p>以数字光影重现古建之魂</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captchaCode"
              placeholder="请输入验证码"
              size="large"
              maxlength="6"
              @keyup.enter="handleLogin"
            />
            <div class="captcha-box" @click="loadCaptcha" title="点击刷新验证码">
              <img v-if="captchaSvg" :src="captchaSvg" alt="验证码" />
              <span v-else>加载中...</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="isLoading"
            class="login-button"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import loginBgVideo from '@/assets/videos/loginbg.mp4'
import { getLoginCaptcha } from '@/api/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const loginFormRef = ref(null)
const isLoading = ref(false)

const videoRef = ref(null)
const useFallbackBg = ref(false)
const videoReady = ref(false)
let fallbackTimer = null
const captchaSvg = ref('')
const loginSuccess = ref(false)
// 优先用接口返回的昵称：登录表单里填的是账号名，未必是用户想看到的称呼
const welcomeName = computed(() => authStore.userInfo?.nickname || loginForm.username || '用户')
const welcomeAvatar = computed(() => authStore.userInfo?.avatar || '')
const welcomeInitial = computed(() => {
  const name = welcomeName.value.trim()
  return name ? name.charAt(0).toUpperCase() : '?'
})
const LOGIN_SUCCESS_REDIRECT_DELAY = 1500

const degradeToFallback = (_reason) => {
  if (videoReady.value) return
  useFallbackBg.value = true
}

const markVideoReady = () => {
  videoReady.value = true
  if (fallbackTimer) {
    clearTimeout(fallbackTimer)
    fallbackTimer = null
  }
}

const onVideoError = (evt) => {
  const el = evt?.target
  const mediaError = el?.error
  const code = mediaError?.code
  const message = mediaError?.message
  const errorText = [code ? `code=${code}` : '', message ? `message=${message}` : ''].filter(Boolean).join(' ')
  degradeToFallback(errorText || 'video error')
}

const onLoadedData = () => {
  markVideoReady()
}

const onCanPlay = () => {
  markVideoReady()
}

onMounted(async () => {
  const el = videoRef.value
  if (!el) return

  try {
    const p = el.play()
    if (p && typeof p.then === 'function') {
      await p.catch(() => {
        // 自动播放被浏览器策略拦下很常见，不代表视频坏了，先不处理
      })
    }
  } catch (e) {
    // 视频起不来不直接报错，交给下面的定时检查和 error 事件兜底切静态背景
  }
  // 4.5 秒还没就绪就切静态背景，别让用户对着黑屏等
  fallbackTimer = setTimeout(() => {
    if (!videoReady.value && !useFallbackBg.value) {
      const state = el.readyState
      if (state < 2) {
        degradeToFallback(`video not ready, readyState=${state}`)
      }
    }
  }, 4500)
  await loadCaptcha()
})

onUnmounted(() => {
  if (fallbackTimer) {
    clearTimeout(fallbackTimer)
    fallbackTimer = null
  }
})

const loginForm = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

const loadCaptcha = async () => {
  try {
    const res = await getLoginCaptcha()
    const payload = res.data?.data
    loginForm.captchaId = payload?.captchaId || ''
    captchaSvg.value = payload?.captchaSvg || ''
    loginForm.captchaCode = ''
  } catch (error) {
    ElMessage.error('验证码加载失败，请稍后重试')
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    const valid = await loginFormRef.value.validate()
    if (!valid) return
    isLoading.value = true

    // 把上次的报错气泡清掉，别堆一屏
    ElMessage.closeAll()

    const result = await authStore.loginAction(loginForm, { redirect: false })

    if (result.success) {
      loginSuccess.value = true
      ElMessage.success('登录成功，正在进入首页...')
      setTimeout(() => {
        router.push('/')
      }, LOGIN_SUCCESS_REDIRECT_DELAY)
    } else {
      ElMessage.error(result.message || '登录失败，请检查用户名、密码和验证码')

      // 密码验证码都清掉，配合新验证码重新输入
      loginForm.password = ''
      loginForm.captchaCode = ''
      await loadCaptcha()

      // 光标放回密码框，少点一次
      setTimeout(() => {
        const passwordInput = document.querySelector('input[type="password"]')
        if (passwordInput) passwordInput.focus()
      }, 100)
    }
  } catch (error) {
    console.error('[Login] 登录异常:', error)
    ElMessage.error('登录请求失败，请检查网络连接或稍后重试')

    // 网络不通时多提一句后端可能没起
    if (error.message && error.message.includes('Network Error')) {
      ElMessage.warning('网络连接失败，请检查后端服务是否运行')
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;

  .login-bg-fallback {
    position: fixed;
    inset: 0;
    z-index: 0;
    pointer-events: none;
    background:
      radial-gradient(circle at 70% 40%, rgba(120, 190, 255, 0.16), transparent 45%),
      radial-gradient(circle at 20% 75%, rgba(255, 206, 145, 0.12), transparent 42%),
      linear-gradient(140deg, #0b1020, #101a2e 45%, #0b1020);
  }

  .login-bg-video {
    position: fixed;
    inset: -7% -3%;
    width: 106%;
    height: 114%;
    object-fit: cover;
    object-position: 50% 52%;
    z-index: 0;
    pointer-events: none;
    transform: scale(1.14);
    filter: saturate(1.08) contrast(1.05) brightness(1.03);
  }

  .login-bg-video-fill {
    position: fixed;
    inset: -10% -6%;
    width: 112%;
    height: 120%;
    object-fit: cover;
    object-position: 50% 52%;
    z-index: 0;
    pointer-events: none;
    transform: scale(1.24);
    filter: blur(26px) brightness(0.9) saturate(1.15);
    opacity: 0.75;
  }

  .login-bg-overlay {
    position: fixed;
    inset: 0;
    background:
      linear-gradient(140deg, rgba(11, 16, 28, 0.26), rgba(15, 20, 34, 0.42)),
      linear-gradient(to bottom, rgba(0, 0, 0, 0.48), transparent 18%, transparent 82%, rgba(0, 0, 0, 0.55)),
      radial-gradient(circle at 72% 34%, rgba(94, 173, 255, 0.16), transparent 42%),
      radial-gradient(circle at 22% 70%, rgba(255, 186, 122, 0.13), transparent 40%);
    z-index: 1;
    pointer-events: none;
  }

  .aurora-layer {
    position: fixed;
    inset: 0;
    z-index: 1;
    pointer-events: none;
    background:
      radial-gradient(circle at 80% 22%, rgba(86, 178, 255, 0.2), transparent 36%),
      radial-gradient(circle at 18% 76%, rgba(255, 208, 147, 0.16), transparent 34%);
    mix-blend-mode: screen;
    animation: aurora-drift 8s ease-in-out infinite alternate;
  }

  .login-success-mask {
    position: fixed;
    inset: 0;
    z-index: 4;
    pointer-events: none;
    display: grid;
    place-items: center;
    background:
      radial-gradient(circle at center, rgba(105, 189, 255, 0.26), rgba(8, 12, 26, 0.72)),
      rgba(10, 16, 32, 0.5);
    backdrop-filter: blur(5px);
    -webkit-backdrop-filter: blur(5px);

    .boot-welcome-panel {
      position: relative;
      width: min(620px, 84vw);
      border-radius: 20px;
      border: 1px solid rgba(137, 220, 255, 0.42);
      background:
        linear-gradient(150deg, rgba(11, 23, 42, 0.8), rgba(10, 20, 34, 0.68)),
        radial-gradient(circle at 20% 30%, rgba(126, 221, 255, 0.16), rgba(126, 221, 255, 0) 50%);
      box-shadow:
        0 30px 80px rgba(6, 12, 24, 0.55),
        inset 0 0 0 1px rgba(180, 236, 255, 0.15);
      padding: 34px 36px 30px;
      overflow: hidden;
      animation: bootPanelIn 520ms cubic-bezier(.2,.9,.2,1) both;
    }

    .scan-line {
      position: absolute;
      inset: 0;
      pointer-events: none;
      background: linear-gradient(180deg, rgba(143, 223, 255, 0), rgba(143, 223, 255, 0.18), rgba(143, 223, 255, 0));
      transform: translateY(-120%);
      animation: bootScan 1.85s ease-in-out infinite;
    }

    .success-ring {
      position: absolute;
      right: 24px;
      top: 20px;
      width: 84px;
      height: 84px;
      border-radius: 999px;
      border: 2px solid rgba(152, 229, 255, 0.65);
      box-shadow: 0 0 28px rgba(131, 216, 255, 0.4);
      animation: ringPulseSoft 1s ease-out;
      // 圆形裁切，让头像跟着圈走而不是溢出成方形
      overflow: hidden;
      display: grid;
      place-items: center;
      background: rgba(11, 23, 42, 0.55);

      .ring-avatar {
        width: 100%;
        height: 100%;
        object-fit: cover;
        border-radius: inherit;
      }

      .ring-avatar-fallback {
        color: #d9f3ff;
        font-size: 32px;
        font-weight: 700;
        letter-spacing: 1px;
        text-shadow: 0 0 16px rgba(124, 212, 255, 0.5);
        user-select: none;
      }
    }

    .boot-title {
      position: relative;
      color: #d9f3ff;
      font-size: clamp(28px, 5vw, 48px);
      font-weight: 800;
      letter-spacing: 5px;
      line-height: 1.05;
      text-shadow: 0 0 24px rgba(124, 212, 255, 0.56);
      animation: bootTextIn 500ms ease-out both;
    }

    .boot-subtitle {
      margin-top: 10px;
      color: rgba(226, 246, 255, 0.95);
      font-size: clamp(16px, 2.3vw, 24px);
      font-weight: 600;
      letter-spacing: 1px;
      animation: bootSubtitleIn 620ms ease-out 100ms both;
    }

    .boot-status {
      margin-top: 16px;
      color: rgba(174, 222, 245, 0.9);
      font-size: 13px;
      letter-spacing: 1.4px;
      animation: bootSubtitleIn 680ms ease-out 200ms both;

      .dot {
        display: inline-block;
        width: 7px;
        text-align: center;
        opacity: 0.2;
      }

      .dot-1 { animation: bootDot 1.2s linear infinite; }
      .dot-2 { animation: bootDot 1.2s linear 0.2s infinite; }
      .dot-3 { animation: bootDot 1.2s linear 0.4s infinite; }
    }

    .boot-cursor {
      margin-top: 14px;
      width: 18px;
      height: 3px;
      border-radius: 999px;
      background: rgba(171, 233, 255, 0.9);
      box-shadow: 0 0 16px rgba(128, 217, 255, 0.72);
      animation: bootBlink 0.8s step-end infinite;
    }
  }

  .login-box {
    // 窄屏时两侧留 16px 边距，避免 400px 固定宽度把标题挤成多行
    width: min(400px, calc(100vw - 32px));
    padding: 40px;
    background: rgba(255, 255, 255, 0.86);
    border: 1px solid rgba(255, 255, 255, 0.68);
    border-radius: 16px;
    box-shadow: 0 26px 70px rgba(11, 20, 36, 0.4);
    backdrop-filter: blur(14px) saturate(1.2);
    -webkit-backdrop-filter: blur(14px) saturate(1.2);
    z-index: 2;
    position: relative;
    overflow: hidden;
    color: #1f2937;

    &::before {
      content: '';
      position: absolute;
      inset: -1px;
      border-radius: inherit;
      background: linear-gradient(130deg, rgba(102, 181, 255, 0.24), rgba(255, 219, 173, 0.2), transparent 52%);
      z-index: -1;
    }

    .login-header {
      text-align: center;
      margin-bottom: 30px;

      // 两行都锁死单行：字号随容器微收，但不下探到影响可读性的程度
      h1 {
        font-size: clamp(20px, 5.4vw, 26px);
        color: #333;
        margin-bottom: 10px;
        line-height: 1.25;
        letter-spacing: 1px;
        white-space: nowrap;
      }

      p {
        color: #5f6a79;
        font-size: clamp(12px, 3.4vw, 15px);
        line-height: 1.4;
        letter-spacing: 0.3px;
        white-space: nowrap;
      }
    }

    .login-form {
      .captcha-row {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
      }

      .captcha-box {
        width: 132px;
        height: 42px;
        border-radius: 10px;
        border: 1px solid rgba(148, 163, 184, 0.5);
        background: #f8fbff;
        display: grid;
        place-items: center;
        overflow: hidden;
        cursor: pointer;
        user-select: none;
        transition: transform 160ms ease, box-shadow 160ms ease;

        &:hover {
          transform: translateY(-1px);
          box-shadow: 0 8px 18px rgba(51, 65, 85, 0.18);
        }

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        span {
          font-size: 12px;
          color: #64748b;
        }
      }

      :deep(.el-input__wrapper) {
        background: rgba(255, 255, 255, 0.96) !important;
        box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.55) inset !important;
        transition: box-shadow 180ms ease, background 180ms ease;
      }

      :deep(.el-input__inner) {
        color: #1f2937 !important;
      }

      :deep(.el-input__inner::placeholder) {
        color: rgba(71, 85, 105, 0.78) !important;
      }

      :deep(.el-input__prefix-inner .el-icon) {
        color: rgba(51, 65, 85, 0.58) !important;
      }

      :deep(.el-input__wrapper.is-focus) {
        box-shadow:
          0 0 0 1px rgba(79, 121, 199, 0.55) inset,
          0 0 0 4px rgba(79, 121, 199, 0.16) !important;
      }

      :deep(.el-form-item__error) {
        color: #dc2626 !important;
        font-weight: 600;
        letter-spacing: 0.2px;
        text-shadow: 0 1px 0 rgba(255, 255, 255, 0.85);
      }

      .login-button {
        width: 100%;
        height: 42px;
        font-weight: 600;
        letter-spacing: 0.4px;
        background: linear-gradient(120deg, #4f79c7, #6da9df);
        border: none;
        box-shadow: 0 10px 24px rgba(79, 121, 199, 0.32);
        transition: transform 180ms ease, box-shadow 180ms ease;

        &:hover {
          transform: translateY(-1px);
          box-shadow: 0 14px 28px rgba(79, 121, 199, 0.4);
        }
      }
    }

    .login-footer {
      text-align: center;
      margin-top: 20px;
      font-size: 14px;
      color: #666;

      a {
        color: #667eea;
        text-decoration: none;
        margin-left: 5px;

        &:hover {
          text-decoration: underline;
        }
      }
    }
  }
}

@keyframes aurora-drift {
  0% {
    transform: translate3d(-1.5%, -1%, 0) scale(1);
    opacity: 0.9;
  }
  100% {
    transform: translate3d(1.2%, 1.5%, 0) scale(1.04);
    opacity: 0.72;
  }
}

@keyframes ringPulse {
  0% {
    transform: scale(0.72);
    opacity: 0.2;
  }
  100% {
    transform: scale(1.25);
    opacity: 0;
  }
}

@keyframes textRise {
  0% {
    transform: translateY(10px);
    opacity: 0;
  }
  100% {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes bootPanelIn {
  0% {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes bootScan {
  0% { transform: translateY(-120%); }
  100% { transform: translateY(120%); }
}

@keyframes bootTextIn {
  0% {
    opacity: 0;
    transform: translateY(8px);
    letter-spacing: 1px;
    filter: blur(1px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
    letter-spacing: 5px;
    filter: blur(0);
  }
}

@keyframes bootSubtitleIn {
  0% {
    opacity: 0;
    transform: translateY(10px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes bootDot {
  0%, 100% { opacity: 0.25; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-1px); }
}

@keyframes bootBlink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0.2; }
}

@keyframes ringPulseSoft {
  0% {
    opacity: 0;
    transform: scale(0.74);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.login-success-fade-enter-active,
.login-success-fade-leave-active {
  transition: opacity 240ms ease;
}

.login-success-fade-enter-from,
.login-success-fade-leave-to {
  opacity: 0;
}
</style>
