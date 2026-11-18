<template>
  <!--
    AI 知识问答页。

    设计意图：不做成一个"空输入框等用户猜"的聊天页，而是先给用户一条明确的路——
    左侧是「能问什么」的能力地图（按角色动态变化），右侧是问答主体。
    能力卡片点一下就等于填好一句问题，用户不需要自己组织语言。

    与模型详情页右下角悬浮问答的区别：
    · 这里是"全局问答"，问的是建筑知识、平台用法、权限、统计数据；
    · 悬浮问答带当前模型上下文，问"这个模型"更准确；
    · 两处的预设问题也分开维护（后台预设问题里的"展示位置"字段）。
  -->
  <div class="ai-chat-page">
    <aside class="guide-rail">
      <div class="rail-head">
        <div class="rail-title">能问什么</div>
        <div class="rail-sub">点一下直接提问，也可以自己输入</div>
      </div>

      <div class="capability-list">
        <button
          v-for="(cap, idx) in capabilities"
          :key="cap.key"
          type="button"
          class="capability"
          :style="{ '--i': idx }"
          @click="askCapability(cap)"
        >
          <span class="cap-icon" aria-hidden="true">{{ cap.icon }}</span>
          <span class="cap-body">
            <span class="cap-title">{{ cap.title }}</span>
            <span class="cap-desc">{{ cap.desc }}</span>
          </span>
        </button>
      </div>

      <div class="rail-foot">
        <!-- 角色直接影响能回答的范围，这里把当前身份明确标出来 -->
        <div class="role-chip" :class="`role-${roleKey}`">
          <span class="role-dot" aria-hidden="true" />
          <span>当前身份：{{ roleText }}</span>
        </div>
        <p class="role-tip">{{ roleTip }}</p>
      </div>
    </aside>

    <section class="chat-stage">
      <ChatFloat ref="chatRef" embedded scene="chat" />
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import ChatFloat from '@/components/ChatFloat.vue'
import { useAuthStore } from '@/stores/auth'

const chatRef = ref(null)
const authStore = useAuthStore()

const roleKey = computed(() => {
  if (!authStore.token) return 'guest'
  return authStore.userInfo?.role || 'user'
})

const roleText = computed(() => {
  switch (roleKey.value) {
    case 'admin': return '管理员'
    case 'contributor': return '贡献者'
    case 'guest': return '游客（未登录）'
    default: return '普通用户'
  }
})

const roleTip = computed(() => {
  switch (roleKey.value) {
    case 'admin':
      return '你可以问审核流程、后台功能、数据统计，也可以问建筑专业知识。'
    case 'contributor':
      return '你可以问上传要求、模型规范、审核标准，也可以问建筑专业知识。'
    case 'guest':
      return '登录后解锁收藏、点赞、评论与下载。现在也可以先问建筑知识。'
    default:
      return '想上传模型？可以在「权限申请」提交贡献者申请。'
  }
})

/**
 * 能力地图：每条都带一个具体问题，点了就发出去。
 * 问题的措辞刻意写得口语化——用户真正会这么问。
 */
const capabilities = computed(() => {
  const base = [
    {
      key: 'platform',
      icon: '览',
      title: '平台怎么用',
      desc: '点赞、收藏、下载、地图导览的操作路径',
      question: '我不太清楚这个平台怎么用，点赞和收藏分别在哪里操作？'
    },
    {
      key: 'stats',
      icon: '数',
      title: '平台有哪些内容',
      desc: '已收录多少模型、多少分类、多少知识',
      question: '现在平台一共收录了多少个3D古建筑模型？'
    },
    {
      key: 'permission',
      icon: '权',
      title: '我能做什么',
      desc: '按你当前的角色说明可用权限',
      question: '我现在这个账号有哪些权限？能做什么、不能做什么？'
    },
    {
      key: 'structure',
      icon: '构',
      title: '结构与做法',
      desc: '斗拱、榫卯、拱券这些构件到底起什么作用',
      question: '斗拱在木构建筑里到底起什么作用？为什么说它是中国古建的标志？'
    },
    {
      key: 'dynasty',
      icon: '代',
      title: '朝代风格对比',
      desc: '唐宋明清的建筑风格差别在哪',
      question: '明清建筑和唐宋建筑在风格和结构上有什么主要区别？'
    },
    {
      key: 'bridge',
      icon: '桥',
      title: '桥梁形制',
      desc: '拱桥、梁桥、廊桥的区别与代表',
      question: '中国古桥主要有哪些类型？拱桥和梁桥在受力上有什么区别？'
    }
  ]

  // 管理员的额外入口：审核与数据运营
  if (roleKey.value === 'admin') {
    base.push({
      key: 'review',
      icon: '审',
      title: '审核与治理',
      desc: '模型审核、纠错处理、知识库维护的流程',
      question: '后台的模型审核流程是怎样的？待审模型和纠错反馈应该按什么标准处理？'
    })
  }

  return base
})

