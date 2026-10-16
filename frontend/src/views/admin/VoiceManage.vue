<template>
  <div class="voice-manage">
    <!-- 克隆流程卡片 -->
    <el-card class="clone-card">
      <template #header>
        <div class="card-head">
          <span>声音克隆（GLM-TTS-Clone）</span>
          <span class="card-head__tip">上传 3~30 秒清晰人声 → 克隆 → 启用后出现在 AI 导览音色列表</span>
        </div>
      </template>

      <el-steps :active="stepActive" align-center class="clone-steps">
        <el-step title="上传样本" description="wav/mp3/m4a ≤10MB" />
        <el-step title="提交克隆" description="填写音色名称与试听文本" />
        <el-step title="试听确认" description="试听效果后自动启用" />
      </el-steps>

      <el-upload
        class="clone-upload"
        drag
        accept=".wav,.mp3,.m4a"
        :auto-upload="false"
        :limit="1"
        :on-change="onSampleChange"
        :file-list="sampleFiles"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽音频文件到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">3~30 秒清晰人声效果最佳，格式支持 wav / mp3 / m4a</div>
        </template>
      </el-upload>

      <el-form label-width="90px" class="clone-form">
        <el-form-item label="音色名称">
          <el-input v-model="voiceName" placeholder="例如：管理员声音、讲解员小王" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="试听文本">
          <el-input
            v-model="previewText"
            type="textarea"
            :rows="2"
            maxlength="100"
            show-word-limit
            placeholder="克隆完成后用这段文字生成试听语音（可留空使用默认）"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="cloning" :disabled="!sampleFile || !voiceName.trim()" @click="submitClone">
            {{ cloning ? '克隆中（约 10~30 秒）…' : '开始克隆' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 音色列表 -->
    <el-card class="list-card">
      <template #header>
        <span>已有音色</span>
      </template>
      <el-table :data="voices" v-loading="listLoading" empty-text="暂无克隆音色，先在上方创建一个">
        <el-table-column prop="voiceName" label="名称" min-width="140" />
        <el-table-column prop="voiceId" label="音色 ID" min-width="220">
          <template #default="{ row }">
            <span class="voice-id">{{ row.voiceId.slice(0, 18) }}…</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">
              {{ row.status === 'enabled' ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :loading="previewingId === row.id" @click="preview(row)">试听</el-button>
            <el-button size="small" :type="row.status === 'enabled' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'enabled' ? '停用' : '启用' }}
            </el-button>
            <el-popconfirm title="确定删除该音色？" @confirm="remove(row)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 试听音频（隐藏元素，播放用） -->
    <audio ref="previewAudioRef" style="display: none" />
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  uploadVoiceSample,
  cloneVoice,
  listVoices,
  updateVoiceStatus,
  deleteVoice,
  previewVoice
} from '@/api/adminVoice'

const sampleFiles = ref([])
const sampleFile = ref(null)
const voiceName = ref('')
const previewText = ref('')
const cloning = ref(false)
const voices = ref([])
const listLoading = ref(false)
const previewingId = ref(null)
const previewAudioRef = ref(null)
let previewUrl = null

const stepActive = computed(() => {
  if (sampleFile.value && cloning.value) return 2
  if (sampleFile.value) return 1
  return 0
})

const onSampleChange = (file) => {
  const raw = file.raw
  if (!raw) return
  const okType = /\.(wav|mp3|m4a)$/i.test(raw.name)
  if (!okType) {
    ElMessage.error('仅支持 wav / mp3 / m4a 格式')
    sampleFiles.value = []
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    ElMessage.error('样本不能超过 10MB')
    sampleFiles.value = []
    return
  }
  sampleFile.value = raw
}

const refreshList = async () => {
  listLoading.value = true
  try {
    const resp = await listVoices()
    voices.value = resp.data?.data ?? resp.data ?? []
  } finally {
    listLoading.value = false
  }
}
refreshList()

const submitClone = async () => {
  cloning.value = true
  try {
    // 两步走：先传样本拿到 fileId，再提交克隆
    const upResp = await uploadVoiceSample(sampleFile.value)
    const fileId = upResp.data?.data?.fileId
    if (!fileId) throw new Error(upResp.data?.message || '样本上传失败')
    await cloneVoice({ voiceName: voiceName.value.trim(), fileId, previewText: previewText.value.trim() })
    ElMessage.success('克隆成功，音色已启用')
    // 清空表单并刷新列表
    sampleFiles.value = []
    sampleFile.value = null
    voiceName.value = ''
    previewText.value = ''
    await refreshList()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '克隆失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    cloning.value = false
  }
}

const preview = async (row) => {
  previewingId.value = row.id
  try {
    const resp = await previewVoice(row.id)
    // 返回的是 arraybuffer，得看 content-type 区分是音频还是错误 JSON
    const ct = resp.headers?.['content-type'] || ''
    if (ct.includes('application/json')) {
      const json = JSON.parse(new TextDecoder().decode(resp.data))
      ElMessage.error(json.message || '试听失败')
      return
    }
    const blob = new Blob([resp.data], { type: 'audio/wav' })
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    previewUrl = URL.createObjectURL(blob)
    const audio = previewAudioRef.value
    audio.src = previewUrl
    await audio.play()
  } catch (e) {
    ElMessage.error('试听失败：' + (e?.response?.data?.message || e.message))
  } finally {
    previewingId.value = null
  }
}

const toggleStatus = async (row) => {
  const next = row.status === 'enabled' ? 'disabled' : 'enabled'
  await updateVoiceStatus(row.id, next)
  row.status = next
  ElMessage.success(next === 'enabled' ? '已启用' : '已停用')
}

const remove = async (row) => {
  await deleteVoice(row.id)
  voices.value = voices.value.filter(v => v.id !== row.id)
  ElMessage.success('已删除')
}

onUnmounted(() => {
  if (previewUrl) URL.revokeObjectURL(previewUrl)
})
</script>

<style scoped>
.voice-manage {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.card-head__tip {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.clone-steps {
  margin-bottom: 20px;
}

.clone-upload {
  margin-bottom: 18px;
}

.clone-form {
  max-width: 560px;
}

.voice-id {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
}
</style>
