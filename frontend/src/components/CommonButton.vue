<template>
  <el-button :type="type" :size="size" :loading="loading" :disabled="disabled" @click="handleClick">
    <slot></slot>
  </el-button>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'default'
  },
  size: {
    type: String,
    default: 'medium'
  },
  loading: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

const handleClick = (event) => {
  emit('click', event)
}
</script>
