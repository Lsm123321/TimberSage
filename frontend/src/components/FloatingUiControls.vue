<template>
  <div class="floating-ui-controls v2-glass-strong">
    <el-tooltip :content="t('languageSwitch')" placement="right">
      <el-button size="small" class="control-btn" @click="toggleLanguage">
        {{ language === 'zh' ? 'EN' : '中' }}
      </el-button>
    </el-tooltip>
    <el-tooltip :content="t('themeSwitch')" placement="right">
      <el-button size="small" class="control-btn" @click="toggleTheme">
        <span class="theme-icon">{{ theme === 'dark' ? '☀' : '◐' }}</span>
      </el-button>
    </el-tooltip>
    <el-tooltip :content="t('buttonStyleSwitch')" placement="right">
      <el-button
        size="small"
        class="control-btn control-btn--style"
        :class="`mode-${buttonStyle}`"
        @click="cycleButtonStyle"
      >
        <span class="style-icon">{{ buttonStyleIcon }}</span>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { language, theme, buttonStyle, toggleLanguage, toggleTheme, cycleButtonStyle, t } = useUiPreferences()

const buttonStyleIcon = computed(() => {
  if (buttonStyle.value === 'glass') return '◍'
  if (buttonStyle.value === 'neon') return '✦'
  return '●'
})
</script>

<style scoped lang="scss">
.floating-ui-controls {
  position: fixed;
  left: 20px;
  bottom: 20px;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 9px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow:
    0 14px 30px rgba(8, 14, 28, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(12px) saturate(135%);
  -webkit-backdrop-filter: blur(12px) saturate(135%);

  &::before {
    content: '';
    position: absolute;
    inset: 7px;
    border-radius: 14px;
    pointer-events: none;
    border: 1px solid rgba(255, 255, 255, 0.08);
  }

  .control-btn {
    width: 54px;
    height: 38px;
    padding: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    font-weight: 700;
    font-size: 20px;
    letter-spacing: 0.3px;
    border: 1px solid rgba(255, 255, 255, 0.2);
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.9));
    color: #3a4152;
    box-shadow: 0 6px 14px rgba(8, 14, 28, 0.14);
    transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease, border-color 0.2s ease;
    position: relative;
    z-index: 1;

    &:hover {
      transform: translateY(-2px);
      filter: brightness(1.03);
      border-color: rgba(255, 255, 255, 0.35);
      box-shadow: 0 10px 18px rgba(8, 14, 28, 0.22);
    }
  }

  .theme-icon {
    display: inline-block;
    line-height: 1;
    transform: translateY(-0.5px);
  }

  .control-btn--style {
    font-size: 17px;
  }

  .style-icon {
    display: inline-block;
    line-height: 1;
    letter-spacing: 0.2px;
  }

  .mode-glass {
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0.62));
  }

  .mode-neon {
    color: #5b41c7;
    box-shadow:
      0 0 0 1px rgba(94, 83, 255, 0.24),
      0 8px 18px rgba(94, 83, 255, 0.28);
  }
}
</style>
