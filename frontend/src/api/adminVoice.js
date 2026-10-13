import request from '@/utils/request'

/**
 * 管理端：克隆音色管理 API
 */

/** 上传音色样本（3~30 秒清晰人声，wav/mp3/m4a，≤10MB），返回 { fileId } */
export function uploadVoiceSample(file) {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/admin/voices/upload',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 提交声音克隆，返回音色记录 { id, voiceName, voiceId, ... } */
export function cloneVoice({ voiceName, fileId, previewText }) {
  return request({
    url: '/admin/voices/clone',
    method: 'post',
    data: { voiceName, fileId, previewText },
    timeout: 300000
  })
}

/** 音色列表（含停用） */
export function listVoices() {
  return request({ url: '/admin/voices', method: 'get' })
}

/** 启用/停用 */
export function updateVoiceStatus(id, status) {
  return request({ url: `/admin/voices/${id}/status`, method: 'put', data: { status } })
}

/** 删除 */
export function deleteVoice(id) {
  return request({ url: `/admin/voices/${id}`, method: 'delete' })
}

/** 试听：返回 wav 二进制（Blob URL 由调用方管理） */
export function previewVoice(id, text) {
  return request({
    url: `/admin/voices/${id}/preview`,
    method: 'post',
    data: text ? { text } : {},
    responseType: 'arraybuffer',
    timeout: 120000
  })
}
