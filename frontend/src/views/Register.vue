<template>
  <div class="register-container">
    <div v-if="useFallbackBg" class="register-bg-fallback" aria-hidden="true"></div>

    <video
      v-show="!useFallbackBg"
      class="register-bg-video-fill"
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
      class="register-bg-video"
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
    <div class="register-bg-overlay"></div>
    <div class="aurora-layer"></div>

    <div class="register-box" :class="{ 'is-entered': panelEntered }">
      <div class="register-header">
        <h1>用户注册</h1>
        <p>加入古建筑探索之旅</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="rules"
        class="register-form"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码（至少6位）"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请确认密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="请输入邮箱（可选）"
            :prefix-icon="Message"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="nickname">
          <el-input
            v-model="registerForm.nickname"
            placeholder="请输入昵称（可选）"
            :prefix-icon="UserFilled"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="isLoading"
            class="register-button"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { User, Lock, Message, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import loginBgVideo from '@/assets/videos/loginbg.mp4'

const authStore = useAuthStore()

const registerFormRef = ref(null)
const isLoading = ref(false)
const panelEntered = ref(false)

const videoRef = ref(null)
const useFallbackBg = ref(false)
const videoReady = ref(false)
let fallbackTimer = null

const degradeToFallback = () => {
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

const onVideoError = () => {
  degradeToFallback()
}

const onLoadedData = () => {
  markVideoReady()
}

const onCanPlay = () => {
  markVideoReady()
}

onMounted(async () => {
  const el = videoRef.value
  if (el) {
    try {
      const p = el.play()
      if (p && typeof p.then === 'function') {
        await p.catch(() => {})
      }
    } catch (e) {
      // keep silent and let timer handle fallback
    }
    fallbackTimer = setTimeout(() => {
      if (!videoReady.value && !useFallbackBg.value && el.readyState < 2) {
        degradeToFallback()
      }
    }, 4500)
  }

  requestAnimationFrame(() => {
    panelEntered.value = true
  })
})

onUnmounted(() => {
  if (fallbackTimer) {
    clearTimeout(fallbackTimer)
    fallbackTimer = null
  }
})

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  nickname: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度必须在3-50之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      isLoading.value = true
      const result = await authStore.registerAction(registerForm)
      isLoading.value = false

      if (!result.success) {
        ElMessage.error(result.message)
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.register-container {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;

  .register-bg-fallback {
    position: fixed;
    inset: 0;
    z-index: 0;
    pointer-events: none;
    background:
      radial-gradient(circle at 70% 40%, rgba(120, 190, 255, 0.16), transparent 45%),
      radial-gradient(circle at 20% 75%, rgba(255, 206, 145, 0.12), transparent 42%),
      linear-gradient(140deg, #0b1020, #101a2e 45%, #0b1020);
  }

  .register-bg-video {
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

  .register-bg-video-fill {
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

  .register-bg-overlay {
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

  .register-box {
    width: 400px;
    padding: 40px;
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid rgba(255, 255, 255, 0.72);
    border-radius: 16px;
    box-shadow: 0 26px 70px rgba(11, 20, 36, 0.4);
    backdrop-filter: blur(14px) saturate(1.2);
    -webkit-backdrop-filter: blur(14px) saturate(1.2);
    z-index: 2;
    position: relative;
    overflow: hidden;
    color: #1f2937;
    opacity: 0;
    transform: translateY(20px) scale(0.98);
    transition: opacity 420ms cubic-bezier(.2,.9,.2,1), transform 420ms cubic-bezier(.2,.9,.2,1);

    &.is-entered {
      opacity: 1;
      transform: translateY(0) scale(1);
    }

    &::before {
      content: '';
      position: absolute;
      inset: -1px;
      border-radius: inherit;
      background: linear-gradient(130deg, rgba(102, 181, 255, 0.24), rgba(255, 219, 173, 0.2), transparent 52%);
      z-index: -1;
    }

    .register-header {
      text-align: center;
      margin-bottom: 30px;

      h1 {
        font-size: 24px;
        color: #1f2937;
        margin-bottom: 10px;
      }

      p {
        color: #5f6a79;
        font-size: 14px;
      }
    }

    .register-form {
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

      :deep(.el-input__prefix-inner .el-icon),
      :deep(.el-input__suffix-inner .el-icon) {
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

      .register-button {
        width: 100%;
        height: 42px;
        font-weight: 600;
        letter-spacing: 0.4px;
        background: linear-gradient(120deg, #d2b06f, #e0c287);
        border: none;
        box-shadow: 0 10px 24px rgba(210, 176, 111, 0.32);
        transition: transform 180ms ease, box-shadow 180ms ease;

        &:hover {
          transform: translateY(-1px);
          box-shadow: 0 14px 28px rgba(210, 176, 111, 0.4);
        }
      }
    }

    .register-footer {
      text-align: center;
      margin-top: 20px;
      font-size: 14px;
      color: #666;

      a {
        color: #4f79c7;
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
</style>
