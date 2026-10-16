<template>
  <div class="material-panel">
    <el-collapse v-model="activeNames">
      <el-collapse-item :title="t('materialAdjust')" name="material">
        <div class="material-controls">
          <div class="control-item">
            <label>{{ t('materialColor') }}</label>
            <el-color-picker v-model="materialColor" @change="handleColorChange" />
          </div>

          <div class="control-item">
            <label>{{ t('materialMetalness') }}</label>
            <el-slider v-model="materialMetalness" :min="0" :max="1" :step="0.01" @change="handleMetalnessChange" />
            <span class="value">{{ materialMetalness.toFixed(2) }}</span>
          </div>

          <div class="control-item">
            <label>{{ t('materialRoughness') }}</label>
            <el-slider v-model="materialRoughness" :min="0" :max="1" :step="0.01" @change="handleRoughnessChange" />
            <span class="value">{{ materialRoughness.toFixed(2) }}</span>
          </div>

          <div class="control-item">
            <label>{{ t('materialOpacity') }}</label>
            <el-slider v-model="materialOpacity" :min="0" :max="1" :step="0.01" @change="handleOpacityChange" />
            <span class="value">{{ materialOpacity.toFixed(2) }}</span>
          </div>

          <div class="control-item">
            <label>{{ t('materialEmissiveIntensity') }}</label>
            <el-slider v-model="emissiveIntensity" :min="0" :max="1" :step="0.01" @change="handleEmissiveIntensityChange" />
            <span class="value">{{ emissiveIntensity.toFixed(2) }}</span>
          </div>

          <div class="control-item">
            <label>{{ t('materialWireframeMode') }}</label>
            <el-switch v-model="wireframeMode" @change="handleWireframeChange" />
          </div>

          <div class="preset-buttons">
            <el-button size="small" @click="applyPreset('wood')">{{ t('materialPresetWood') }}</el-button>
            <el-button size="small" @click="applyPreset('stone')">{{ t('materialPresetStone') }}</el-button>
            <el-button size="small" @click="applyPreset('bronze')">{{ t('materialPresetBronze') }}</el-button>
            <el-button size="small" @click="applyPreset('wireframe')">{{ t('materialPresetWireframe') }}</el-button>
            <el-button size="small" @click="resetMaterial">{{ t('materialResetDefault') }}</el-button>
          </div>
        </div>
      </el-collapse-item>

      <el-collapse-item :title="t('materialSavePlan')" name="save">
        <div class="save-controls">
          <el-button type="primary" @click="showSaveDialog = true" :disabled="!isLoggedIn">
            {{ t('materialSaveCurrentPlan') }}
          </el-button>

          <el-select
            v-model="selectedPreset"
            :placeholder="t('materialLoadSavedPlan')"
            clearable
            @change="handlePresetChange"
            v-if="savedPresets.length > 0"
          >
            <el-option
              v-for="preset in savedPresets"
              :key="preset.id"
              :label="preset.presetName"
              :value="preset.id"
            />
          </el-select>

          <p v-if="!isLoggedIn" class="login-tip">{{ t('materialLoginToSave') }}</p>
        </div>
      </el-collapse-item>
    </el-collapse>

    <el-dialog
      v-model="showSaveDialog"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="t('materialSavePlan')"
      width="400px"
    >
      <el-form :model="saveForm" label-width="80px">
        <el-form-item :label="t('materialPlanName')">
          <el-input v-model="saveForm.presetName" :placeholder="t('materialEnterPlanName')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSaveDialog = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ t('save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserMaterialPresets } from '@/api/profile'
import { useUiPreferences } from '@/composables/useUiPreferences'