const askCapability = (cap) => {
  chatRef.value?.sendPresetQuestion?.(cap.question)
}
</script>

<style lang="scss" scoped>
.ai-chat-page {
  display: flex;
  gap: 14px;
  flex: 1 1 auto;
  height: 100%;
  min-height: 520px;

  /* ===== 左侧能力地图 ===== */
  .guide-rail {
    flex: none;
    width: 268px;
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 18px 16px;
    border-radius: var(--v2-radius-lg);
    background: var(--v2-surface-strong);
    border: 1px solid var(--v2-border-soft);
    box-shadow: var(--v2-shadow-soft);
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: 999px;
      background: var(--v2-chip-border);
    }

    .rail-head {
      .rail-title {
        font-size: 14px;
        font-weight: 800;
        letter-spacing: 0.4px;
        color: var(--v2-text-strong);
      }

      .rail-sub {
        margin-top: 5px;
        font-size: 12px;
        line-height: 1.6;
        color: var(--v2-text-weak);
      }
    }

    .capability-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .capability {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      width: 100%;
      padding: 10px 11px;
      text-align: left;
      border-radius: 12px;
      cursor: pointer;
      background: var(--v2-surface-soft);
      border: 1px solid var(--v2-border-soft);
      transition: transform 180ms ease, border-color 180ms ease, background 180ms ease;
      /* 逐条入场，和地图页的胶囊保持同一套节奏 */
      animation: cap-in 320ms cubic-bezier(.2,.8,.2,1) both;
      animation-delay: calc(var(--i, 0) * 45ms);

      .cap-icon {
        flex: none;
        width: 26px;
        height: 26px;
        display: grid;
        place-items: center;
        border-radius: 9px;
        font-size: 12px;
        font-weight: 700;
        color: var(--v2-chip-text);
        background: var(--v2-chip-bg);
        border: 1px solid var(--v2-chip-border);
      }

      .cap-body {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 3px;
      }

      .cap-title {
        font-size: 13px;
        font-weight: 700;
        color: var(--v2-text-strong);
      }

      .cap-desc {
        font-size: 11.5px;
        line-height: 1.5;
        color: var(--v2-text-weak);
      }

      &:hover {
        transform: translateX(3px);
        border-color: var(--v2-chip-border);
        background: var(--v2-surface-hover);
      }
    }

    .rail-foot {
      margin-top: auto;
      padding-top: 12px;
      border-top: 1px dashed var(--v2-divider);

      .role-chip {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 4px 10px;
        border-radius: 999px;
        font-size: 11.5px;
        font-weight: 600;
        border: 1px solid var(--v2-border-soft);
        background: var(--v2-surface-soft);
        color: var(--v2-text-body);

        .role-dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: var(--v2-text-weak);
        }

        &.role-admin .role-dot { background: var(--v2-gold); }
        &.role-contributor .role-dot { background: var(--v2-cyan); }
        &.role-guest .role-dot { background: var(--v2-text-weak); }
      }

      .role-tip {
        margin: 8px 0 0;
        font-size: 11.5px;
        line-height: 1.6;
        color: var(--v2-text-weak);
      }
    }
  }

  .chat-stage {
    flex: 1;
    min-width: 0;
    min-height: 0;
    display: flex;

    :deep(.chat-float-container) {
      flex: 1;
      min-width: 0;
    }
  }
}

@keyframes cap-in {
  from {
    opacity: 0;
    transform: translateX(-6px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 平板：能力栏收到上方，横向滚动 */
@media (max-width: 1100px) {
  .ai-chat-page {
    flex-direction: column;
    height: auto;

    .guide-rail {
      width: 100%;
      flex-direction: row;
      align-items: flex-start;
      gap: 14px;
      overflow-x: auto;

      .rail-head {
        flex: none;
        min-width: 150px;
      }

      .capability-list {
        flex: none;
        flex-direction: row;
      }

      .capability {
        width: 210px;
        flex: none;
      }

      .rail-foot {
        flex: none;
        min-width: 190px;
        margin-top: 0;
        padding-top: 0;
        padding-left: 12px;
        border-top: none;
        border-left: 1px dashed var(--v2-divider);
      }
    }

    .chat-stage {
      min-height: 520px;
    }
  }
}

/* 手机：能力栏只留标题和按钮，说明文字隐藏 */
@media (max-width: 640px) {
  .ai-chat-page {
    gap: 10px;

    .guide-rail {
      flex-direction: column;
      gap: 10px;
      padding: 14px;
      overflow-x: visible;

      .rail-head {
        min-width: 0;
      }

      .capability-list {
        flex-direction: column;
        width: 100%;
      }

      .capability {
        width: 100%;
      }

      .cap-desc {
        display: none;
      }

      .rail-foot {
        min-width: 0;
        padding-left: 0;
        border-left: none;
        border-top: 1px dashed var(--v2-divider);
        padding-top: 10px;
        width: 100%;
      }
    }

    .chat-stage {
      min-height: 460px;
    }
  }
}
</style>