const props = defineProps({
  modelId: {
    type: Number,
    required: true
  },
  isLoggedIn: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['materialChange', 'wireframeChange', 'save'])
const { t } = useUiPreferences()

const activeNames = ref(['material', 'save'])

const materialColor = ref('#8B5A2B')
const materialMetalness = ref(0.3)
const materialRoughness = ref(0.7)
const materialOpacity = ref(1)
const emissiveIntensity = ref(0)
const wireframeMode = ref(false)

const showSaveDialog = ref(false)
const saveForm = ref({
  presetName: ''
})

const savedPresets = ref([])
const selectedPreset = ref(null)

const presets = {
  wood: {
    color: '#8B5A2B',
    metalness: 0.3,
    roughness: 0.7,
    opacity: 1,
    emissive: '#000000',
    emissiveIntensity: 0
  },
  stone: {
    color: '#C0C0C0',
    metalness: 0.1,
    roughness: 0.9,
    opacity: 1,
    emissive: '#000000',
    emissiveIntensity: 0
  },
  bronze: {
    color: '#CD7F32',
    metalness: 0.8,
    roughness: 0.3,
    opacity: 1,
    emissive: '#000000',
    emissiveIntensity: 0
  },
  wireframe: {
    color: '#FFFFFF',
    metalness: 0,
    roughness: 1,
    opacity: 1,
    emissive: '#000000',
    emissiveIntensity: 0
  }
}

watch(() => props.modelId, () => {
  if (props.isLoggedIn) {
    loadSavedPresets()
  }
}, { immediate: true })

const handleColorChange = (color) => {
  emit('materialChange', { property: 'color', value: color })
}

const handleMetalnessChange = (value) => {
  emit('materialChange', { property: 'metalness', value })
}

const handleRoughnessChange = (value) => {
  emit('materialChange', { property: 'roughness', value })
}

const handleOpacityChange = (value) => {
  emit('materialChange', { property: 'opacity', value })
}

const handleEmissiveIntensityChange = (value) => {
  emit('materialChange', { property: 'emissiveIntensity', value })
}

const handleWireframeChange = (enabled) => {
  emit('wireframeChange', enabled)
}

const applyPreset = (presetName) => {
  const preset = presets[presetName]
  if (!preset) return

  if (presetName === 'wireframe') {
    materialColor.value = preset.color
    materialMetalness.value = preset.metalness
    materialRoughness.value = preset.roughness
    materialOpacity.value = preset.opacity
    emissiveIntensity.value = preset.emissiveIntensity
    wireframeMode.value = true
    emit('wireframeChange', true)
  } else {
    materialColor.value = preset.color
    materialMetalness.value = preset.metalness
    materialRoughness.value = preset.roughness
    materialOpacity.value = preset.opacity
    emissiveIntensity.value = preset.emissiveIntensity
    wireframeMode.value = false
    emit('materialChange', { property: 'color', value: preset.color })
    emit('materialChange', { property: 'metalness', value: preset.metalness })
    emit('materialChange', { property: 'roughness', value: preset.roughness })
    emit('materialChange', { property: 'opacity', value: preset.opacity })
    emit('materialChange', { property: 'emissiveIntensity', value: preset.emissiveIntensity })
    emit('wireframeChange', false)
  }
}

const resetMaterial = () => {
  materialColor.value = '#8B5A2B'
  materialMetalness.value = 0.3
  materialRoughness.value = 0.7
  materialOpacity.value = 1
  emissiveIntensity.value = 0
  wireframeMode.value = false

  emit('materialChange', { property: 'color', value: '#8B5A2B' })
  emit('materialChange', { property: 'metalness', value: 0.3 })
  emit('materialChange', { property: 'roughness', value: 0.7 })
  emit('materialChange', { property: 'opacity', value: 1 })
  emit('materialChange', { property: 'emissiveIntensity', value: 0 })
  emit('wireframeChange', false)
}

async function loadSavedPresets() {
  try {
    const response = await getUserMaterialPresets(props.modelId)
    const payload = response?.data?.data ?? response?.data
    savedPresets.value = Array.isArray(payload) ? payload : []
  } catch (error) {
    console.error('[MaterialPanel] 加载已保存方案失败', error)
    savedPresets.value = []
  }
}

const handlePresetChange = async (presetId) => {
  if (!presetId) return

  const preset = savedPresets.value.find(p => p.id === presetId)
  if (!preset) return

  try {
    const materialParams = JSON.parse(preset.materialParams)
    materialColor.value = materialParams.color || '#8B5A2B'
    materialMetalness.value = materialParams.metalness ?? 0.3
    materialRoughness.value = materialParams.roughness ?? 0.7
    materialOpacity.value = materialParams.opacity ?? 1
    emissiveIntensity.value = materialParams.emissiveIntensity ?? 0

    emit('materialChange', { property: 'color', value: materialParams.color })
    emit('materialChange', { property: 'metalness', value: materialParams.metalness })
    emit('materialChange', { property: 'roughness', value: materialParams.roughness })
    emit('materialChange', { property: 'opacity', value: materialParams.opacity })
    emit('materialChange', { property: 'emissiveIntensity', value: materialParams.emissiveIntensity })

    ElMessage.success(t('materialPresetApplied'))
  } catch (error) {
    console.error('[MaterialPanel] 应用材质方案失败', error)
    ElMessage.error(t('materialApplyFailed'))
  }
}

const handleSave = async () => {
  if (!saveForm.value.presetName) {
    ElMessage.warning(t('materialEnterPlanName'))
    return
  }

  emit('save', {
    presetName: saveForm.value.presetName,
    getParams: () => ({
      color: materialColor.value,
      metalness: materialMetalness.value,
      roughness: materialRoughness.value,
      opacity: materialOpacity.value,
      emissive: '#000000',
      emissiveIntensity: emissiveIntensity.value
    })
  })

  showSaveDialog.value = false
  saveForm.value.presetName = ''
  await loadSavedPresets()
}

const updateFromThree = (params) => {
  if (params.color) materialColor.value = params.color
  if (params.metalness !== undefined) materialMetalness.value = params.metalness
  if (params.roughness !== undefined) materialRoughness.value = params.roughness
  if (params.opacity !== undefined) materialOpacity.value = params.opacity
  if (params.emissiveIntensity !== undefined) emissiveIntensity.value = params.emissiveIntensity
}

defineExpose({
  applyPreset,
  resetMaterial,
  updateFromThree
})
</script>

<style scoped>
.material-panel {
  background: white;
  border-radius: 8px;
  padding: 10px;

  .material-controls {
    .control-item {
      display: flex;
      align-items: center;
      margin-bottom: 15px;

      label {
        width: 100px;
        font-size: 14px;
        color: #666;
      }

      .el-slider {
        flex: 1;
        margin: 0 10px;
      }

      .value {
        width: 40px;
        text-align: right;
        font-size: 12px;
        color: #999;
      }
    }

    .preset-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 20px;

      .el-button {
        flex: 1;
        min-width: 70px;
      }
    }
  }

  .save-controls {
    display: flex;
    flex-direction: column;
    gap: 15px;

    .el-select {
      width: 100%;
    }

    .login-tip {
      color: #999;
      font-size: 12px;
      text-align: center;
    }
  }
}
</style>
